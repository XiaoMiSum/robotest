package io.github.xiaomisum.robotest.service.ai.task.core;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 执行模型工具参数的最小 JSON Schema 子集校验：required/type/enum/maxItems。 */
@Component
public class AgentToolArgumentValidator {

    public String validate(Map<String, Object> schema, Map<String, Object> args) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        Object required = schema.get("required");
        if (required instanceof List<?> names) {
            for (Object name : names) {
                if (!args.containsKey(String.valueOf(name)) || args.get(String.valueOf(name)) == null) {
                    return "缺少必填参数: " + name;
                }
            }
        }
        Object rawProperties = schema.get("properties");
        if (!(rawProperties instanceof Map<?, ?> properties)) {
            return null;
        }
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            Object rawRule = properties.get(entry.getKey());
            if (!(rawRule instanceof Map<?, ?> rule) || entry.getValue() == null) {
                continue;
            }
            if (!matchesType(String.valueOf(rule.get("type")), entry.getValue())) {
                return "参数类型非法: " + entry.getKey();
            }
            if (rule.get("enum") instanceof List<?> allowed && !allowed.contains(entry.getValue())) {
                return "参数枚举值非法: " + entry.getKey();
            }
            if (entry.getValue() instanceof List<?> values && rule.get("maxItems") instanceof Number max
                    && values.size() > max.intValue()) {
                return "参数数组超限: " + entry.getKey();
            }
        }
        return null;
    }

    private static boolean matchesType(String type, Object value) {
        return switch (type) {
            case "null", "" -> true;
            case "string" -> value instanceof String;
            case "array" -> value instanceof List<?>;
            case "object" -> value instanceof Map<?, ?>;
            case "integer" -> value instanceof Byte || value instanceof Short || value instanceof Integer
                    || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            default -> true;
        };
    }
}
