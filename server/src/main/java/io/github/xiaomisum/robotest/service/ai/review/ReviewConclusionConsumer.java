package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewConclusionEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import xyz.migoo.framework.common.exception.ServiceException;

/**
 * 评审结论事件消费者（06 §5.2 自动路径）：completeReview 事务提交后入队 review_conclusion 任务，
 * 结论覆盖式落库（保留最新 success）。并发双触发时先入队者受理，后者命中 6005 幂等吞掉。
 */
@Component
public class ReviewConclusionConsumer {

    private final AiTaskService aiTaskService;
    private final TestReviewMapper testReviewMapper;
    private final ProjectMapper projectMapper;

    public ReviewConclusionConsumer(AiTaskService aiTaskService,
            TestReviewMapper testReviewMapper, ProjectMapper projectMapper) {
        this.aiTaskService = aiTaskService;
        this.testReviewMapper = testReviewMapper;
        this.projectMapper = projectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReviewConclusion(ReviewConclusionEvent event) {
        TestReview review = testReviewMapper.selectById(event.reviewId());
        if (review == null || !Constants.Status.COMPLETED.equals(review.getStatus())) {
            return;
        }
        // 评审域实体无 workspaceId，经项目补全（C4 上下文隔离切面在事件线程无头可取）
        Project project = projectMapper.selectById(review.getProjectId());
        if (project == null) {
            return;
        }
        try {
            aiTaskService.createTask(Constants.AiTaskType.REVIEW_CONCLUSION,
                    project.getWorkspaceId(), review.getProjectId(), event.reviewId(), review.getInitiatorId());
        } catch (ServiceException e) {
            if (!ErrorCodeConstants.AI_TASK_DUPLICATE.msg().equals(e.getMessage())) {
                throw e;
            }
        }
    }
}