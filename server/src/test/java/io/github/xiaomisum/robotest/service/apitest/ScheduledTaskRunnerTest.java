package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSwaggerUrl;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSwaggerUrlMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 定时任务执行留痕：跳过/失败记录与异常边界（定时任务详细设计 4.1、4.3） */
@ExtendWith(MockitoExtension.class)
class ScheduledTaskRunnerTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID CONFIG_ID = UUID.randomUUID();

    @Mock
    private SceneExecutionService sceneExecutionService;
    @Mock
    private ApiInterfaceService apiInterfaceService;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ApiScheduledTaskMapper taskMapper;
    @Mock
    private ApiScheduledTaskExecutionMapper executionMapper;
    @Mock
    private ApiSwaggerUrlMapper swaggerUrlMapper;

    @InjectMocks
    private ScheduledTaskRunner runner;

    @Test
    void writeSkippedRecordsSkippedStatusWithoutTouchingLastExecution() {
        runner.writeSkipped(importTask(), "scheduled");

        ArgumentCaptor<ApiScheduledTaskExecution> captor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(captor.capture());
        assertEquals("skipped", captor.getValue().getStatus());
        assertEquals("scheduled", captor.getValue().getTriggerType());
        // 跳过不代表执行，最近状态不回写
        verify(taskMapper, never()).updateById(any(ApiScheduledTask.class));
    }

    @Test
    void runImportRethrowRecordsFailureThenRethrows() {
        ApiScheduledTask task = importTask();
        when(swaggerUrlMapper.selectById(CONFIG_ID)).thenReturn(config());
        when(apiInterfaceService.importUrl(eq(PROJECT_ID), any(UUID.class), any(), any()))
                .thenThrow(new ServiceException(1000017012, "HTTP 500"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> runner.runImportRethrow(task, UUID.randomUUID(), "manual"));
        assertEquals(1000017012, ex.getCode().intValue());

        // markRunning(running) + recordFailure(failed) 两次回写
        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper, times(2)).updateById(taskCaptor.capture());
        assertEquals("running", taskCaptor.getAllValues().get(0).getLastExecutionStatus());
        assertEquals("failed", taskCaptor.getAllValues().get(1).getLastExecutionStatus());

        ArgumentCaptor<ApiScheduledTaskExecution> captor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("HTTP 500", captor.getValue().getErrorMessage());
    }

    @Test
    void runTaskSwallowsFailureAndMarksLastExecution() {
        // 绑定配置缺失 → 业务异常被吞掉，仅留痕失败并回写最近状态
        when(swaggerUrlMapper.selectById(CONFIG_ID)).thenReturn(null);

        assertDoesNotThrow(() -> runner.runTask(importTask(), "scheduled"));

        ArgumentCaptor<ApiScheduledTaskExecution> captor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertTrue(captor.getValue().getErrorMessage().contains("Swagger URL 不存在"));

        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertEquals("failed", taskCaptor.getValue().getLastExecutionStatus());
        // 系统身份执行失败不再自动停用任务（4.3 变更）：部分更新载体不携带 enabled
        assertNull(taskCaptor.getValue().getEnabled());
    }

    private ApiScheduledTask importTask() {
        ApiScheduledTask task = new ApiScheduledTask();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setTaskType("import_swagger");
        task.setBoundObjectId(CONFIG_ID);
        task.setEnabled(true);
        return task;
    }

    private ApiSwaggerUrl config() {
        ApiSwaggerUrl config = new ApiSwaggerUrl();
        config.setId(CONFIG_ID);
        config.setProjectId(PROJECT_ID);
        return config;
    }
}
