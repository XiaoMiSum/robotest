package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStatusRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSwaggerUrl;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSwaggerUrlMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SWAGGER_URL_NOT_FOUND;

/**
 * 定时/手动触发的统一执行路径（定时任务详细设计 4.1）：
 * 执行（场景走执行引擎、导入复用 importUrl）→ 执行记录留痕 → 回写任务最近状态。
 * 定时触发以系统身份执行（不校验创建者成员关系），手动触发以当前登录用户身份执行。
 */
@Component
public class ScheduledTaskRunner {

    private static final Set<String> TERMINAL_STATUSES = Set.of("success", "failed", "error", "cancelled", "timeout");

    @Resource
    private SceneExecutionService sceneExecutionService;
    @Resource
    private ApiInterfaceService apiInterfaceService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ApiScheduledTaskMapper taskMapper;
    @Resource
    private ApiScheduledTaskExecutionMapper executionMapper;
    @Resource
    private ApiSwaggerUrlMapper swaggerUrlMapper;

    /** 手动触发场景执行后的完成监听与调度线程隔离，避免长轮询占满调度池 */
    private final ExecutorService trackerPool = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "api-test-task-tracker");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${robotest.api-test.scheduler.execution-timeout-minutes:30}")
    private int executionTimeoutMinutes;

    @Value("${robotest.api-test.scheduler.poll-interval-ms:5000}")
    private long pollIntervalMs;

    /** 场景任务启动结果：场景执行记录已落库，executionId 供手动响应与完成追踪 */
    public record SceneLaunch(UUID workspaceId, String sceneExecutionId) {
    }

    public record ImportOutcome(UUID importRecordId, String status) {
    }

    /** 调度线程调用：同步跑完整链路（场景阻塞等待至终态）；系统身份执行，不校验成员关系 */
    public void runTask(ApiScheduledTask task, String triggerType) {
        LocalDateTime triggeredAt = LocalDateTime.now();
        try {
            if ("scene_execute".equals(task.getTaskType())) {
                SceneLaunch launch = launchScene(task, ProjectAccessGuard.SYSTEM_OPERATOR_ID);
                awaitAndFinalizeScene(task, launch.workspaceId(), ProjectAccessGuard.SYSTEM_OPERATOR_ID,
                        launch.sceneExecutionId(), triggerType, triggeredAt);
            } else {
                runImport(task, ProjectAccessGuard.SYSTEM_OPERATOR_ID, triggerType, triggeredAt);
            }
        } catch (Exception e) {
            recordFailure(task, triggerType, triggeredAt, e);
        }
    }

    /** 手动触发场景任务：快速入队返回场景执行 ID，等待阶段交给 tracker 线程 */
    public SceneLaunch launchSceneAsyncFinalize(ApiScheduledTask task, UUID executorUserId, String triggerType) {
        LocalDateTime triggeredAt = LocalDateTime.now();
        SceneLaunch launch = launchScene(task, executorUserId);
        trackerPool.submit(() -> {
            try {
                awaitAndFinalizeScene(task, launch.workspaceId(), executorUserId,
                        launch.sceneExecutionId(), triggerType, triggeredAt);
            } catch (Exception e) {
                recordFailure(task, triggerType, triggeredAt, e);
            }
        });
        return launch;
    }

    /** 手动触发导入任务：同步执行并留痕，业务异常留痕后向上抛出供前端提示 */
    public ImportOutcome runImportRethrow(ApiScheduledTask task, UUID executorUserId, String triggerType) {
        LocalDateTime triggeredAt = LocalDateTime.now();
        try {
            return runImport(task, executorUserId, triggerType, triggeredAt);
        } catch (ServiceException e) {
            recordFailure(task, triggerType, triggeredAt, e);
            throw e;
        }
    }

    /** 上一次触发未结束时写入 skipped 记录，不重复触发（定时任务详细设计 4.1 第 4 步） */
    public void writeSkipped(ApiScheduledTask task, String triggerType) {
        insertRecord(task.getId(), task.getProjectId(), triggerType, "skipped",
                "上一次执行尚未结束，本次触发跳过", null, null, LocalDateTime.now(), 0);
    }

    private SceneLaunch launchSceneRaw(ApiScheduledTask task, UUID executorUserId) {
        Project project = projectMapper.selectById(task.getProjectId());
        ApiSceneExecuteReqDTO reqDTO = new ApiSceneExecuteReqDTO();
        reqDTO.setEnvironmentId(task.getEnvironmentId());
        // 系统身份（定时触发）标记 scheduled，执行记录据此区分自动/手动
        reqDTO.setTriggerType(ProjectAccessGuard.SYSTEM_OPERATOR_ID.equals(executorUserId)
                ? "scheduled" : "manual");
        ApiExecutionStartRespDTO start = sceneExecutionService.execute(
                project.getWorkspaceId(), task.getProjectId(), executorUserId,
                task.getBoundObjectId(), reqDTO);
        markRunning(task);
        return new SceneLaunch(project.getWorkspaceId(), start.getExecutionId());
    }

    SceneLaunch launchScene(ApiScheduledTask task, UUID executorUserId) {
        return launchSceneRaw(task, executorUserId);
    }

    private void awaitAndFinalizeScene(ApiScheduledTask task, UUID workspaceId, UUID executorUserId,
            String sceneExecutionId, String triggerType, LocalDateTime triggeredAt) {
        long startedAt = System.currentTimeMillis();
        String finalStatus = null;
        String errorMessage = null;
        String reportId = null;
        int durationMs = 0;
        Duration maxWait = Duration.ofMinutes(executionTimeoutMinutes);
        while (Duration.ofMillis(System.currentTimeMillis() - startedAt).compareTo(maxWait) < 0) {
            ApiExecutionStatusRespDTO status = sceneExecutionService.getStatus(
                    workspaceId, task.getProjectId(), executorUserId, UUID.fromString(sceneExecutionId));
            if (status != null && TERMINAL_STATUSES.contains(status.getStatus())) {
                boolean success = "success".equals(status.getStatus());
                finalStatus = success ? "success" : "failed";
                errorMessage = success ? null : firstNonBlank(status.getErrorMessage(),
                        "场景执行未通过：" + status.getStatus());
                reportId = status.getReportId();
                durationMs = status.getDurationMs() != null ? status.getDurationMs() : 0;
                break;
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (finalStatus == null) {
            finalStatus = "failed";
            errorMessage = "场景执行等待超时（" + executionTimeoutMinutes + " 分钟）";
        }
        insertRecord(task.getId(), task.getProjectId(), triggerType, finalStatus, errorMessage,
                parseUuid(reportId), null, triggeredAt, durationMs);
        updateTaskLastExecution(task.getId(), finalStatus);
    }

    private ImportOutcome runImport(ApiScheduledTask task, UUID executorUserId,
            String triggerType, LocalDateTime triggeredAt) {
        long startedAt = System.currentTimeMillis();
        ApiSwaggerUrl config = swaggerUrlMapper.selectById(task.getBoundObjectId());
        if (config == null || !config.getProjectId().equals(task.getProjectId())) {
            throw ServiceExceptionUtil.get(API_SWAGGER_URL_NOT_FOUND);
        }
        markRunning(task);
        // 成员校验由 importUrl 内部承担：系统身份直通，真实用户正常校验
        ApiImportResultRespDTO result = apiInterfaceService.importUrl(
                task.getProjectId(), executorUserId, config.getUrl(), null);
        insertRecord(task.getId(), task.getProjectId(), triggerType, "success", null,
                null, result.getImportHistoryId(), triggeredAt,
                (int) (System.currentTimeMillis() - startedAt));

        // 部分更新：仅回写配置的最近导入状态与时间（C9）
        ApiSwaggerUrl configUpdate = new ApiSwaggerUrl();
        configUpdate.setId(config.getId());
        configUpdate.setLastImportStatus("success");
        configUpdate.setLastImportAt(LocalDateTime.now());
        swaggerUrlMapper.updateById(configUpdate);
        updateTaskLastExecution(task.getId(), "success");
        return new ImportOutcome(result.getImportHistoryId(), "success");
    }

    private void recordFailure(ApiScheduledTask task, String triggerType, LocalDateTime triggeredAt, Exception e) {
        String message = truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        insertRecord(task.getId(), task.getProjectId(), triggerType, "failed", message,
                null, null, triggeredAt, 0);
        updateTaskLastExecution(task.getId(), "failed");
    }

    private void markRunning(ApiScheduledTask task) {
        updateTaskExecution(task.getId(), "running");
    }

    void updateTaskLastExecution(UUID taskId, String status) {
        updateTaskExecution(taskId, status);
    }

    private void updateTaskExecution(UUID taskId, String status) {
        ApiScheduledTask update = new ApiScheduledTask();
        update.setId(taskId);
        update.setLastExecutionStatus(status);
        update.setLastExecutionAt(LocalDateTime.now());
        taskMapper.updateById(update);
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

    private String truncate(String message) {
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @PreDestroy
    void shutdown() {
        trackerPool.shutdownNow();
    }

}
