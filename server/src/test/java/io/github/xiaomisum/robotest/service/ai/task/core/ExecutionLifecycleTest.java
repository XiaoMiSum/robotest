package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskProposal;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskProposalMapper;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionLifecycleTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private AiTaskExecutionMapper executionMapper;
    @Mock
    private AiTaskProposalMapper proposalMapper;
    @Mock
    private TraceRecorder traceRecorder;

    @InjectMocks
    private ExecutionLifecycle lifecycle;

    private AiTask task;
    private AiTaskExecution execution;

    @BeforeEach
    void setUp() {
        task = new AiTask();
        task.setId(TASK_ID);
        task.setStatus(Constants.AiTaskFlowStatus.RUNNING);
        execution = new AiTaskExecution();
        execution.setId(EXECUTION_ID);
        execution.setTaskId(TASK_ID);
        execution.setStatus(Constants.AiTaskExecutionStatus.RUNNING);
        execution.setSteps(4);
        execution.setTokensIn(12);
        execution.setTokensOut(8);
        execution.setCost(new BigDecimal("0.25"));
        execution.setCreatedAt(LocalDateTime.now().minusSeconds(3));
        execution.setHeartbeatAt(LocalDateTime.now().minusSeconds(2));
    }

    @Test
    void succeed_proposalInsertFailure_propagatesForTransactionRollback() {
        lockCurrent(execution);
        when(executionMapper.succeed(eq(EXECUTION_ID), anyInt(), anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(true);
        when(taskMapper.markResultMeta(TASK_ID, Map.of("ok", true))).thenReturn(true);
        when(taskMapper.accumulateTokens(TASK_ID, 12, 8)).thenReturn(true);
        when(proposalMapper.insert(any(AiTaskProposal.class)))
                .thenThrow(new DuplicateKeyException("proposal key"));

        assertThrows(DuplicateKeyException.class, () -> lifecycle.succeed(TASK_ID, EXECUTION_ID,
                List.of(new ProposalDraft("p1", "kind", null, "title", Map.of())), Map.of("ok", true),
                new ExecutionLifecycle.Usage(4, 12, 8, new BigDecimal("0.25"), 3000)));

        verify(taskMapper, never()).markFlowStatus(any(), anyString());
    }

    @Test
    void succeed_casLoser_writesNoProjectionProposalOrTerminalTrace() {
        lockCurrent(execution);
        when(executionMapper.succeed(eq(EXECUTION_ID), anyInt(), anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(false);

        assertFalse(lifecycle.succeed(TASK_ID, EXECUTION_ID,
                List.of(new ProposalDraft("p1", "kind", null, "title", Map.of())), null,
                new ExecutionLifecycle.Usage(4, 12, 8, new BigDecimal("0.25"), 3000)));

        verify(taskMapper, never()).accumulateTokens(any(), anyInt(), anyInt());
        verify(proposalMapper, never()).insert(any(AiTaskProposal.class));
        verify(traceRecorder, never()).record(any(), anyString(), anyString(), any());
    }

    @Test
    void terminate_alreadyTerminated_isIdempotentLoser() {
        execution.setStatus(Constants.AiTaskExecutionStatus.ABORTED);
        lockCurrent(execution);

        assertFalse(lifecycle.terminate(TASK_ID, EXECUTION_ID, 1000018117, "failed"));

        verify(executionMapper, never()).abort(any(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                any(), anyInt());
        verify(taskMapper, never()).accumulateTokens(any(), anyInt(), anyInt());
        verify(traceRecorder, never()).record(any(), anyString(), anyString(), any());
    }

    @Test
    void terminate_oldExecutionAfterRerun_cannotWriteTaskProjection() {
        AiTaskExecution latest = new AiTaskExecution();
        latest.setId(UUID.randomUUID());
        latest.setTaskId(TASK_ID);
        latest.setStatus(Constants.AiTaskExecutionStatus.RUNNING);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);
        when(executionMapper.selectLatestByTaskIdForUpdate(TASK_ID)).thenReturn(latest);

        assertFalse(lifecycle.terminate(TASK_ID, EXECUTION_ID, 1000018117, "late"));

        verify(executionMapper, never()).abort(any(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                any(), anyInt());
        verify(taskMapper, never()).markAborted(any(), anyInt(), anyString());
    }

    @Test
    void terminateIfHeartbeatStale_refreshedAfterScan_isNotAborted() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(30);
        execution.setHeartbeatAt(cutoff.plusSeconds(1));
        lockCurrent(execution);

        assertFalse(lifecycle.terminateIfHeartbeatStale(TASK_ID, EXECUTION_ID, cutoff,
                1000018117, "timeout"));

        verify(executionMapper, never()).abort(any(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                any(), anyInt());
    }

    @Test
    void terminate_externalAbort_usesLockedLatestUsageOnce() {
        lockCurrent(execution);
        when(executionMapper.abort(eq(EXECUTION_ID), eq(1000018109), eq("abort"),
                eq(4), eq(12), eq(8), eq(new BigDecimal("0.25")), anyInt())).thenReturn(true);
        when(taskMapper.markAborted(TASK_ID, 1000018109, "abort")).thenReturn(true);
        when(taskMapper.accumulateTokens(TASK_ID, 12, 8)).thenReturn(true);

        assertTrue(lifecycle.terminate(TASK_ID, EXECUTION_ID, 1000018109, "abort"));

        verify(taskMapper).accumulateTokens(TASK_ID, 12, 8);
        verify(traceRecorder).record(EXECUTION_ID, Constants.AiTraceEventType.STATUS_CHANGE,
                "执行终止: abort", Map.of("terminateCode", 1000018109));
    }

    private void lockCurrent(AiTaskExecution lockedExecution) {
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);
        when(executionMapper.selectLatestByTaskIdForUpdate(TASK_ID)).thenReturn(lockedExecution);
    }
}
