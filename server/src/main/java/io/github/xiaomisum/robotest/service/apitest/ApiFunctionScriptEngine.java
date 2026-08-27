package io.github.xiaomisum.robotest.service.apitest;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 自定义函数脚本体执行器（Groovy 沙箱）。
 *
 * <p>为什么独立成组件：保存校验、试算与执行期分发三处共用同一编译缓存与超时口径，
 * 避免脚本被重复编译；安全约束统一收敛于此，防止散落各处出现口径不一致。</p>
 *
 * <p>沙箱策略：导入黑名单 + 间接导入检查拦截 IO/网络/反射/进程等危险能力；
 * 死循环类失控由求值超时兜底（cancel 中断）。脚本内 {@code args} 变量承接调用参数。</p>
 */
@Slf4j
@Component
public class ApiFunctionScriptEngine {

    /** 单次求值超时上限（毫秒）：自定义函数定位为轻量表达式体，超时即视为异常 */
    private static final long EVAL_TIMEOUT_MS = 3000;

    /** 编译缓存上限：超出后整体清空（脚本条目量级极小，全清比维护 LRU 更简单可靠） */
    private static final int CACHE_MAX = 1024;

    private static final List<String> BLACKLIST_IMPORTS = List.of(
            "java.io", "java.nio", "java.net", "java.rmi", "java.sql", "javax.script",
            "java.lang.ProcessBuilder", "java.lang.Runtime", "java.lang.Thread",
            "java.lang.reflect", "java.lang.invoke", "javax.naming",
            "groovy.lang.GroovyShell", "groovy.lang.GroovyClassLoader", "groovy.lang.Eval",
            "org.codehaus.groovy.runtime");
    private static final List<String> BLACKLIST_STAR_IMPORTS = List.of(
            "java.io", "java.nio", "java.net", "java.rmi", "java.sql", "javax.script",
            "java.lang.reflect", "java.lang.invoke", "javax.naming",
            "org.codehaus.groovy.runtime");

    private final ConcurrentHashMap<String, Class<? extends Script>> compileCache = new ConcurrentHashMap<>();

    /** 守护线程池：上限 8 线程，超时 60s 回收，防止并发脚本求值耗尽系统线程 */
    private final ThreadPoolExecutor evalExecutor = new ThreadPoolExecutor(
            0, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "api-custom-fn-eval");
                t.setDaemon(true);
                return t;
            });

    /** 保存前语法校验：不可编译抛 7019（message 携带编译错误摘要） */
    public void assertCompilable(String script) {
        compile(script);
    }

    /**
     * 执行脚本体并返回结果。
     *
     * @param script           Groovy 脚本体
     * @param args             调用参数（已由模板引擎解析为字符串）
     * @param contextVariables Ryze 上下文链中的变量（环境/场景/步骤），注入脚本 Binding 供直接访问
     */
    public Object evaluate(String script, List<Object> args, Map<String, Object> contextVariables) {
        Class<? extends Script> clazz = compile(script);
        Future<Object> future = evalExecutor.submit(() -> {
            Script instance = clazz.getDeclaredConstructor().newInstance();
            Binding binding = new Binding();
            binding.setVariable("args", args == null ? List.of() : args);
            if (contextVariables != null) {
                contextVariables.forEach(binding::setVariable);
            }
            instance.setBinding(binding);
            return instance.run();
        });
        try {
            return future.get(EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_FUNCTION_EVAL_FAILED, "求值超时（>" + EVAL_TIMEOUT_MS + "ms）");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.info("[api-function] 脚本求值异常: {}", cause.getMessage());
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_FUNCTION_EVAL_FAILED, cause.getMessage());
        }
    }

    private Class<? extends Script> compile(String script) {
        String key = sha256(script);
        Class<? extends Script> cached = compileCache.get(key);
        if (cached != null) {
            return cached;
        }
        CompilerConfiguration configuration = new CompilerConfiguration();
        SecureASTCustomizer secure = new SecureASTCustomizer();
        secure.setImportsBlacklist(BLACKLIST_IMPORTS);
        secure.setStarImportsBlacklist(BLACKLIST_STAR_IMPORTS);
        secure.setIndirectImportCheckEnabled(true);
        configuration.addCompilationCustomizers(secure);
        try (GroovyClassLoader loader = new GroovyClassLoader(ApiFunctionScriptEngine.class.getClassLoader(), configuration)) {
            Class<?> parsed = loader.parseClass(script);
            if (compileCache.size() >= CACHE_MAX) {
                compileCache.clear();
            }
            Class<? extends Script> clazz = parsed.asSubclass(Script.class);
            compileCache.put(key, clazz);
            return clazz;
        } catch (MultipleCompilationErrorsException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_CUSTOM_FUNCTION_SCRIPT_INVALID,
                    firstErrorLine(e.getMessage()));
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_CUSTOM_FUNCTION_SCRIPT_INVALID,
                    e.getMessage() != null ? e.getMessage() : "未知编译错误");
        }
    }

    private static String firstErrorLine(String message) {
        if (message == null) {
            return "语法错误";
        }
        for (String line : message.split("\n")) {
            if (line.contains("error:") || line.contains("ERROR")) {
                return line.trim();
            }
        }
        return message.split("\n")[0].trim();
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JVM 必带 SHA-256，此分支仅为受检签名兜底
            return String.valueOf(text.hashCode());
        }
    }
}
