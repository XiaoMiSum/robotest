package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 混合任务完成判据（GitLab 集成详设 4.4、定时任务详设 27 §2 步骤 8，BR-1.28）：
 * 平台组全部到达终态且批次内所有流水线到达终态，任务方可从 running 转 success / failed。
 *
 * <p>扫描式、无内存态（与 {@link ApiGitBatchPoller} 同架构）：两侧结果全部取自库中产出，进程重启后
 * 扫描即自愈（BR-1.29）；触发线程结束时的短路调用只是省去一个扫描周期。
 *
 * <p>平台组终态信号（零新增状态表与列，任务状态仍由任务行字段承载）：本触发写入的套件报告或终态
 * （success / failed）任务执行记录之一。
 * 平台组正常结束先插报告再写逐场景记录、崩溃走失败记录、无可用场景由触发线程补写 success 标记记录，
 * 故产出均晚于平台组执行结束——平台组仍在执行时判据不满足、保持 running；执行中途进程退出则无任何
 * 产出，任务残留 running（与既有 running 残留口径一致）。平台组失败取失败任务执行记录，或最新套件
 * 报告（external_id = 任务 ID、source = schedule、创建不早于批次冻结）的 summary.failedScenes。
 * 流水线侧取批次记录状态：全部终态后任一非 success 即失败，两侧结果取「与」。
 * 流水线组场景的任务执行记录在判据满足时补写（report_id 关联平台套件报告）。
 */
@Slf4j
@Component
public class ApiScheduledTaskCompletionTracker {

    /** 批次终态集合（与 {@code ApiGitBatchService} 终态写闸门白名单一致） */
    private static final Set<String> BATCH_TERMINAL_STATUSES =
            Set.of("success", "failed", "error", "cancelled", "timeout");
    private static final String TYPE_TEST_PLAN = "scene_execute";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String SOURCE_SCHEDULE = "schedule";

    private final ApiScheduledTaskMapper taskMapper;
    private final ApiScheduledTaskExecutionMapper executionMapper;
    private final ApiExecutionRecordMapper executionRecordMapper;
    private final ApiSceneMapper sceneMapper;
    private final ApiReportMapper reportMapper;

    public ApiScheduledTaskCompletionTracker(ApiScheduledTaskMapper taskMapper,
            ApiScheduledTaskExecutionMapper executionMapper, ApiExecutionRecordMapper executionRecordMapper,
            ApiSceneMapper sceneMapper, ApiReportMapper reportMapper) {
        this.taskMapper = taskMapper;
        this.executionMapper = executionMapper;
        this.executionRecordMapper = executionRecordMapper;
        this.sceneMapper = sceneMapper;
        this.reportMapper = reportMapper;
    }

    @Scheduled(fixedDelayString = "${robotest.gitlab.pipeline-poll-interval-ms:30000}",
            initialDelayString = "${robotest.gitlab.pipeline-poll-interval-ms:30000}")
    public void scan() {
        List<ApiScheduledTask> runningTasks;
        try {
            runningTasks = taskMapper.listRunningTestPlan();
        } catch (Exception e) {
            log.warn("[任务判据] 扫描运行中任务失败，本轮跳过：{}", e.getMessage());
            return;
        }
        for (ApiScheduledTask task : runningTasks) {
            try {
                tryFinalize(task);
            } catch (Exception e) {
                log.warn("[任务判据] 终局处理异常 taskId={}：{}", task.getId(), e.getMessage());
            }
        }
    }

    /**
     * 终局裁定入口：触发线程结束时的短路调用与周期扫描共用。
     * 判据不满足（流水线侧或平台组未达终态）时任务保持 running，下轮扫描自然重试。
     *
     * @return true = 本次已裁定任务终态；false = 不适用（非混合任务 / 非运行态）或判据未满足
     */
    public boolean tryFinalize(UUID taskId) {
        ApiScheduledTask task = taskMapper.selectById(taskId);
        return task != null && tryFinalize(task);
    }

