package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.ryze.context.ContextWrapper;
import io.github.xiaomisum.ryze.function.Args;
import io.github.xiaomisum.ryze.function.Function;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台统一注册的 SPI 函数：所有自定义函数经 {@code ${__robotest_custom("name", ...)}} 调用。
 *
 * <p>为什么经由 ServiceLoader 注册单个分发器而非运行时动态注册多个函数：
 * Ryze 的 FreeMarkerFunctionRegistry 为静态缓存，不支持执行期增删；
 * 单一分发器 + 名称重写即可在不动框架的前提下接入任意数量的自定义函数。</p>
 *
 * <p>通过 META-INF/services/io.github.xiaomisum.ryze.function.Function 声明，
 * 由 Ryze ApplicationConfig 初始化时加载；Spring 容器就绪后回填运行时引用。</p>
 */
public class RobotestCustomFunctionDispatcher implements Function {

    public static final String KEY = "robotest_custom";

    /** Spring 启动后由 CustomFunctionRuntime 回填；未初始化时调用视为求值失败 */
    private static volatile CustomFunctionRuntime runtime;

    static void bind(CustomFunctionRuntime instance) {
        runtime = instance;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Object execute(ContextWrapper contextWrapper, Args args) {
        CustomFunctionRuntime rt = runtime;
        if (rt == null || contextWrapper == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_FUNCTION_EVAL_FAILED, "自定义函数运行时不可用");
        }
        if (args == null || args.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_FUNCTION_EVAL_FAILED, "缺少函数名参数");
        }
        String name = args.getString(0);
        UUID projectId = rt.resolveProjectId(contextWrapper);
        CustomFunctionRuntime.ScriptEntry entry = rt.resolve(projectId, name);
        if (entry == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_FUNCTION_EVAL_FAILED,
                    "自定义函数不存在或已停用: __" + name);
        }
        // 首参为函数名占位，真实参数从下标 1 开始；失败按步骤级异常抛出，
        // 是否中断整个场景由平台失败规则决定（不额外吞异常）
        List<Object> realArgs = (args.size() > 1)
                ? new java.util.ArrayList<>(args.subList(1, args.size()))
                : List.of();
        Map<String, Object> contextVars = new LinkedHashMap<>();
        if (contextWrapper != null) {
            contextVars.putAll(contextWrapper.getAllVariablesWrapper().mergeVariables());
        }
        return entry.execute(realArgs, contextVars);
    }
}
