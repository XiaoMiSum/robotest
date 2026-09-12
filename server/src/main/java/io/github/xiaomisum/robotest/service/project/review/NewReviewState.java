package io.github.xiaomisum.robotest.service.project.review;

/**
 * NEW 状态的迁移表：SUBMIT_RECORD→IN_PROGRESS（首次标记自动升级），COMPLETE→COMPLETED，其余不变
 */
public class NewReviewState implements ReviewState {

    @Override
    public ReviewStatus code() {
        return ReviewStatus.NEW;
    }

    @Override
    public ReviewStatus transition(ReviewEvent event) {
        return switch (event) {
        case SUBMIT_RECORD -> ReviewStatus.IN_PROGRESS;
        case COMPLETE -> ReviewStatus.COMPLETED;
        case UPDATE_CASES, SYNC, DELETE -> ReviewStatus.NEW;
        };
    }
}