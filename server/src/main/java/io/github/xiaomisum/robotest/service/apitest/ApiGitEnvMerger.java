package io.github.xiaomisum.robotest.service.apitest;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境声明合并组件（GitLab 集成详设 3.5，SRS 3.3 BR-1.16~1.20）。
 * <p>
 * 源侧 Ryze env.yaml 元件格式（{testclass: http|jdbc, ref_name, config}）转换为平台
 * http_configs / data_sources 结构，并按 refName 覆盖式合并（保留用户自行添加的其他条目）。
 * <p>
 * 纯函数组件：无 Spring 依赖、无 I/O，解析失败由调用方降级（不中止导入）。
 */
public final class ApiGitEnvMerger {

    /** Ryze 端口缺省（http 80）：与协议默认端口相同时 baseUrl 不携带端口 */
    private static final Map<String, Integer> DEFAULT_PORTS = Map.of("http", 80, "https", 443);
    private static final String REF_DEFAULT = "default";
    private static final String DRIVER_MYSQL = "com.mysql.cj.jdbc.Driver";
    private static final String DRIVER_POSTGRES = "org.postgresql.Driver";
    /** Redis 无 JDBC 驱动（Ryze 内置客户端），平台约定以 - 占位（环境管理详设 2.1.4） */
    private static final String DRIVER_PLACEHOLDER = "-";
    private static final Pattern USER_INFO = Pattern.compile("\\?(.*)$", Pattern.DOTALL);
    private static final int MAX_POOL_SIZE_DEFAULT = 5;

    /** 合并目标：平台结构的两组条目 */
    public record MergeTarget(List<Map<String, Object>> httpConfigs, List<Map<String, Object>> dataSources) {
    }

    /** 解析结果：两组条目 + 跳过的元件说明（计入导入明细） */
    public record ParsedEnv(MergeTarget target, List<String> skipped) {
    }

    private ApiGitEnvMerger() {
    }

    /**
     * 解析 env.yaml 文本为平台结构。根结构必须是元件列表；无法识别的元件
     * （未知 testclass、缺 config、驱动不受支持的 jdbc）跳过并记录原因，不抛异常。
     */
    @SuppressWarnings("unchecked")
    public static ParsedEnv parse(String yamlText) {
        Object root = new Yaml(new SafeConstructor(new org.yaml.snakeyaml.LoaderOptions())).load(yamlText);
        List<Map<String, Object>> httpConfigs = new ArrayList<>();
        List<Map<String, Object>> dataSources = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if (!(root instanceof List<?> elements)) {
            skipped.add("环境声明文件根结构不是元件列表");
            return new ParsedEnv(new MergeTarget(httpConfigs, dataSources), skipped);
        }
        for (Object element : elements) {
            if (!(element instanceof Map<?, ?> raw)) {
                skipped.add("元件不是映射结构");
                continue;
            }
            Map<String, Object> item = (Map<String, Object>) raw;
            String testclass = str(item.get("testclass"));
            Map<String, Object> config = item.get("config") instanceof Map<?, ?>
                    ? (Map<String, Object>) item.get("config") : Map.of();
            String refName = firstNonBlank(str(item.get("ref_name")), REF_DEFAULT);
            switch (testclass) {
                case "http" -> {
                    // Ryze 语义：无 ref_name 的首条 http 元件即默认配置，对应平台 isDefault
                    boolean defaultCandidate = httpConfigs.isEmpty() && str(item.get("ref_name")).isBlank();
                    httpConfigs.add(httpConfig(refName, config, defaultCandidate));
                }
                case "jdbc" -> {
                    Map<String, Object> dataSource = dataSource(refName, config);
                    if (dataSource == null) {
                        skipped.add("jdbc 元件 " + refName + "：驱动不受支持或 url 缺失");
                    } else {
                        dataSources.add(dataSource);
                    }
                }
                default -> skipped.add("未知元件类型：" + testclass);
            }
        }
        return new ParsedEnv(new MergeTarget(httpConfigs, dataSources), skipped);
    }

    /**
     * 按 refName 覆盖式合并（BR-1.16）：导入条目与既有同 refName 同类条目整行替换，
     * 无同名的追加；用户自行添加的其余条目原样保留。
     */
    public static MergeTarget merge(MergeTarget existing, MergeTarget imported) {
        return new MergeTarget(
                mergeByRefName(existing.httpConfigs(), imported.httpConfigs(), true),
                mergeByRefName(existing.dataSources(), imported.dataSources(), false));
    }

