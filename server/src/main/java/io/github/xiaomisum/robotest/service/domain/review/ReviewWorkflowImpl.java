package io.github.xiaomisum.robotest.service.domain.review;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.Map;

/**
 * 评审状态机实现：按迁移矩阵（§3.1.1）逐格裁决
 * COMPLETED 为终态，无状态类；仅放行 COMPLETE（幂等）与 DELETE（软删许可），其余事件抛 TEST_REVIEW_FINISHED
 */
@Service
public class ReviewWorkflowImpl implements ReviewWorkflow {

    private final Map<ReviewStatus, ReviewState> states;

    public ReviewWorkflowImpl() {
        this.states = Map.of(
                ReviewStatus.NEW, new NewReviewState(),
                ReviewStatus.IN_PROGRESS, new InProgressReviewState());
    }

    @Override
    public ReviewStatus transition(TestReview review, ReviewEvent event) {
        ReviewStatus status = resolveStatus(review);
        if (status == ReviewStatus.COMPLETED) {
            // 终态：非法跃迁统一在此拦截（C3）
            if (event != ReviewEvent.COMPLETE && event != ReviewEvent.DELETE) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_FINISHED);
            }
            return status;
        }
        ReviewState state = states.getOrDefault(status, new NewReviewState());
        return state.transition(event);
    }

    @Override
    public boolean can(TestReview review, ReviewEvent event) {
        ReviewStatus status = resolveStatus(review);
        if (status == ReviewStatus.COMPLETED) {
            return event == ReviewEvent.COMPLETE || event == ReviewEvent.DELETE;
        }
        return true;
    }

    @Override
    public void assertTransition(TestReview review, ReviewEvent event) {
        transition(review, event);
    }

    /**
     * 状态值为空或未知时按 NEW 兜底：现状代码只显式拦截 COMPLETED，未知状态等同"可写"（创建即 NEW）
     */
    private ReviewStatus resolveStatus(TestReview review) {
        ReviewStatus status = ReviewStatus.fromCode(review.getStatus());
        return status != null ? status : ReviewStatus.NEW;
    }
}