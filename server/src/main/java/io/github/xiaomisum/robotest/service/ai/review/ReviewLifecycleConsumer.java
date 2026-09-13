package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewLifecycleEvent;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 评审生命周期事件消费者：评审完成/删除后取消仍进行中的 review_check 任务。
 * 事件只在 service.ai 内部订阅；评审域只发布（03 §4④）。afterCommit 保证执行
 * 线程读不到未提交状态的问题与事务提交保持一致；无事务时（如单元测试）同步兜底执行。
 */
@Component
public class ReviewLifecycleConsumer {

    private final AiTaskService aiTaskService;

    public ReviewLifecycleConsumer(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReviewLifecycle(ReviewLifecycleEvent event) {
        aiTaskService.cancelByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, event.reviewId());
    }
}