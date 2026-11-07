package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.core.TraceRecorder;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

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
    private AiTaskMapper taskMapper;
    @Resource
    private AiSettingsReader settingsReader;
    @Resource
    private TraceRecorder traceRecorder;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void sweepTimeoutExecutions() {
        int timeoutSeconds = settingsReader.settings().taskTimeoutSeconds();
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(timeoutSeconds);
        for (AiTaskExecution stale : executionMapper.selectStale(cutoff)) {
            int steps = stale.getSteps() == null ? 0 : stale.getSteps();
            int tokensIn = stale.getTokensIn() == null ? 0 : stale.getTokensIn();
            int tokensOut = stale.getTokensOut() == null ? 0 : stale.getTokensOut();
            BigDecimal cost = stale.getCost() == null ? BigDecimal.ZERO : stale.getCost();
            // 条件写与执行核竞争：已终态（或刚被用户终止）的执行跳过
            if (!executionMapper.abort(stale.getId(), ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    TIMEOUT_ERROR_MSG, steps, tokensIn, tokensOut, cost, elapsedSince(stale))) {
                continue;
            }
            traceRecorder.record(stale.getId(), Constants.AiTraceEventType.STATUS_CHANGE,
                    "执行终止: " + TIMEOUT_ERROR_MSG,
                    Map.of("terminateCode", ErrorCodeConstants.AI_MODEL_CALL_FAILED.code()));
            taskMapper.markAborted(stale.getTaskId(), ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    TIMEOUT_ERROR_MSG);
            taskMapper.accumulateTokens(stale.getTaskId(), tokensIn, tokensOut);
            log.warn("[AI] 执行心跳超时清扫 executionId={} taskId={} steps={}",
                    stale.getId(), stale.getTaskId(), steps);
        }
    }

    private static int elapsedSince(AiTaskExecution execution) {
        if (execution.getCreatedAt() == null) {
            return 0;
        }
        long millis = Duration.between(execution.getCreatedAt(), LocalDateTime.now()).toMillis();
        return (int) Math.min(Math.max(millis, 0), Integer.MAX_VALUE);
    }
}