    private static List<Map<String, Object>> mergeByRefName(List<Map<String, Object>> existing,
            List<Map<String, Object>> imported, boolean http) {
        List<Map<String, Object>> merged = new ArrayList<>(existing);
        for (Map<String, Object> entry : imported) {
            String refName = str(entry.get("refName"));
            boolean replaced = false;
            for (int i = 0; i < merged.size(); i++) {
                if (refName.equals(str(merged.get(i).get("refName")))) {
                    merged.set(i, entry);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                merged.add(entry);
            }
        }
        // isDefault 归一：http 配置至多一个默认，取导入侧首个默认，否则保持既有
        if (http) {
            normalizeDefault(merged);
        }
        return merged;
    }

    private static void normalizeDefault(List<Map<String, Object>> httpConfigs) {
        boolean seen = false;
        for (int i = 0; i < httpConfigs.size(); i++) {
            Map<String, Object> config = httpConfigs.get(i);
            boolean isDefault = Boolean.TRUE.equals(config.get("isDefault")) && !seen;
            // 条目可能来自导入解析或平台侧既有 JSONB（均按不可变 Map 处理，改写而非原地修改）
            if (Boolean.TRUE.equals(config.get("isDefault")) != isDefault) {
                Map<String, Object> normalized = new LinkedHashMap<>(config);
                normalized.put("isDefault", isDefault);
                httpConfigs.set(i, normalized);
            }
            seen |= isDefault;
        }
    }

    private static Map<String, Object> httpConfig(String refName, Map<String, Object> config, boolean defaultCandidate) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", refName);
        entry.put("refName", refName);
        entry.put("baseUrl", baseUrl(config));
        entry.put("headers", headers(config.get("headers")));
        entry.put("isDefault", defaultCandidate);
        return entry;
    }

    private static String baseUrl(Map<String, Object> config) {
        String protocol = firstNonBlank(str(config.get("protocol")), "http");
        String host = firstNonBlank(str(config.get("host")), "");
        Object port = config.get("port");
        if (port instanceof Number number && !isDefaultPort(protocol, number.intValue())) {
            return protocol + "://" + host + ":" + number.intValue();
        }
        return protocol + "://" + host;
    }

    private static boolean isDefaultPort(String protocol, int port) {
        return DEFAULT_PORTS.getOrDefault(protocol, -1) == port;
    }

    private static List<Map<String, Object>> headers(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Map<String, Object> header = new LinkedHashMap<>();
                header.put("key", str(entry.getKey()));
                header.put("value", str(entry.getValue()));
                header.put("enabled", true);
                result.add(header);
            }
        }
        return result;
    }

    private static Map<String, Object> dataSource(String refName, Map<String, Object> config) {
        String url = str(config.get("url"));
        if (url.isBlank()) {
            return null;
        }
        String driver = driverOf(url);
        if (driver == null) {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", refName);
        entry.put("refName", refName);
        entry.put("driver", driver);
        entry.put("url", withCredentials(url, config));
        entry.put("connectionProperties", Map.of());
        entry.put("maxPoolSize", MAX_POOL_SIZE_DEFAULT);
        entry.put("isDefault", false);
        return entry;
    }

    /** 按 url 协议推断驱动：平台连接测试白名单仅 mysql / postgresql，Redis 以 - 占位 */
    private static String driverOf(String url) {
        if (url.startsWith("jdbc:mysql:")) {
            return DRIVER_MYSQL;
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return DRIVER_POSTGRES;
        }
        if (url.startsWith("redis://") || url.startsWith("rediss://")) {
            return DRIVER_PLACEHOLDER;
        }
        return null;
    }

    /** 平台约定用户名密码直接写入 URL（环境管理详设 2.1.4）：追加查询参数，已有同名参数不覆盖 */
    private static String withCredentials(String url, Map<String, Object> config) {
        String user = str(config.get("username"));
        String password = str(config.get("password"));
        if (user.isBlank() && password.isBlank()) {
            return url;
        }
        if (url.startsWith("redis://") || url.startsWith("rediss://")) {
            return redisUrl(url, user, password);
        }
        StringBuilder suffix = new StringBuilder();
        Matcher matcher = USER_INFO.matcher(url);
        boolean hasQuery = matcher.find();
        // 已有查询串时首个新参数以 & 起头；首个参数被跳过而后续参数需追加时同样适用
        boolean leadingAmpersand = hasQuery;
        if (!user.isBlank() && !(hasQuery && matcher.group(1).contains("user="))) {
            appendParam(suffix, "user", user, leadingAmpersand);
            leadingAmpersand = false;
        }
        if (!password.isBlank() && !(hasQuery && matcher.group(1).contains("password="))) {
            appendParam(suffix, "password", password, leadingAmpersand);
        }
        if (suffix.isEmpty()) {
            return url;
        }
        return url + (hasQuery ? "" : "?") + suffix;
    }

    private static void appendParam(StringBuilder suffix, String key, String value, boolean withAmpersand) {
        if (!suffix.isEmpty() || withAmpersand) {
            suffix.append('&');
        }
        suffix.append(key).append('=').append(value);
    }

    /** Redis url 格式 redis://[password@]host:port/db（环境管理详设 2.1.4）：密码插在 host 前 */
    private static String redisUrl(String url, String user, String password) {
        if (password.isBlank()) {
            return url;
        }
        int schemeEnd = url.indexOf("://") + 3;
        return url.substring(0, schemeEnd) + password + "@" + url.substring(schemeEnd);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
