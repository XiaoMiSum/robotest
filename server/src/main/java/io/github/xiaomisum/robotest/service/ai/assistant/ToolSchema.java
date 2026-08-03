package io.github.xiaomisum.robotest.service.ai.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI tools 参数 JSON Schema 构造辅助（详细设计 4.1，手写常量）
 */
public final class ToolSchema {

    private ToolSchema() {
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

    public static Prop bool(String name, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "boolean");
        schema.put("description", description);
        return new Prop(name, schema);
    }

    public record Prop(String name, Map<String, Object> schema) {
    }
}
