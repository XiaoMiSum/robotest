package io.github.xiaomisum.robotest.service.apitest;

import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class RyzeYamlToSceneConverter {

    private static final Pattern ANCHORED_START = Pattern.compile("^\\^.*");
    private static final Pattern ANCHORED_END = Pattern.compile("\\$$");

    private RyzeYamlToSceneConverter() {
    }

    public static Map<String, Object> convert(String ryzeJson) {
        Map<String, Object> suite = JsonUtils.parseObject(ryzeJson,
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
        return convertSuite(suite);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> convertSuite(Map<String, Object> suite) {
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("name", str(suite.get("title")));
        scene.put("description", null);
        scene.put("variables", reverseVariables(suite.get("variables")));
        scene.put("steps", convertChildren(suite.get("children")));
        return scene;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> reverseVariables(Object variablesObj) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (variablesObj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("name", entry.getKey() == null ? "" : entry.getKey().toString());
                v.put("value", entry.getValue() == null ? "" : entry.getValue().toString());
                result.add(v);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> convertChildren(Object childrenObj) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (!(childrenObj instanceof List<?> list)) {
            return steps;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> sampler) {
                steps.add(convertSampler(sampler));
            }
        }
        return steps;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> convertSampler(Map<?, ?> sampler) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", str(sampler.get("title")));
        step.put("type", str(sampler.get("testclass")));
        step.put("requestConfig", reverseConfig(sampler.get("config")));
        step.put("validators", reverseValidators(sampler.get("validators")));
        step.put("extractors", reverseExtractors(sampler.get("extractors")));
        return step;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Map<String, Object> reverseConfig(Object configObj) {
        if (!(configObj instanceof Map<?, ?> config)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> rc = new LinkedHashMap<>((Map) config);

        String baseUrl = str(rc.remove("base_url"));
        if (!baseUrl.isBlank()) {
            rc.put("url", baseUrl);
        }

        Object headersObj = rc.remove("headers");
        if (headersObj instanceof Map<?, ?> headers) {
            rc.put("headers", entriesFromMap(headers));
        }

        Object queryObj = rc.remove("query");
        if (queryObj instanceof Map<?, ?> query) {
            rc.put("params", entriesFromMap(query));
        }

        Object bodyObj = rc.get("body");
        Object dataObj = rc.remove("data");
        if (dataObj != null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", "form");
            body.put("content", dataObj instanceof Map<?, ?> dataMap ? entriesFromMap(dataMap) : dataObj);
            rc.put("body", body);
        } else if (bodyObj != null) {
            if (bodyObj instanceof Map<?, ?> bodyMap) {
                Object typeObj = bodyMap.get("type");
                String type = str(typeObj == null ? "json" : typeObj).toLowerCase();
                if (!"none".equals(type) && !"".equals(type)) {
                    Object content = bodyMap.get("content");
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    normalized.put("type", type);
                    normalized.put("content", "form".equals(type) && content instanceof Map<?, ?> formMap
                            ? entriesFromMap(formMap) : content);
                    rc.put("body", normalized);
                } else {
                    rc.remove("body");
                }
            }
        }

        return rc;
    }

    static List<Map<String, Object>> entriesFromMap(Map<?, ?> map) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toString();
            if (key.isBlank()) {
                continue;
            }
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("key", key);
            e.put("value", entry.getValue() == null ? "" : entry.getValue().toString());
            e.put("enabled", true);
            entries.add(e);
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> reverseValidators(Object validatorsObj) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(validatorsObj instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> element)) {
                continue;
            }
            String testclass = str(element.get("testclass"));
            String rule = str(element.get("rule") != null ? element.get("rule") : "equals");
            String expected = str(element.get("expected"));
            String field = str(element.get("field"));

            Map<String, Object> v = new LinkedHashMap<>();
            v.put("name", str(element.get("title")));
            v.put("enabled", true);

            switch (testclass) {
                case "status" -> {
                    v.put("target", "status_code");
                    v.put("condition", reverseCompareRule(rule));
                    v.put("expected", expected);
                }
                case "json" -> {
                    v.put("target", "json_field");
                    v.put("condition", reverseCompareRule(rule));
                    v.put("expected", expected);
                    v.put("expression", field);
                }
                case "http" -> {
                    v.put("target", "response_header");
                    v.put("condition", reverseCompareRule(rule));
                    v.put("expected", expected);
                    String headerField = field.startsWith("headers.") ? field.substring(8) : field;
                    v.put("expression", headerField);
                }
                case "result" -> {
                    if ("regex".equals(rule)) {
                        if (ANCHORED_START.matcher(expected).find() && ANCHORED_END.matcher(expected).find()) {
                            v.put("target", "response_body");
                            v.put("condition", "matches_regex");
                            v.put("expected", expected);
                        } else if (ANCHORED_START.matcher(expected).find()) {
                            v.put("target", "response_body");
                            v.put("condition", "starts_with");
                            String unquoted = unquote(expected);
                            v.put("expected", unquoted.startsWith("^") ? unquoted.substring(1) : unquoted);
                        } else if (ANCHORED_END.matcher(expected).find()) {
                            v.put("target", "response_body");
                            v.put("condition", "ends_with");
                            String unquoted = unquote(expected);
                            v.put("expected", unquoted.endsWith("$") ? unquoted.substring(0, unquoted.length() - 1) : unquoted);
                        } else {
                            v.put("target", "regex");
                            v.put("condition", "matches_regex");
                            v.put("expression", expected);
                        }
                    } else {
                        v.put("target", "response_body");
                        v.put("condition", reverseCompareRule(rule));
                        v.put("expected", expected);
                    }
                }
                default -> {
                    v.put("target", testclass);
                    v.put("condition", reverseCompareRule(rule));
                    v.put("expected", expected);
                }
            }
            result.add(v);
        }
        return result;
    }

    static String reverseCompareRule(String rule) {
        return switch (rule) {
            case "equals" -> "equals";
            case "not_equals" -> "not_equals";
            case "contains" -> "contains";
            case "not_contains" -> "not_contains";
            case "gt" -> "greater_than";
            case "lt" -> "less_than";
            case "gte" -> "greater_or_equal";
            case "lte" -> "less_or_equal";
            case "regex" -> "matches_regex";
            default -> rule;
        };
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> reverseExtractors(Object extractorsObj) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(extractorsObj instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> element)) {
                continue;
            }
            String testclass = str(element.get("testclass"));
            String field = str(element.get("field"));
            String refName = str(element.get("ref_name"));

            Map<String, Object> ex = new LinkedHashMap<>();
            ex.put("name", str(element.get("title")));
            ex.put("enabled", true);
            ex.put("variableName", refName);

            switch (testclass) {
                case "json" -> {
                    ex.put("source", "json_field");
                    ex.put("expression", field);
                }
                case "http_header" -> {
                    ex.put("source", "response_header");
                    ex.put("expression", field);
                }
                case "regex" -> {
                    ex.put("source", "regex");
                    ex.put("expression", field);
                }
                case "result" -> ex.put("source", "full_body");
                default -> {
                    ex.put("source", testclass);
                    ex.put("expression", field);
                }
            }
            result.add(ex);
        }
        return result;
    }

    private static String unquote(String s) {
        if (s == null) return "";
        if (s.startsWith("\\Q") && s.endsWith("\\E")) {
            return s.substring(2, s.length() - 2);
        }
        return s;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
