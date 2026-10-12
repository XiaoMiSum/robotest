package io.github.xiaomisum.robotest.service.apitest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 环境声明合并组件测试：Ryze 元件到平台结构的字段映射与按 refName 覆盖式合并（详设 3.5，BR-1.16）。
 */
class ApiGitEnvMergerTest {

    private static final String ENV_YAML = """
            - testclass: http
              config:
                protocol: http
                host: 127.0.0.1
                port: 7612
            - testclass: jdbc
              ref_name: test_env
              config:
                url: jdbc:mysql://127.0.0.1:3306/testdb
                username: root
                password: 123456
            - testclass: http
              ref_name: test_w
              config:
                protocol: http
                host: 127.0.0.1
                port: 80
                headers:
                  systemid: 12323
            """;

    // ========== 解析映射 ==========

    @Test
    void parsesHttpConfigsAndDataSources() {
        ApiGitEnvMerger.ParsedEnv parsed = ApiGitEnvMerger.parse(ENV_YAML);

        assertTrue(parsed.skipped().isEmpty());
        List<Map<String, Object>> http = parsed.target().httpConfigs();
        assertEquals(2, http.size());
        // 无 ref_name 的首条 http 元件：refName 缺省 default 且为默认配置
        assertEquals("default", http.get(0).get("refName"));
        assertEquals("http://127.0.0.1:7612", http.get(0).get("baseUrl"));
        assertTrue((Boolean) http.get(0).get("isDefault"));
        assertEquals(List.of(), http.get(0).get("headers"));
        // 协议默认端口省略
        assertEquals("test_w", http.get(1).get("refName"));
        assertEquals("http://127.0.0.1", http.get(1).get("baseUrl"));
        assertFalse((Boolean) http.get(1).get("isDefault"));
        List<Map<String, Object>> headers = castList(http.get(1).get("headers"));
        assertEquals(1, headers.size());
        assertEquals("systemid", headers.get(0).get("key"));
        assertEquals("12323", headers.get(0).get("value"));
        assertEquals(true, headers.get(0).get("enabled"));

        List<Map<String, Object>> dataSources = parsed.target().dataSources();
        assertEquals(1, dataSources.size());
        Map<String, Object> ds = dataSources.get(0);
        assertEquals("test_env", ds.get("refName"));
        assertEquals("com.mysql.cj.jdbc.Driver", ds.get("driver"));
        // 用户名密码并入 URL 查询串（环境管理详设 2.1.4）
        assertEquals("jdbc:mysql://127.0.0.1:3306/testdb?user=root&password=123456", ds.get("url"));
        assertEquals(5, ds.get("maxPoolSize"));
    }

    @Test
    void postgresAndRedisDriverInference() {
        ApiGitEnvMerger.ParsedEnv parsed = ApiGitEnvMerger.parse("""
                - testclass: jdbc
                  ref_name: pg
                  config:
                    url: jdbc:postgresql://db:5432/app
                - testclass: jdbc
                  ref_name: cache
                  config:
                    url: redis://:6379/0
                    password: secret
                """);

        assertTrue(parsed.skipped().isEmpty());
        assertEquals("org.postgresql.Driver", parsed.target().dataSources().get(0).get("driver"));
        Map<String, Object> redis = parsed.target().dataSources().get(1);
        assertEquals("-", redis.get("driver"));
        // Redis 密码插在 host 前（redis://[password@]host:port/db）
        assertEquals("redis://secret@:6379/0", redis.get("url"));
    }

    @Test
    void unknownOrInvalidElementsAreSkippedWithReasons() {
        ApiGitEnvMerger.ParsedEnv parsed = ApiGitEnvMerger.parse("""
                - testclass: dubbo
                  ref_name: rpc
                  config: {}
                - testclass: jdbc
                  ref_name: oracle
                  config:
                    url: jdbc:oracle:thin:@db:1521:orcl
                - testclass: jdbc
                  ref_name: no_url
                  config: {}
                - not a map
                """);

        assertTrue(parsed.target().httpConfigs().isEmpty());
        assertTrue(parsed.target().dataSources().isEmpty());
        assertEquals(4, parsed.skipped().size());
    }

