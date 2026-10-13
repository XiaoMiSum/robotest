package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.task.DispatchableTask;
import io.github.xiaomisum.robotest.framework.task.TaskDispatchContext;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GitLab 同步任务执行器（GitLab 集成详设 3.6、定时任务详设 27 §4）：
 * 置 running → 幂等闸门判定（无变更跳过）→ 同步导入 → 落执行记录与任务终态。
 * 同步任务不产生报告、不写 api_report；仓库配置缺失/禁用、GitLab 不可达、解析异常
 * 一律写 failed 执行记录留痕、不重试；执行期异常不上抛（触发入口的前置校验异常已在入池前抛出）。
 */
@Slf4j
@Component
public class GitLabSyncTaskHandler implements DispatchableTask {

    public static final String TYPE = "gitlab_sync";
    /** 无变更跳过的执行记录说明（详设 27 §4 步骤 2：error_message = 无变更说明，状态仍为 success） */
    static final String NO_CHANGE_MESSAGE = "目标分支提交与上次导入一致，本次同步无变更，不进入解析";

    @Resource
    private ApiGitImportService apiGitImportService;
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
        // 由 ScheduledTaskRunner 委托调用，实际逻辑通过 executeTask 方法暴露
        return null;
    }

    /**
     * 执行一次源变更同步（详设 27 §4）。
     *
     * @param preInsertedRecordId 手动触发时为触发入口预插入的 running 执行记录 ID（完成时更新为终态）；
     *                            定时触发为 null（完成时现插 1 条终态记录）
     */
    public void executeTask(ApiScheduledTask task, UUID executorUserId, String triggerType,
            LocalDateTime triggeredAt, UUID preInsertedRecordId) {
        updateTaskLastExecution(task.getId(), "running");
        long startedAt = System.currentTimeMillis();
        try {
            ApiGitImportService.SyncOutcome outcome =
                    apiGitImportService.syncImport(task.getProjectId(), executorUserId);
            Map<String, Object> syncSummary = new LinkedHashMap<>(outcome.summary());
            syncSummary.put("commit", outcome.headCommit());
            finish(task, triggerType, preInsertedRecordId, triggeredAt,
                    "success", outcome.skipped() ? NO_CHANGE_MESSAGE : null,
                    syncSummary, duration(startedAt));
        } catch (Exception e) {
            // 执行期异常一律落 failed 留痕、不上抛（详设 27 §4 失败处理）
            log.warn("[GitLab 同步] 任务执行失败 taskId={}：{}", task.getId(), e.getMessage());
            finish(task, triggerType, preInsertedRecordId, triggeredAt,
                    "failed", truncate(e), null, duration(startedAt));
        }
    }

    private void finish(ApiScheduledTask task, String triggerType, UUID preInsertedRecordId,
            LocalDateTime triggeredAt, String status, String errorMessage,
            Map<String, Object> syncSummary, int durationMs) {
        ApiScheduledTaskExecution carrier = new ApiScheduledTaskExecution();
        if (preInsertedRecordId != null) {
            // 手动触发：更新预插入的 running 记录（C11 部分更新——error_message / sync_summary
            // 预插入时为 null，按需写入；成功且无失败说明时无需显式清空）
            carrier.setId(preInsertedRecordId);
            carrier.setStatus(status);
            carrier.setDurationMs(durationMs);
            if (errorMessage != null) {
                carrier.setErrorMessage(errorMessage);
            }
            if (syncSummary != null) {
                carrier.setSyncSummary(syncSummary);
            }
            executionMapper.updateById(carrier);
        } else {
            carrier.setId(UUID.randomUUID());
            carrier.setTaskId(task.getId());
            carrier.setProjectId(task.getProjectId());
            carrier.setTriggerType(triggerType);
            carrier.setStatus(status);
            carrier.setErrorMessage(errorMessage);
            carrier.setSyncSummary(syncSummary);
            carrier.setTriggeredAt(triggeredAt);
            carrier.setDurationMs(durationMs);
            executionMapper.insert(carrier);
        }
        updateTaskLastExecution(task.getId(), status);
    }

    private void updateTaskLastExecution(UUID taskId, String status) {
        // C11 部分更新：仅 id + 终态两列，不以查询整行作载体
        ApiScheduledTask update = new ApiScheduledTask();
        update.setId(taskId);
        update.setLastExecutionStatus(status);
        update.setLastExecutionAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private static int duration(long startedAt) {
        return (int) (System.currentTimeMillis() - startedAt);
    }

    private static String truncate(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
