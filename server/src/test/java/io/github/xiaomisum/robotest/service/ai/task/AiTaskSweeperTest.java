package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.core.ExecutionLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 执行心跳超时清扫（总册 4.2）：cutoff = now - task_timeout_seconds，
 * 超时执行置 aborted 并同步任务态，已终态执行跳过。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskSweeperTest {

    @Mock
    private AiTaskExecutionMapper executionMapper;
    @Mock
    private AiSettingsReader settingsReader;
    @Mock
    private ExecutionLifecycle executionLifecycle;

    @InjectMocks
    private AiTaskSweeper sweeper;

    @Test
    void sweep_staleExecution_abortExecutionAndSyncTask() {
        when(settingsReader.settings()).thenReturn(new AiSettingsReader.AiSettings(true, null, 600, 2));
        AiTaskExecution stale = new AiTaskExecution();
        stale.setId(UUID.randomUUID());
        stale.setTaskId(UUID.randomUUID());
        stale.setSteps(3);
        stale.setTokensIn(10);
        stale.setTokensOut(20);
        stale.setCreatedAt(LocalDateTime.now().minusMinutes(11));
        when(executionMapper.selectStale(any())).thenReturn(List.of(stale));
        when(executionLifecycle.terminateIfHeartbeatStale(eq(stale.getTaskId()), eq(stale.getId()), any(),
                eq(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code()), eq("任务执行超时"))).thenReturn(true);
        LocalDateTime beforeSweep = LocalDateTime.now();

        sweeper.sweepTimeoutExecutions();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(executionMapper).selectStale(cutoffCaptor.capture());
        LocalDateTime cutoff = cutoffCaptor.getValue();
        // 截止时间 = 当前时间 - task_timeout_seconds（600s）
        assertTrue(cutoff.isBefore(beforeSweep.minusSeconds(599)));
        assertTrue(cutoff.isAfter(beforeSweep.minusSeconds(601)));

        verify(executionLifecycle).terminateIfHeartbeatStale(eq(stale.getTaskId()), eq(stale.getId()),
                eq(cutoff), eq(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code()), eq("任务执行超时"));
    }

    @Test
    void sweep_executionAlreadyFinished_skipsTaskSync() {
        when(settingsReader.settings()).thenReturn(new AiSettingsReader.AiSettings(true, null, 600, 2));
        AiTaskExecution stale = new AiTaskExecution();
        stale.setId(UUID.randomUUID());
        stale.setTaskId(UUID.randomUUID());
        stale.setCreatedAt(LocalDateTime.now().minusMinutes(11));
        when(executionMapper.selectStale(any())).thenReturn(List.of(stale));
        // 条件写失败 = 执行刚被用户终止 / 已到终态：不再同步任务态
        when(executionLifecycle.terminateIfHeartbeatStale(eq(stale.getTaskId()), eq(stale.getId()), any(),
                anyInt(), anyString())).thenReturn(false);

        sweeper.sweepTimeoutExecutions();

        verify(executionLifecycle).terminateIfHeartbeatStale(eq(stale.getTaskId()), eq(stale.getId()), any(),
                eq(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code()), eq("任务执行超时"));
    }
}
