package io.github.xiaomisum.robotest.service.domain.review;

/**
 * IN_PROGRESS 状态的迁移表：COMPLETE→COMPLETED，REJECT→REJECTED，其余不变
 * REOPEN 仅对 REJECTED 合法，活跃态由 workflow 统一拦截，此处落空迁移
 */
public class InProgressReviewState implements ReviewState {

    @Override
    public ReviewStatus code() {
        return ReviewStatus.IN_PROGRESS;
    }

    @Override
    public ReviewStatus transition(ReviewEvent event) {
        return switch (event) {
        case COMPLETE -> ReviewStatus.COMPLETED;
        case REJECT -> ReviewStatus.REJECTED;
        case SUBMIT_RECORD, UPDATE_CASES, SYNC, DELETE, REOPEN -> ReviewStatus.IN_PROGRESS;
        };
    }
}