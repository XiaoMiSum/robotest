package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.service.ai.task.core.TaskExecutionStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * vector_reindex 类型适配器单测（总册 3.6.1 / 4.2）：scope 校验、首版预算、
 * 确定性策略分派与无提示词 / 无工具 / 无提案登记（C8）。
 */
@ExtendWith(MockitoExtension.class)
class VectorReindexTypeAdapterTest {

    @Mock
    private VectorReindexExecutionStrategy executionStrategy;
    @Mock
    private TaskExecutionStrategy agentStrategy;

    @InjectMocks
    private VectorReindexTypeAdapter adapter;

    @Test
    void type_budgetAndRegistrationDeclareDeterministicTask() {
        assertEquals(Constants.AiTaskType.VECTOR_REINDEX, adapter.type());

        Map<String, Object> budget = adapter.defaultBudget();
        assertEquals(500, budget.get("maxSteps"));
        assertEquals(3600, budget.get("maxDurationSeconds"));
        assertEquals(10000000, budget.get("maxTokens"));
        assertEquals(20, budget.get("maxCost"));

        assertTrue(adapter.toolCodes().isEmpty());
        assertTrue(adapter.systemPrompt().isBlank());
        assertTrue(adapter.buildProposals("", Map.of()).isEmpty());
        assertSame(executionStrategy, adapter.executionStrategy(agentStrategy));
    }

    @Test
    void validateInput_scopeDefaultsToFullAndRejectsIllegalScope() {
        adapter.validateInput(null);
        adapter.validateInput(Map.of());
        adapter.validateInput(Map.of("scope", Map.of()));
        adapter.validateInput(Map.of("scope", Map.of("entityTypes", List.of("bug"))));

        assertInputInvalid(() -> adapter.validateInput(Map.of("scope", "requirement")));
        assertInputInvalid(() -> adapter.validateInput(
                Map.of("scope", Map.of("entityTypes", List.of()))));
        assertInputInvalid(() -> adapter.validateInput(
                Map.of("scope", Map.of("entityTypes", List.of("foo")))));
    }

    private static void assertInputInvalid(Runnable invocation) {
        ServiceException error = assertThrows(ServiceException.class, invocation::run);
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), error.getCode());
    }
}
