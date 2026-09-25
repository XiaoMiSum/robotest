package io.github.xiaomisum.robotest.framework.security;

import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 敏感字段递归脱敏（安全规范 6.4、8.3；实时协议详细设计 3.2）：
 * 按字段名匹配，对任意深度的 Map/List/字符串凭证与请求头统一掩码，
 * 供审计日志、Mock 访问日志与错误日志复用，避免嵌套 DTO 绕过顶层参数名过滤。
 */
public final class SensitiveDataMasker {

    public static final String MASK = "***";

    /** 字段名包含以下片段即视为敏感（大小写不敏感） */
    private static final List<String> SENSITIVE_KEY_SNIPPETS = List.of(
            "password", "passwd", "pwd", "token", "secret", "ticket",
            "apikey", "api-key", "api_key", "authorization", "cookie", "credential");

    /** 字符串值内嵌的凭证（如 JDBC 连接串中的 password=xxx） */
    private static final Pattern INLINE_CREDENTIAL = Pattern.compile(
            "(?i)([?&;\\s]|^)([\\w-]*(?:password|passwd|pwd|secret|token|apikey|api[_-]?key)[\\w-]*)=([^&\\s\"']+)");

    private SensitiveDataMasker() {
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_SNIPPETS.stream().anyMatch(lower::contains);
    }

    /** 递归脱敏任意值：Map/List 深入遍历，字符串内嵌凭证替换，标量原样保留 */
    public static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> out = new ArrayList<>();
            for (Object item : iterable) {
                out.add(sanitizeValue(item));
            }
            return out;
        }
        if (value instanceof String text) {
            return sanitizeInlineCredentials(text);
        }
        return value;
    }

    public static Map<String, Object> sanitizeMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (isSensitiveKey(key)) {
                out.put(key, MASK);
                continue;
            }
            out.put(key, sanitizeValue(entry.getValue()));
        }
        maskNameValuePair(out);
        return out;
    }

    /** 请求头/变量类条目（{key|name: 敏感名, value: ...}）按名掩掉 value */
    private static void maskNameValuePair(Map<String, Object> map) {
        for (String nameField : List.of("key", "name")) {
            if (map.get(nameField) instanceof String name && isSensitiveKey(name) && map.containsKey("value")) {
                map.put("value", MASK);
                return;
            }
        }
    }

    /** 请求头脱敏：敏感头整体掩码，其余保留 */
    public static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((name, value) -> out.put(name, isSensitiveKey(name) ? MASK : value));
        return out;
    }

    private static String sanitizeInlineCredentials(String value) {
        return INLINE_CREDENTIAL.matcher(value).replaceAll("$1$2=" + MASK);
    }

    /**
     * 错误日志 requestParams（{@code {"query":...,"body":"<原始请求体>"}}）脱敏。
     * 查询参数按键名掩码；对象请求体逐字段递归，非对象请求体无法逐字段脱敏时整体掩码；
     * 解析失败整体掩码，保证凭证不外泄。
     */
    public static String sanitizeErrorParams(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        Map<String, Object> root;
        try {
            root = JsonUtils.parseObject(raw, Map.class);
        } catch (RuntimeException exception) {
            return MASK;
        }
        if (root == null) {
            return MASK;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            if ("body".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof String body) {
                out.put(entry.getKey(), sanitizeBody(body));
            } else {
                out.put(entry.getKey(), sanitizeValue(entry.getValue()));
            }
        }
        try {
            return JsonUtils.toJsonString(out);
        } catch (RuntimeException exception) {
            return MASK;
        }
    }

    private static String sanitizeBody(String body) {
        if (body.isBlank()) {
            return body;
        }
        try {
            Map<String, Object> parsed = JsonUtils.parseObject(body, Map.class);
            return parsed == null ? MASK : JsonUtils.toJsonString(sanitizeMap(parsed));
        } catch (RuntimeException exception) {
            return MASK;
        }
    }
}
