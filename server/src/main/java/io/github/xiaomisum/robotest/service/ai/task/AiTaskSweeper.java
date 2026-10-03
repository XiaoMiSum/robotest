package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 任务超时清扫器（详设 4.2）：按 status + updated_at 索引扫描 pending / running，
 * 超过 task_timeout_seconds 置 failed（1000018117）；进度上报会刷新 updated_at，活跃任务不会被误杀。
 */
@Component
public class AiTaskSweeper {

    private static final Logger log = LoggerFactory.getLogger(AiTaskSweeper.class);

    private static final String TIMEOUT_ERROR_MSG = "任务执行超时";

    @Resource
    private AiTaskMapper taskMapper;
    @Resource
    private AiSettingsReader settingsReader;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void sweepTimeoutTasks() {
        int timeoutSeconds = settingsReader.settings().taskTimeoutSeconds();
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(timeoutSeconds);
        int swept = taskMapper.failStale(cutoff, ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), TIMEOUT_ERROR_MSG);
        if (swept > 0) {
            log.info("[AI] 任务超时清扫完成，{} 个任务置 failed（timeout={}s）", swept, timeoutSeconds);
        }
    }
}
