package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * JVM 内分钟级 Cron 调度器（定时任务详细设计 4.1）：
 * 启动加载 enabled 任务注册一次性延迟触发，触发后重算下次时间续订；
 * 多实例部署下各实例均会触发，任务级 running 跳过语义保证不重复执行。
 */
@Component
public class ApiTestTaskScheduler {

    @Resource
    private ApiScheduledTaskMapper taskMapper;
    @Resource
    private ScheduledTaskRunner taskRunner;

    private final ScheduledExecutorService executor;

    private final Map<UUID, ScheduledFuture<?>> registrations = new ConcurrentHashMap<>();

    public ApiTestTaskScheduler(@Value("${robotest.api-test.scheduler.pool-size:2}") int poolSize) {
        ScheduledThreadPoolExecutor scheduledPool = new ScheduledThreadPoolExecutor(Math.max(1, poolSize),
                runnable -> {
                    Thread thread = new Thread(runnable, "api-test-scheduler");
                    thread.setDaemon(true);
                    return thread;
                });
        scheduledPool.setRemoveOnCancelPolicy(true);
        this.executor = scheduledPool;
    }

    @EventListener(ApplicationReadyEvent.class)
    void loadEnabledTasks() {
        taskMapper.selectEnabled().forEach(task -> register(task.getId()));
    }

    /** 任务创建/更新/启停/删除后由 Service 回调，重载该任务的调度注册 */
    public void onTaskChanged(UUID taskId) {
        cancelRegistration(taskId);
        // 逻辑删除行被 @TableLogic 过滤，selectById 返回 null 即视为已删除
        ApiScheduledTask task = taskMapper.selectById(taskId);
        if (task != null && Boolean.TRUE.equals(task.getEnabled())) {
            register(taskId);
        }
    }

    private void register(UUID taskId) {
        LocalDateTime now = LocalDateTime.now();
        CronExpression expression = CronSupport.parse(readCron(taskId));
        if (expression == null) {
            return;
        }
        LocalDateTime next = expression.next(now);
        if (next == null) {
            return;
        }
        long delayMillis = Math.max(0, Duration.between(now, next).toMillis());
        ScheduledFuture<?> future = executor.schedule(() -> fire(taskId), delayMillis, TimeUnit.MILLISECONDS);
        registrations.put(taskId, future);
    }

    private String readCron(UUID taskId) {
        ApiScheduledTask task = taskMapper.selectById(taskId);
        return task != null ? task.getCronExpression() : "";
    }

    private void fire(UUID taskId) {
        try {
            ApiScheduledTask task = taskMapper.selectById(taskId);
            // 已删除/停用的任务不再续订；上一次未结束则记 skipped（设计 4.1 第 3-4 步）
            if (task == null || !Boolean.TRUE.equals(task.getEnabled())) {
                return;
            }
            if ("running".equals(task.getLastExecutionStatus())) {
                taskRunner.writeSkipped(task, "scheduled");
            } else {
                taskRunner.runTask(task, "scheduled");
            }
        } finally {
            registrations.remove(taskId);
            ApiScheduledTask latest = taskMapper.selectById(taskId);
            if (latest != null && Boolean.TRUE.equals(latest.getEnabled())) {
                register(taskId);
            }
        }
    }

    private void cancelRegistration(UUID taskId) {
        ScheduledFuture<?> future = registrations.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

}
