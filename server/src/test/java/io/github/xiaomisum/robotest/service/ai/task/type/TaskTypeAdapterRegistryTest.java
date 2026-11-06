package io.github.xiaomisum.robotest.service.ai.task.type;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 类型适配器注册表单测（总册 3.6.1）：注册与查找、重复注册启动即失败（C8）。
 */
class TaskTypeAdapterRegistryTest {

    @Test
    void get_withoutRegistrations_returnsNull() {
        TaskTypeAdapterRegistry registry = new TaskTypeAdapterRegistry(List.of());
        assertNull(registry.get("test_design_generation"));
        assertNull(registry.get(null));
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void get_registeredType_returnsAdapter() {
        TaskTypeAdapter adapter = stubAdapter("case_complete");
        TaskTypeAdapterRegistry registry = new TaskTypeAdapterRegistry(List.of(adapter));

        assertSame(adapter, registry.get("case_complete"));
        assertNull(registry.get("bug_classify"));
        assertEquals(1, registry.all().size());
    }

    @Test
    void constructor_duplicateType_failsFast() {
        TaskTypeAdapter first = stubAdapter("case_complete");
        TaskTypeAdapter second = stubAdapter("case_complete");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new TaskTypeAdapterRegistry(List.of(first, second)));
        assertTrue(exception.getMessage().contains("case_complete"));
    }

    private static TaskTypeAdapter stubAdapter(String type) {
        return new TaskTypeAdapter() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public void validateInput(Map<String, Object> input) {
            }

            @Override
            public List<String> toolCodes() {
                return List.of();
            }

            @Override
            public Map<String, Object> defaultBudget() {
                return Map.of();
            }

            @Override
            public String systemPrompt() {
                return "";
            }

            @Override
            public List<ProposalDraft> buildProposals(String finalOutput, Map<String, Object> input) {
                return List.of();
            }
        };
    }
}
