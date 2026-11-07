package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.core.ExecutionLifecycle;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 执行心跳超时清扫器（总册 4.2 生命周期）：按执行表 status + heartbeat_at 索引扫描，
 * 超过 task_timeout_seconds 的进行中执行置 aborted（1000018117）并同步任务态；
 * 心跳由执行核每跳刷新，活跃执行不会被误杀。
 */
@Component
public class AiTaskSweeper {

    private static final Logger log = LoggerFactory.getLogger(AiTaskSweeper.class);

    private static final String TIMEOUT_ERROR_MSG = "任务执行超时";

    @Resource
    private AiTaskExecutionMapper executionMapper;
    @Resource
    private AiSettingsReader settingsReader;
    @Resource
    private ExecutionLifecycle executionLifecycle;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void sweepTimeoutExecutions() {
        int timeoutSeconds = settingsReader.settings().taskTimeoutSeconds();
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(timeoutSeconds);
        for (AiTaskExecution stale : executionMapper.selectStale(cutoff)) {
            if (!executionLifecycle.terminateIfHeartbeatStale(stale.getTaskId(), stale.getId(), cutoff,
                    ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), TIMEOUT_ERROR_MSG)) {
                continue;
            }
            log.warn("[AI] 执行心跳超时清扫 executionId={} taskId={} steps={}",
                    stale.getId(), stale.getTaskId(), stale.getSteps());
        }
    }
}
