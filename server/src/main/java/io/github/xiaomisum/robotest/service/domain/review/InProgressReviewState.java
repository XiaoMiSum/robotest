package io.github.xiaomisum.robotest.service.domain.review;

/**
 * IN_PROGRESS 状态的迁移表：COMPLETE→COMPLETED，其余不变
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
        case SUBMIT_RECORD, UPDATE_CASES, SYNC, DELETE -> ReviewStatus.IN_PROGRESS;
        };
    }
}