    @Test
    void rootNotListIsSkippedNotThrown() {
        ApiGitEnvMerger.ParsedEnv parsed = ApiGitEnvMerger.parse("key: value");

        assertTrue(parsed.target().httpConfigs().isEmpty());
        assertEquals(1, parsed.skipped().size());
    }

    @Test
    void credentialsKeptWhenUrlAlreadyHasParams() {
        ApiGitEnvMerger.ParsedEnv parsed = ApiGitEnvMerger.parse("""
                - testclass: jdbc
                  ref_name: ds
                  config:
                    url: jdbc:mysql://db:3306/app?serverTimezone=UTC&user=existing
                    username: root
                    password: pw
                """);

        Map<String, Object> ds = parsed.target().dataSources().get(0);
        assertEquals("jdbc:mysql://db:3306/app?serverTimezone=UTC&user=existing&password=pw", ds.get("url"));
    }

    // ========== 合并规则 ==========

    @Test
    void mergeOverwritesByRefNameAndKeepsOthers() {
        ApiGitEnvMerger.MergeTarget existing = new ApiGitEnvMerger.MergeTarget(
                new ArrayList<>(List.of(entry("custom", "http://custom", true))),
                new ArrayList<>(List.of()));
        ApiGitEnvMerger.MergeTarget imported = new ApiGitEnvMerger.MergeTarget(
                new ArrayList<>(List.of(entry("custom", "http://new", false),
                        entry("default", "http://imported", true))),
                new ArrayList<>(List.of()));

        ApiGitEnvMerger.MergeTarget merged = ApiGitEnvMerger.merge(existing, imported);

        assertEquals(2, merged.httpConfigs().size());
        // 同 refName 整行覆盖（BR-1.16 按引用名覆盖同类条目）
        assertEquals("http://new", merged.httpConfigs().get(0).get("baseUrl"));
        assertEquals("custom", merged.httpConfigs().get(0).get("refName"));
        assertFalse((Boolean) merged.httpConfigs().get(0).get("isDefault"));
        // isDefault 归一至多一个：导入侧默认生效
        assertTrue((Boolean) merged.httpConfigs().get(1).get("isDefault"));
    }

    @Test
    void mergeAppendsNewRefNames() {
        ApiGitEnvMerger.MergeTarget existing = new ApiGitEnvMerger.MergeTarget(
                new ArrayList<>(List.of(entry("a", "http://a", true))), new ArrayList<>(List.of()));
        ApiGitEnvMerger.MergeTarget imported = new ApiGitEnvMerger.MergeTarget(
                new ArrayList<>(List.of(entry("b", "http://b", false))), new ArrayList<>(List.of()));

        ApiGitEnvMerger.MergeTarget merged = ApiGitEnvMerger.merge(existing, imported);

        assertEquals(2, merged.httpConfigs().size());
        assertEquals("a", merged.httpConfigs().get(0).get("refName"));
        assertEquals("b", merged.httpConfigs().get(1).get("refName"));
    }

    @Test
    void dataSourcesMergeIndependentOfHttp() {
        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("refName", "db1");
        ds.put("url", "jdbc:mysql://new");
        ApiGitEnvMerger.MergeTarget existing = new ApiGitEnvMerger.MergeTarget(
                new ArrayList<>(List.of()), new ArrayList<>(List.of(ds)));
        ApiGitEnvMerger.MergeTarget imported = new ApiGitEnvMerger.MergeTarget(
                new ArrayList<>(List.of()), new ArrayList<>(List.of(ds)));

        ApiGitEnvMerger.MergeTarget merged = ApiGitEnvMerger.merge(existing, imported);

        assertEquals(1, merged.dataSources().size());
    }

    private static Map<String, Object> entry(String refName, String baseUrl, boolean isDefault) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", refName);
        entry.put("refName", refName);
        entry.put("baseUrl", baseUrl);
        entry.put("headers", List.of());
        entry.put("isDefault", isDefault);
        return entry;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