    private boolean tryFinalize(ApiScheduledTask task) {
        if (!TYPE_TEST_PLAN.equals(task.getTaskType())
                || !STATUS_RUNNING.equals(task.getLastExecutionStatus())) {
            return false;
        }
        List<ApiExecutionRecord> batch = currentBatch(task);
        if (batch.isEmpty()) {
            // 平台组任务没有 CI 批次，终态由触发线程直接裁定，不在本判据范围
            return false;
        }
        if (batch.stream().anyMatch(r -> !BATCH_TERMINAL_STATUSES.contains(r.getStatus()))) {
            // 流水线侧未达终态，保持 running（BR-1.28「与」逻辑），下轮再判
            return false;
        }
        LocalDateTime triggeredAt = task.getLastExecutionAt();
        ApiReport suiteReport = reportMapper.selectLatestSuiteReportSince(task.getId(), SOURCE_SCHEDULE,
                batch.get(0).getCreatedAt());
        if (suiteReport == null && executionMapper.countTerminalSince(task.getId(), triggeredAt) == 0) {
            // 平台组仍在执行（套件报告与终态任务执行记录均未产出），保持 running 下轮再判
            return false;
        }
        boolean ciFailed = batch.stream().anyMatch(r -> !STATUS_SUCCESS.equals(r.getStatus()));
        boolean platformFailed = executionMapper.countFailedSince(task.getId(), triggeredAt) > 0
                || platformFailed(suiteReport);
        int durationMs = triggeredAt == null ? 0 : (int) Math.max(0, System.currentTimeMillis()
                - triggeredAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        writeCiSceneRecords(task, batch, suiteReport, triggeredAt, durationMs);

        String finalStatus = ciFailed || platformFailed ? STATUS_FAILED : STATUS_SUCCESS;
        ApiScheduledTask update = new ApiScheduledTask();
        update.setId(task.getId());
        update.setLastExecutionStatus(finalStatus);
        update.setLastExecutionAt(LocalDateTime.now());
        taskMapper.updateById(update);
        log.info("[任务判据] 混合任务终局 taskId={} status={} ciFailed={} platformFailed={}",
                task.getId(), finalStatus, ciFailed, platformFailed);
        return true;
    }

    /**
     * 本触发的批次：running 期间不产生同任务新触发（定时只记 skipped、手动返回 1000017504），
     * 故创建时刻不早于本次 running 时刻的批次记录即属当前触发，历史触发的批次不参与判据。
     */
    private List<ApiExecutionRecord> currentBatch(ApiScheduledTask task) {
        if (task.getLastExecutionAt() == null) {
            return List.of();
        }
        LocalDateTime triggerStart = task.getLastExecutionAt();
        return executionRecordMapper.listBatchRecordsByTask(task.getId()).stream()
                .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(triggerStart))
                .toList();
    }

    /** 平台组结果之一：套件报告场景失败计数 > 0 即失败；无报告（平台组无可用场景 / 崩溃走失败记录）时由失败记录信号兜底 */
    private boolean platformFailed(ApiReport suiteReport) {
        if (suiteReport == null || suiteReport.getSummary() == null) {
            return false;
        }
        Object failedScenes = suiteReport.getSummary().get("failedScenes");
        return failedScenes instanceof Number number && number.longValue() > 0;
    }

    /** 判据满足时补写流水线组场景的任务执行记录（详设 27 §2 步骤 8：任务执行记录覆盖两组） */
    private void writeCiSceneRecords(ApiScheduledTask task, List<ApiExecutionRecord> batch,
            ApiReport suiteReport, LocalDateTime triggeredAt, Integer durationMs) {
        Set<UUID> sceneIds = batch.stream().map(ApiExecutionRecord::getSceneId).collect(Collectors.toSet());
        Map<UUID, String> sceneNames = sceneMapper.selectByIds(sceneIds).stream()
                .collect(Collectors.toMap(ApiScene::getId, ApiScene::getName, (a, b) -> a));
        for (ApiExecutionRecord record : batch) {
            boolean success = STATUS_SUCCESS.equals(record.getStatus());
            String sceneName = sceneNames.getOrDefault(record.getSceneId(), "");
            ApiScheduledTaskExecution taskRecord = new ApiScheduledTaskExecution();
            taskRecord.setId(UUID.randomUUID());
            taskRecord.setTaskId(task.getId());
            taskRecord.setProjectId(task.getProjectId());
            taskRecord.setTriggerType(record.getTriggerType());
            taskRecord.setStatus(success ? STATUS_SUCCESS : STATUS_FAILED);
            taskRecord.setErrorMessage(success ? "场景「" + sceneName + "」执行完成"
                    : truncate(record.getErrorMessage() == null ? "流水线执行失败" : record.getErrorMessage()));
            taskRecord.setReportId(suiteReport == null ? null : suiteReport.getId());
            taskRecord.setTriggeredAt(triggeredAt);
            taskRecord.setDurationMs(durationMs);
            executionMapper.insert(taskRecord);
        }
    }

    private String truncate(String message) {
        return message != null && message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
