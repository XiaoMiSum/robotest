package io.github.xiaomisum.robotest.service.ai;


import io.github.xiaomisum.robotest.service.ai.gateway.AiSettingDefinitions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSettingDefinitionsTest {

    private final AiSettingDefinitions definitions = new AiSettingDefinitions();

    @Test
    void schema_isGroupedAndCoversKeys() {
        var schema = definitions.schema();
        assertFalse(schema.isEmpty());
        long totalItems = schema.stream().mapToLong(g -> g.getItems().size()).sum();
        // 与 defaults 键数一致
        assertEquals(definitions.defaults().size(), totalItems);
    }

    @Test
    void validate_acceptsInRangeValues() {
        definitions.validate(Map.of(
                "rateLimit.generation", 30,
                "dedup.similarityThreshold", 0.8,
                "planOrder.weights", Map.of("w1", 0.2, "w2", 0.3, "w3", 0.5)));
    }

    @Test
    void validate_rejectsUnknownKey() {
        assertThrows(ServiceException.class, () -> definitions.validate(Map.of("unknown.key", 1)));
    }

    @Test
    void validate_rejectsOutOfRange() {
        // dedup.topK 上限 50
        assertThrows(ServiceException.class, () -> definitions.validate(Map.of("dedup.topK", 999)));
    }

    @Test
    void validate_rejectsWeightsSumNotOne() {
        assertThrows(ServiceException.class, () -> definitions.validate(
                Map.of("planOrder.weights", Map.of("w1", 0.5, "w2", 0.5, "w3", 0.5))));
    }

    @Test
    void validate_rejectsUnknownWriteTool() {
        assertThrows(ServiceException.class, () -> definitions.validate(
                Map.of("assistantWriteToolWhitelist", List.of("drop_database"))));
    }

    @Test
    void stripDefaults_keepsOnlyOverrides() {
        Map<String, Object> stripped = definitions.stripDefaults(Map.of(
                "rateLimit.generation", 20,   // 等于默认 → 丢弃
                "rateLimit.suggestion", 99));  // 偏离默认 → 保留
        assertFalse(stripped.containsKey("rateLimit.generation"));
        assertTrue(stripped.containsKey("rateLimit.suggestion"));
        assertEquals(1, stripped.size());
    }

    @Test
    void stripDefaults_treatsNumericEquivalenceAsDefault() {
        // 20.0 与默认 20 数值相等 → 视为默认丢弃
        Map<String, Object> stripped = definitions.stripDefaults(Map.of("rateLimit.generation", 20.0));
        assertFalse(stripped.containsKey("rateLimit.generation"));
    }
}
