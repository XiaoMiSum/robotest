package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.AiInvocationLogMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 异步任务与审计的定时调度。
 *
 * <p>多实例部署下各实例均会触发，三者均以带状态/时间谓词的条件 UPDATE
 * （拾取经抢占更新）实现、天然幂等，无需额外分布式锁。</p>
 */
@Slf4j
@Component
public class AiTaskScheduler {

    private static final int PENDING_PICKUP_BATCH = 20;
    /** running 且超过该时长未推进判定执行实例失联 */
    private static final int ORPHAN_MINUTES = 10;

    @Resource
    private AiAnalysisTaskMapper taskMapper;
    @Resource
    private AiInvocationLogMapper invocationLogMapper;
    @Resource
    private AiTaskService aiTaskService;
    @Resource
    private AiConfigService aiConfigService;

    /**
     * pending 拾取：兜底线程池队列拒绝与实例重启丢失的内存队列
     */
    @Scheduled(fixedDelay = 30_000)
    public void pickupPendingTasks() {
        List<AiAnalysisTask> pending = taskMapper.findPendingTasks(PENDING_PICKUP_BATCH);
        if (pending.isEmpty()) {
            return;
        }
        AiTaskServiceImpl impl = (AiTaskServiceImpl) aiTaskService;
        pending.forEach(task -> impl.trySubmit(task.getId()));
    }

    /**
     * 孤儿回收：覆盖实例宕机场景（任务执行中每批次必须触发 updated_at 更新）
     */
    @Scheduled(fixedDelay = 300_000)
    public void recoverOrphanTasks() {
        int recovered = taskMapper.update(null, new LambdaUpdateWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getStatus, Constants.AiTaskStatus.RUNNING)
                .lt(AiAnalysisTask::getUpdatedAt, LocalDateTime.now().minusMinutes(ORPHAN_MINUTES))
                .set(AiAnalysisTask::getStatus, Constants.AiTaskStatus.FAILED)
                .set(AiAnalysisTask::getErrorMessage, "执行实例失联")
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now()));
        if (recovered > 0) {
            log.warn("[AI] 孤儿任务回收 {} 条", recovered);
        }
    }

    /**
     * 审计保留期两阶段清理（每日 03:00）：标记超期记录，物理删除已标记超 1 天的记录
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupInvocationLogs() {
        int retentionDays = aiConfigService.getIntSetting("logRetentionDays");
        LocalDateTime expireBefore = LocalDateTime.now().minusDays(retentionDays);
        int marked = invocationLogMapper.markExpired(expireBefore);
        int purged = invocationLogMapper.purgeMarked(LocalDateTime.now().minusDays(1));
        if (marked > 0 || purged > 0) {
            log.info("[AI] 审计日志清理：标记 {} 条，物理删除 {} 条", marked, purged);
        }
    }
}
