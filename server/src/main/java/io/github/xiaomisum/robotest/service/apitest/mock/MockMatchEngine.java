package io.github.xiaomisum.robotest.service.apitest.mock;

import tools.jackson.databind.JsonNode;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiMockDefinition;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Mock 匹配引擎（Mock服务详细设计 4.1）。
 * 规则：方法一致 + 路径匹配（支持 * 通配）+ 全部 match_rules 命中；规则为空时仅按方法+路径。
 * value 支持普通值或正则表达式：优先按整串正则解释，正则非法时退化为字符串相等比较。
 */
public class MockMatchEngine {

    private MockMatchEngine() {
    }

    public static boolean matches(ApiMockDefinition definition, String method, String path,
                                  Map<String, String> headers, Map<String, String> queryParams,
                                  JsonNode bodyNode) {
        if (!definition.getMethod().equalsIgnoreCase(method)) {
            return false;
        }
        if (!pathMatches(definition.getPath(), path)) {
            return false;
        }
        List<Map<String, Object>> rules = definition.getMatchRules();
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        return rules.stream().allMatch(rule -> ruleMatches(rule, headers, queryParams, bodyNode));
    }

    /** * 通配符转正则；其余字符按字面量处理 */
    static boolean pathMatches(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        if (!pattern.contains("*")) {
            return pattern.equals(path);
        }
        String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
        return path.matches(regex);
    }

    private static boolean ruleMatches(Map<String, Object> rule, Map<String, String> headers,
                                       Map<String, String> queryParams, JsonNode bodyNode) {
        String type = String.valueOf(rule.get("type"));
        String name = rule.get("name") == null ? "" : String.valueOf(rule.get("name"));
        String expected = rule.get("value") == null ? "" : String.valueOf(rule.get("value"));
        return switch (type) {
            case "header" -> valueMatches(headerValue(headers, name), expected);
            case "param" -> valueMatches(queryParams == null ? null : queryParams.get(name), expected);
            case "body" -> bodyMatches(bodyNode, name, expected);
            default -> false;
        };
    }

    /** HTTP 头名大小写不敏感，按忽略大小写方式取值 */
    private static String headerValue(Map<String, String> headers, String name) {
        if (headers == null || name.isEmpty()) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    static boolean valueMatches(String actual, String expected) {
        if (actual == null) {
            return false;
        }
        try {
            return Pattern.compile(expected).matcher(actual).matches();
        } catch (PatternSyntaxException e) {
            return actual.equals(expected);
        }
    }

    /**
     * JSONPath 子集（$.a.b[0].c）：覆盖 Mock 规则常见的字段定位场景，
     * 复杂表达式不在首期范围（需求 3.3 以 Ryze 语法为准，此处仅用于规则命中判断）。
     */
    static JsonNode extract(JsonNode bodyNode, String jsonPath) {
        if (bodyNode == null || jsonPath == null || jsonPath.isBlank()) {
            return null;
        }
        String normalized = jsonPath.trim();
        if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        JsonNode current = bodyNode;
        for (String segment : normalized.split("\\.")) {
            if (segment.isEmpty()) {
                continue;
            }
            String field = segment;
            int bracket = segment.indexOf('[');
            if (bracket >= 0) {
                field = segment.substring(0, bracket);
            }
            if (!field.isEmpty()) {
                current = current.get(field);
                if (current == null) {
                    return null;
                }
            }
            while (bracket >= 0) {
                int close = segment.indexOf(']', bracket);
                if (close < 0) {
                    return null;
                }
                String indexText = segment.substring(bracket + 1, close);
                try {
                    current = current.get(Integer.parseInt(indexText));
                } catch (NumberFormatException e) {
                    return null;
                }
                if (current == null) {
                    return null;
                }
                bracket = segment.indexOf('[', close);
            }
        }
        return current;
    }

    private static boolean bodyMatches(JsonNode bodyNode, String jsonPath, String expected) {
        JsonNode node = extract(bodyNode, jsonPath);
        if (node == null) {
            return false;
        }
        String actual = node.isValueNode() ? node.asText() : node.toString();
        return valueMatches(actual, expected);
    }

}
