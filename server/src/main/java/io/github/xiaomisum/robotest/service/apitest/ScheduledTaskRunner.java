package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 定时/手动触发的统一执行入口（Facade）。
 * 实际执行委托给 {@link TestPlanTaskHandler} 和 {@link ApiSyncTaskHandler}，
 * 本类仅保留原有公共方法签名以保证调用方（ApiTestTaskScheduler / ApiScheduleServiceImpl）零改动。
 *
 * <p><b>单实例内存态约束（总体重构计划 R5）：</b>
 * {@code trackerPool} 为 JVM 内线程池，仅对当前实例有效。
 * 多实例部署时各实例独立执行，通过任务级 running 跳过语义保证幂等，不提供跨实例执行协调。
 */
@Slf4j
@Component
public class ScheduledTaskRunner {

    private static final String TYPE_TEST_PLAN = "scene_execute";

    @Resource
    private TestPlanTaskHandler testPlanHandler;
    @Resource
    private ApiSyncTaskHandler apiSyncHandler;
    @Resource
    private ApiScheduledTaskMapper taskMapper;
    @Resource
    private ApiScheduledTaskExecutionMapper executionMapper;

    /** 手动触发场景执行后的完成监听与调度线程隔离 */
    private final ExecutorService trackerPool = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "api-test-task-tracker");
        thread.setDaemon(true);
        return thread;
    });

    /** 兼容旧调用方：ImportOutcome 从 ApiSyncTaskHandler 转发 */
    public record ImportOutcome(UUID importRecordId, String status) {
    }

    /** 调度线程调用：系统身份执行 */
    public void runTask(ApiScheduledTask task, String triggerType) {
        LocalDateTime triggeredAt = LocalDateTime.now();
        try {
            if (TYPE_TEST_PLAN.equals(task.getTaskType())) {
                testPlanHandler.executeTask(task,
                        io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard.SYSTEM_OPERATOR_ID,
                        triggerType, triggeredAt);
            } else {
                apiSyncHandler.executeSync(task,
                        io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard.SYSTEM_OPERATOR_ID,
                        triggerType, triggeredAt);
            }
        } catch (Exception e) {
            recordFailure(task, triggerType, triggeredAt, e);
        }
    }

    /** 手动触发测试计划任务：异步执行 */
    public void launchTestPlanAsyncFinalize(ApiScheduledTask task, UUID executorUserId, String triggerType) {
        LocalDateTime triggeredAt = LocalDateTime.now();
        updateTaskExecution(task.getId(), "running");
        trackerPool.submit(() -> testPlanHandler.executeTask(task, executorUserId, triggerType, triggeredAt));
    }

    /** 手动触发接口同步任务：同步执行并留痕 */
    public ImportOutcome runSyncRethrow(ApiScheduledTask task, UUID executorUserId, String triggerType) {
        LocalDateTime triggeredAt = LocalDateTime.now();
        try {
            ApiSyncTaskHandler.ImportOutcome raw = apiSyncHandler.executeSync(task, executorUserId, triggerType,
                    triggeredAt);
            return new ImportOutcome(raw.importRecordId(), raw.status());
        } catch (Exception e) {
            recordFailure(task, triggerType, triggeredAt, e);
            throw e;
        }
    }

    /** 上一次触发未结束时写入 skipped 记录 */
    public void writeSkipped(ApiScheduledTask task, String triggerType) {
        ApiScheduledTaskExecution record = new ApiScheduledTaskExecution();
        record.setId(UUID.randomUUID());
        record.setTaskId(task.getId());
        record.setProjectId(task.getProjectId());
        record.setTriggerType(triggerType);
        record.setStatus("skipped");
        record.setErrorMessage("上一次执行尚未结束，本次触发跳过");
        record.setTriggeredAt(LocalDateTime.now());
        record.setDurationMs(0);
        executionMapper.insert(record);
    }

    private void recordFailure(ApiScheduledTask task, String triggerType, LocalDateTime triggeredAt, Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (message.length() > 2000) {
            message = message.substring(0, 2000);
        }
        ApiScheduledTaskExecution record = new ApiScheduledTaskExecution();
        record.setId(UUID.randomUUID());
        record.setTaskId(task.getId());
        record.setProjectId(task.getProjectId());
        record.setTriggerType(triggerType);
        record.setStatus("failed");
        record.setErrorMessage(message);
        record.setTriggeredAt(triggeredAt);
        record.setDurationMs(0);
        executionMapper.insert(record);
        updateTaskExecution(task.getId(), "failed");
    }

    private void updateTaskExecution(UUID taskId, String status) {
        ApiScheduledTask update = new ApiScheduledTask();
        update.setId(taskId);
        update.setLastExecutionStatus(status);
        update.setLastExecutionAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    @PreDestroy
    void shutdown() {
        trackerPool.shutdownNow();
    }
}
