package io.github.xiaomisum.robotest.framework.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全规范 6.2 守卫（backlog SEC-011）：Bearer Header 场景默认不启用 CSRF，
 * 服务端不注册 CORS 通配配置；migoo 升级或自定义安全链改变默认时本测试失败，
 * 要求先重新评估 CSRF/CORS 适用边界并更新安全规范再放行。
 */
class CsrfCorsBoundaryTest {

    /** migoo 框架安全链（依赖 jar 内）：csrf 绑定 CsrfConfigurer→AbstractHttpConfigurer#disable */
    private static final String SECURITY_CHAIN_CLASS =
            "xyz/migoo/framework/security/config/MiGooWebSecurityFilterChainConfiguration.class";

    /** Spring CORS 通配 Origin 写法：业务源码均不得出现（须改用明确白名单） */
    private static final List<String> CORS_WILDCARDS = List.of(
            "allowedOrigins(\"*\")",
            "allowedOriginPatterns(\"*\")",
            "setAllowedOrigins(\"*\")",
            "setAllowedOriginPatterns(\"*\")");

    @Test
    void frameworkSecurityChainDisablesCsrf() throws IOException {
        String text;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SECURITY_CHAIN_CLASS)) {
            assertNotNull(in, "migoo 安全链类未找到，SEC-011 的 CSRF 边界需重新评估");
            text = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
        // 框架引导表将 (CsrfConfigurer)V Lambda 绑定到 AbstractHttpConfigurer#disable；
        // 三个常量必须同时出现，缺一即说明框架默认已改变
        assertTrue(text.contains("CsrfConfigurer"),
                "migoo 安全链不再引用 CsrfConfigurer，Bearer 场景 CSRF 默认需重新评估：" + SECURITY_CHAIN_CLASS);
        assertTrue(text.contains("AbstractHttpConfigurer") && text.contains("disable"),
                "migoo 安全链不再以 AbstractHttpConfigurer#disable 关闭 CSRF，SEC-011 边界需重新评估");
    }

    @Test
    void mainSourcesConfigureNoCorsWildcard() throws IOException {
        for (Path file : javaSources()) {
            String content = Files.readString(file);
            for (String wildcard : CORS_WILDCARDS) {
                assertFalse(content.contains(wildcard),
                        "禁止注册 CORS 通配 Origin（安全规范 6.2：须使用明确 Origin 白名单，禁止生产使用 *）：" + file);
            }
        }
    }

    @Test
    void applicationDoesNotOverrideFrameworkSecurityChain() throws IOException {
        for (Path file : javaSources()) {
            assertFalse(Files.readString(file).contains("SecurityFilterChain"),
                    "自定义 SecurityFilterChain 属 CSRF/CORS 适用边界变更，须先更新安全规范 6.2 并重评 SEC-011：" + file);
        }
    }

    private List<Path> javaSources() throws IOException {
        Path root = Path.of("src/main/java");
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(path -> path.toString().endsWith(".java")).toList();
            assertFalse(files.isEmpty(), "未扫描到后端源码：" + root);
            return files;
        }
    }
}
