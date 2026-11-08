package io.github.xiaomisum.robotest.service.ai.task.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolArgumentValidatorTest {

    private final AgentToolArgumentValidator validator = new AgentToolArgumentValidator();

    @Test
    void validate_requiredTypeEnumAndMaxItems() {
        Map<String, Object> schema = Map.of(
                "required", List.of("scope"),
                "properties", Map.of(
                        "scope", Map.of("type", "string", "enum", List.of("active")),
                        "ids", Map.of("type", "array", "maxItems", 2)));

        assertTrue(validator.validate(schema, Map.of()).contains("缺少"));
        assertTrue(validator.validate(schema, Map.of("scope", 1)).contains("类型"));
        assertTrue(validator.validate(schema, Map.of("scope", "all")).contains("枚举"));
        assertTrue(validator.validate(schema, Map.of("scope", "active", "ids", List.of(1, 2, 3)))
                .contains("超限"));
        assertNull(validator.validate(schema, Map.of("scope", "active", "ids", List.of(1, 2))));
    }
}
