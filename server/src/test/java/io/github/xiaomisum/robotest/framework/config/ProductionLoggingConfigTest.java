package io.github.xiaomisum.robotest.framework.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全规范 6.4 守卫：生产环境不得输出 SQL 参数，不得使用过高日志级别。
 *
 * <p>通过源码文本断言锁定配置，防止后续改动把 StdOutImpl 或 DEBUG 级别带回生产。
 */
class ProductionLoggingConfigTest {

    private static final String BASE_CONFIG = "application.yaml";
    private static final String DEV_CONFIG = "application-dev.yaml";
    private static final String PROD_CONFIG = "application-prod.yaml";
    private static final String LOGBACK_CONFIG = "logback-spring.xml";

    private static final String STDOUT_IMPL = "org.apache.ibatis.logging.stdout.StdOutImpl";
    private static final String NO_LOGGING_IMPL = "org.apache.ibatis.logging.nologging.NoLoggingImpl";

    @Test
    void baseConfigDoesNotEnableSqlLogging() throws IOException {
        String content = readConfig(BASE_CONFIG);

        assertFalse(content.contains(STDOUT_IMPL),
                "基座配置不得启用 StdOutImpl（会无条件输出 SQL 参数）：" + BASE_CONFIG);
    }

    @Test
    void devConfigKeepsStdoutSqlLogging() throws IOException {
        String content = readConfig(DEV_CONFIG);

        assertTrue(content.contains("log-impl: " + STDOUT_IMPL),
                "开发环境应保留 SQL 输出便于排查：" + DEV_CONFIG);
    }

    @Test
    void prodConfigDisablesSqlLogging() throws IOException {
        String content = readConfig(PROD_CONFIG);

        assertTrue(content.contains("log-impl: " + NO_LOGGING_IMPL),
                "生产环境必须显式禁用 SQL 日志输出：" + PROD_CONFIG);
        assertFalse(content.contains(STDOUT_IMPL),
                "生产环境不得出现 StdOutImpl：" + PROD_CONFIG);
    }

    @Test
    void logbackProdRootLevelIsInfo() throws IOException {
        String prodBlock = extractProfile(readConfig(LOGBACK_CONFIG), "<springProfile name=\"prod\">");

        assertTrue(prodBlock.contains("<root level=\"INFO\">"),
                "生产 root 日志级别必须为 INFO");
        assertFalse(prodBlock.contains("DEBUG"),
                "生产环境不得出现 DEBUG 级别：" + prodBlock);
    }

    @Test
    void logbackNonProdKeepsDebugLevel() throws IOException {
        String nonProdBlock = extractProfile(readConfig(LOGBACK_CONFIG), "<springProfile name=\"!prod\">");

        assertTrue(nonProdBlock.contains("<root level=\"DEBUG\">"),
                "非生产环境保留 DEBUG 级别便于开发排查");
    }

    private static String extractProfile(String content, String profileTag) {
        int start = content.indexOf(profileTag);
        assertTrue(start >= 0, "logback-spring.xml 缺少配置段：" + profileTag);
        int end = content.indexOf("</springProfile>", start);
        assertTrue(end > start, "logback-spring.xml 配置段未闭合：" + profileTag);
        return content.substring(start, end);
    }

    private static String readConfig(String name) throws IOException {
        Path path = Path.of("src/main/resources", name);
        assertTrue(Files.exists(path), "配置文件不存在：" + path);
        return Files.readString(path);
    }
}
