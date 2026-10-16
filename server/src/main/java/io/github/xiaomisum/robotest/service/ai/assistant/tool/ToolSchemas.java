package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具参数 JSON Schema 构造辅助（详设 4.1：参数 Schema 为手写常量）。
 * 只提供本清单用到的类型（string / number / array / object），避免重复拼装嵌套 Map。
 */
public final class ToolSchemas {

    private ToolSchemas() {
    }

    public static Map<String, Object> object(List<Prop> props, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Prop prop : props) {
            properties.put(prop.name(), prop.schema());
        }
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    public static Prop string(String name, String description) {
        return string(name, description, null);
    }

    public static Prop string(String name, String description, List<String> enumValues) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", description);
        if (enumValues != null && !enumValues.isEmpty()) {
            schema.put("enum", enumValues);
        }
        return new Prop(name, schema);
    }

    public static Prop stringArray(String name, String description, int maxItems) {
        return array(name, description, Map.of("type", "string"), maxItems);
    }

    public static Prop array(String name, String description, Map<String, Object> items, int maxItems) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("description", description);
        schema.put("items", items);
        if (maxItems > 0) {
            schema.put("maxItems", maxItems);
        }
        return new Prop(name, schema);
    }

    public record Prop(String name, Map<String, Object> schema) {
    }
}
