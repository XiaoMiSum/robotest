package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskExecutionDispatcherTest {

    private static final String TYPE = "test_design_generation";

    @Mock
    private TaskTypeAdapterRegistry adapterRegistry;
    @Mock
    private AgentExecutionRunner agentExecutionRunner;
    @Mock
    private BudgetGuard budgetGuard;
    @Mock
    private AiTaskExecutionMapper executionMapper;
    @Mock
    private TraceRecorder traceRecorder;
    @Mock
    private ExecutionLifecycle executionLifecycle;

    @InjectMocks
    private TaskExecutionDispatcher dispatcher;

    private AiTask task;
    private AiTaskExecution execution;
    private TaskExecutionDispatcher.DispatchContext context;

    @BeforeEach
    void setUp() {
        task = new AiTask();
        task.setId(UUID.randomUUID());
        task.setType(TYPE);
        execution = new AiTaskExecution();
        execution.setId(UUID.randomUUID());
        execution.setTaskId(task.getId());
        execution.setBudget(Map.of("maxSteps", 10));
        execution.setCost(BigDecimal.ZERO);
        context = new TaskExecutionDispatcher.DispatchContext(task, execution,
                new AiModelConfig(), UUID.randomUUID(), UUID.randomUUID(), null);
        lenient().when(executionLifecycle.isCurrentActive(task.getId(), execution.getId())).thenReturn(true);
        lenient().when(budgetGuard.exceeded(eq(execution.getBudget()), any())).thenReturn(false);
    }

    @Test
    void dispatch_defaultAdapterStrategy_usesAgentAndSucceedsOnce() {
        TaskTypeAdapter adapter = new StubAdapter(null);
        ProposalDraft proposal = new ProposalDraft("p1", "kind", null, "title", Map.of());
        when(adapterRegistry.get(TYPE)).thenReturn(adapter);
        when(agentExecutionRunner.execute(any(), any()))
                .thenReturn(new TaskExecutionStrategy.ExecutionResult(List.of(proposal), Map.of("ok", true)));

        dispatcher.dispatch(context);

        verify(agentExecutionRunner).execute(any(), any());
        verify(executionLifecycle).succeed(eq(task.getId()), eq(execution.getId()),
                eq(List.of(proposal)), eq(Map.of("ok", true)), any());
        verify(executionLifecycle, never()).terminate(any(), any(), anyInt(), anyString(), any());
    }

    @Test
    void dispatch_adapterRegisteredNonAgentStrategy_usesItWithoutModelStrategy() {
        TaskExecutionStrategy deterministic = org.mockito.Mockito.mock(TaskExecutionStrategy.class);
        when(adapterRegistry.get(TYPE)).thenReturn(new StubAdapter(deterministic));
        when(deterministic.execute(any(), any()))
                .thenReturn(new TaskExecutionStrategy.ExecutionResult(List.of(), Map.of("rebuilt", true)));

        dispatcher.dispatch(context);

        verify(deterministic).execute(any(), any());
        verify(agentExecutionRunner, never()).execute(any(), any());
        verify(executionLifecycle).succeed(eq(task.getId()), eq(execution.getId()),
                anyList(), eq(Map.of("rebuilt", true)), any());
    }

    @Test
    void dispatch_strategyFailure_terminatesThroughSharedLifecycle() {
        when(adapterRegistry.get(TYPE)).thenReturn(new StubAdapter(null));
        when(agentExecutionRunner.execute(any(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED));

        dispatcher.dispatch(context);

        verify(executionLifecycle).terminate(eq(task.getId()), eq(execution.getId()),
                eq(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code()), anyString(), any());
        verify(executionLifecycle, never()).succeed(any(), any(), anyList(), any(), any());
    }

    @Test
    void dispatch_lastModelHopExceedsBudget_terminatesWithoutSuccessAndKeepsSingleUsage() {
        TaskExecutionStrategy measured = (strategyContext, control) -> {
            control.afterModelCall(7, 3, new BigDecimal("0.25"));
            return new TaskExecutionStrategy.ExecutionResult(List.of(), null);
        };
        when(adapterRegistry.get(TYPE)).thenReturn(new StubAdapter(measured));
        when(executionMapper.heartbeat(execution.getId(), 1, 7, 3, new BigDecimal("0.25")))
                .thenReturn(true);
        when(budgetGuard.exceeded(eq(execution.getBudget()), any())).thenReturn(false, true);

        dispatcher.dispatch(context);

        verify(executionLifecycle, never()).succeed(any(), any(), anyList(), any(), any());
        verify(executionLifecycle).terminate(eq(task.getId()), eq(execution.getId()),
                eq(ErrorCodeConstants.AI_EXECUTION_FINISHED.code()), eq("预算耗尽"),
                argThat(usage -> usage.steps() == 1 && usage.tokensIn() == 7
                        && usage.tokensOut() == 3 && usage.cost().compareTo(new BigDecimal("0.25")) == 0));
    }

    @Test
    void dispatch_oldOrTerminatedExecution_doesNotStartStrategyOrTerminateAgain() {
        when(adapterRegistry.get(TYPE)).thenReturn(new StubAdapter(null));
        when(executionLifecycle.isCurrentActive(task.getId(), execution.getId())).thenReturn(false);

        dispatcher.dispatch(context);

        verify(agentExecutionRunner, never()).execute(any(), any());
        verify(executionLifecycle, never()).succeed(any(), any(), anyList(), any(), any());
        verify(executionLifecycle, never()).terminate(any(), any(), anyInt(), anyString(), any());
    }

    @Test
    void dispatch_unknownType_terminatesWithoutStartingStrategy() {
        when(adapterRegistry.get(TYPE)).thenReturn(null);

        dispatcher.dispatch(context);

        verify(agentExecutionRunner, never()).execute(any(), any());
        verify(executionLifecycle).terminate(eq(task.getId()), eq(execution.getId()),
                eq(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED.code()), anyString(), any());
    }

    private static final class StubAdapter implements TaskTypeAdapter {
        private final TaskExecutionStrategy strategy;

        private StubAdapter(TaskExecutionStrategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public TaskExecutionStrategy executionStrategy(TaskExecutionStrategy defaultAgentStrategy) {
            return strategy == null ? TaskTypeAdapter.super.executionStrategy(defaultAgentStrategy) : strategy;
        }

        @Override
        public String type() {
            return TYPE;
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
    }
}
