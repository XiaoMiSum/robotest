package io.github.xiaomisum.robotest.service.ai.task;


import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTaskServiceImplTest {

    @Mock
    private AiAnalysisTaskMapper taskMapper;
    @Mock
    private ThreadPoolTaskExecutor aiTaskExecutor;

    @InjectMocks
    private AiTaskServiceImpl taskService;

    private UUID taskId;
    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        taskId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        userId = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        projectId = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    }

    private AiAnalysisTask task(String status, UUID createdBy) {
        AiAnalysisTask task = new AiAnalysisTask();
        task.setId(taskId);
        task.setType(Constants.AiTaskType.REVIEW_CHECK);
        task.setStatus(status);
        task.setProjectId(projectId);
        task.setCreatedBy(createdBy);
        return task;
    }

    @Test
    void getTask_projectMismatchThrows() {
        AiAnalysisTask task = task(Constants.AiTaskStatus.RUNNING, userId);
        task.setProjectId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
        when(taskMapper.selectById(taskId)).thenReturn(task);

        assertThrows(ServiceException.class, () -> taskService.getTask(taskId, projectId));
    }

    @Test
    void getTask_success() {
        when(taskMapper.selectById(taskId)).thenReturn(task(Constants.AiTaskStatus.RUNNING, userId));
        AiTaskRespDTO dto = taskService.getTask(taskId, projectId);
        assertEquals(Constants.AiTaskStatus.RUNNING, dto.getStatus());
    }

    @Test
    void getLatestTaskByTypeAndTarget_noRecord_returnsNull() {
        when(taskMapper.findLatestByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, taskId)).thenReturn(null);
        assertNull(taskService.getLatestTaskByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, taskId, projectId));
    }

    @Test
    void getLatestTaskByTypeAndTarget_projectMismatch_returnsNull() {
        AiAnalysisTask task = task(Constants.AiTaskStatus.RUNNING, userId);
        task.setProjectId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
        when(taskMapper.findLatestByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, taskId)).thenReturn(task);
        assertNull(taskService.getLatestTaskByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, taskId, projectId));
    }

    @Test
    void getLatestTaskByTypeAndTarget_success() {
        when(taskMapper.findLatestByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, taskId))
                .thenReturn(task(Constants.AiTaskStatus.SUCCESS, userId));
        AiTaskRespDTO dto = taskService.getLatestTaskByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, taskId, projectId);
        assertEquals(Constants.AiTaskStatus.SUCCESS, dto.getStatus());
    }

    @Test
    void cancelTask_notInProgressThrows() {
        when(taskMapper.selectById(taskId)).thenReturn(task(Constants.AiTaskStatus.SUCCESS, userId));
        // 仅 pending/running 可取消，success 返回 6006
        assertThrows(ServiceException.class, () -> taskService.cancelTask(taskId, userId));
    }

    @Test
    void cancelTask_notInitiatorThrows() {
        when(taskMapper.selectById(taskId)).thenReturn(task(Constants.AiTaskStatus.RUNNING, userId));
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000e9");
        assertThrows(ServiceException.class, () -> taskService.cancelTask(taskId, other));
    }

    @Test
    void cancelTask_success() {
        when(taskMapper.selectById(taskId)).thenReturn(task(Constants.AiTaskStatus.RUNNING, userId));
        when(taskMapper.markCancelledById(taskId)).thenReturn(1);
        taskService.cancelTask(taskId, userId);
        verify(taskMapper).markCancelledById(taskId);
    }

    @Test
    void retryTask_notFailedThrows() {
        when(taskMapper.selectById(taskId)).thenReturn(task(Constants.AiTaskStatus.RUNNING, userId));
        // 仅 failed 可重试
        assertThrows(ServiceException.class, () -> taskService.retryTask(taskId, userId));
    }

    @Test
    void retryTask_duplicateInProgressThrows() {
        when(taskMapper.selectById(taskId)).thenReturn(task(Constants.AiTaskStatus.FAILED, userId));
        when(taskMapper.findInProgressByTypeAndTarget(any(), any()))
                .thenReturn(List.of(new AiAnalysisTask()));
        // 同 type+target 已有进行中任务返回 6005
        assertThrows(ServiceException.class, () -> taskService.retryTask(taskId, userId));
        verify(aiTaskExecutor, never()).execute(any());
    }

    @Test
    void retryTask_success() {
        when(taskMapper.selectById(taskId)).thenReturn(task(Constants.AiTaskStatus.FAILED, userId));
        when(taskMapper.findInProgressByTypeAndTarget(any(), any())).thenReturn(List.of());
        taskService.retryTask(taskId, userId);
        verify(taskMapper).resetToPending(taskId);
        verify(aiTaskExecutor, times(1)).execute(any());
    }

    @Test
    void retryRebuildTask_notFailedOrCancelledThrows() {
        AiAnalysisTask running = new AiAnalysisTask();
        running.setStatus(Constants.AiTaskStatus.RUNNING);
        when(taskMapper.findLatestByType(Constants.AiTaskType.EMBEDDING_REBUILD)).thenReturn(running);
        assertThrows(ServiceException.class, () -> taskService.retryRebuildTask());
    }

    @Test
    void retryRebuildTask_cancelledCanRetry() {
        AiAnalysisTask cancelled = new AiAnalysisTask();
        cancelled.setId(taskId);
        cancelled.setStatus(Constants.AiTaskStatus.CANCELLED);
        when(taskMapper.findLatestByType(Constants.AiTaskType.EMBEDDING_REBUILD)).thenReturn(cancelled);
        taskService.retryRebuildTask();
        verify(taskMapper).resetToPending(taskId);
        verify(aiTaskExecutor, times(1)).execute(any());
    }

    @Test
    void executeTask_handlerMissingMarksFailed() {
        // 无 handler 注册时，抢占成功后置 failed 保证状态机闭环
        when(taskMapper.claimForExecution(eq(taskId), any())).thenReturn(1);
        when(taskMapper.selectById(taskId)).thenReturn(task(Constants.AiTaskStatus.RUNNING, userId));

        taskService.executeTask(taskId);
        verify(taskMapper).markFailedIfRunning(eq(taskId), any());
    }

    @Test
    void executeTask_claimFailedSkips() {
        // 抢占影响行数为 0（被其他实例消费）直接返回，不再查询任务
        when(taskMapper.claimForExecution(eq(taskId), any())).thenReturn(0);
        taskService.executeTask(taskId);
        verify(taskMapper, never()).selectById(eq(taskId));
    }
}
