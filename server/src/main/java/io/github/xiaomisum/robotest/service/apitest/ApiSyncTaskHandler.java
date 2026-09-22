package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.task.DispatchableTask;
import io.github.xiaomisum.robotest.framework.task.TaskDispatchContext;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 接口同步任务执行器（从 ScheduledTaskRunner 提取）。
 * 拉取 openapi_url 指定的 OpenAPI/Swagger JSON 文件增量同步接口。
 */
@Slf4j
@Component
public class ApiSyncTaskHandler implements DispatchableTask {

    public static final String TYPE = "api_sync";

    @Resource
    private ApiInterfaceImportService apiInterfaceImportService;
    @Resource
    private ApiScheduledTaskMapper taskMapper;
    @Resource
    private ApiScheduledTaskExecutionMapper executionMapper;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, Object> execute(TaskDispatchContext context) {
        // 由 ScheduledTaskRunner 委托调用，实际逻辑通过 executeSync 方法暴露
        return null;
    }

    /**
     * 同步执行接口同步任务：拉取 OpenAPI 文档 → 增量导入 → 留痕。
     * 业务异常留痕后向上抛出供前端提示。
     */
    public ImportOutcome executeSync(ApiScheduledTask task, UUID executorUserId, String triggerType,
                                     LocalDateTime triggeredAt) {
        long startedAt = System.currentTimeMillis();
        updateTaskExecution(task.getId(), "running");
        ApiImportResultRespDTO result = apiInterfaceImportService.importUrl(
                task.getProjectId(), executorUserId, task.getOpenapiUrl(), null);
        insertRecord(task.getId(), task.getProjectId(), triggerType, "success", null,
                null, result.getImportHistoryId(), triggeredAt,
                (int) (System.currentTimeMillis() - startedAt));
        updateTaskLastExecution(task.getId(), "success");
        return new ImportOutcome(result.getImportHistoryId(), "success");
    }

    public record ImportOutcome(UUID importRecordId, String status) {
    }

    private void updateTaskExecution(UUID taskId, String status) {
        ApiScheduledTask update = new ApiScheduledTask();
        update.setId(taskId);
        update.setLastExecutionStatus(status);
        update.setLastExecutionAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    void updateTaskLastExecution(UUID taskId, String status) {
        updateTaskExecution(taskId, status);
    }

    private void insertRecord(UUID taskId, UUID projectId, String triggerType, String status,
            String errorMessage, UUID reportId, UUID importRecordId, LocalDateTime triggeredAt, Integer durationMs) {
        ApiScheduledTaskExecution record = new ApiScheduledTaskExecution();
        record.setId(UUID.randomUUID());
        record.setTaskId(taskId);
        record.setProjectId(projectId);
        record.setTriggerType(triggerType);
        record.setStatus(status);
        record.setErrorMessage(errorMessage);
        record.setReportId(reportId);
        record.setImportRecordId(importRecordId);
        record.setTriggeredAt(triggeredAt);
        record.setDurationMs(durationMs);
        executionMapper.insert(record);
    }
}
