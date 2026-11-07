package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionControlTest {

    @Mock
    private BudgetGuard budgetGuard;
    @Mock
    private AiTaskExecutionMapper executionMapper;
    @Mock
    private TraceRecorder traceRecorder;
    @Mock
    private ExecutionLifecycle executionLifecycle;

    private AiTaskExecution execution;
    private ExecutionControl control;

    @BeforeEach
    void setUp() {
        execution = new AiTaskExecution();
        execution.setId(UUID.randomUUID());
        execution.setTaskId(UUID.randomUUID());
        execution.setSteps(0);
        execution.setTokensIn(0);
        execution.setTokensOut(0);
        execution.setCost(BigDecimal.ZERO);
        execution.setBudget(Map.of("maxSteps", 10, "maxTokens", 100));
        control = new ExecutionControl(execution, budgetGuard, executionMapper,
                traceRecorder, executionLifecycle);
    }

    @Test
    void beforeCall_oldOrTerminatedExecution_stopsBeforeExternalCall() {
        when(executionLifecycle.isCurrentActive(execution.getTaskId(), execution.getId())).thenReturn(false);

        assertThrows(ExecutionControl.InactiveExecutionException.class, control::beforeCall);

        verify(budgetGuard, never()).exceeded(any(), any());
        verify(executionMapper, never()).heartbeat(any(), any(Integer.class), any(Integer.class),
                any(Integer.class), any());
    }

    @Test
    void afterModelCall_countsUsageOnceAndHeartbeats() {
        activeWithinBudget();
        when(executionMapper.heartbeat(execution.getId(), 1, 7, 3, new BigDecimal("0.25")))
                .thenReturn(true);

        control.afterModelCall(7, 3, new BigDecimal("0.25"));

        ExecutionLifecycle.Usage usage = control.usage();
        assertEquals(1, usage.steps());
        assertEquals(7, usage.tokensIn());
        assertEquals(3, usage.tokensOut());
        assertEquals(new BigDecimal("0.25"), usage.cost());
        verify(executionMapper).heartbeat(execution.getId(), 1, 7, 3, new BigDecimal("0.25"));
    }

    @Test
    void afterModelCall_lastHopExceedsBudget_cannotProceedToSuccess() {
        when(executionMapper.heartbeat(execution.getId(), 1, 7, 3, BigDecimal.ZERO)).thenReturn(true);
        when(executionLifecycle.isCurrentActive(execution.getTaskId(), execution.getId())).thenReturn(true);
        when(budgetGuard.exceeded(eq(execution.getBudget()), any(BudgetGuard.Consumed.class))).thenReturn(true);

        assertThrows(ExecutionControl.BudgetExceededException.class,
                () -> control.afterModelCall(7, 3, BigDecimal.ZERO));

        assertEquals(10, control.usage().tokensIn() + control.usage().tokensOut());
    }

    @Test
    void beforeToolCall_rechecksBudgetAfterIncrementBeforeToolRuns() {
        when(executionLifecycle.isCurrentActive(execution.getTaskId(), execution.getId())).thenReturn(true);
        when(executionMapper.heartbeat(execution.getId(), 1, 0, 0, BigDecimal.ZERO)).thenReturn(true);
        when(budgetGuard.exceeded(eq(execution.getBudget()), any(BudgetGuard.Consumed.class)))
                .thenReturn(false, true);

        assertThrows(ExecutionControl.BudgetExceededException.class, control::beforeToolCall);

        verify(executionMapper).heartbeat(execution.getId(), 1, 0, 0, BigDecimal.ZERO);
    }

    private void activeWithinBudget() {
        when(executionLifecycle.isCurrentActive(execution.getTaskId(), execution.getId())).thenReturn(true);
        when(budgetGuard.exceeded(eq(execution.getBudget()), any(BudgetGuard.Consumed.class))).thenReturn(false);
    }
}
