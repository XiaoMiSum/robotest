package io.github.xiaomisum.robotest.service.domain.review;

/**
 * NEW 状态的迁移表：SUBMIT_RECORD→IN_PROGRESS（首次标记自动升级），COMPLETE→COMPLETED，REJECT→REJECTED，其余不变
 * REOPEN 仅对 REJECTED 合法，活跃态由 workflow 统一拦截，此处落空迁移
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
        case REJECT -> ReviewStatus.REJECTED;
        case UPDATE_CASES, SYNC, DELETE, REOPEN -> ReviewStatus.NEW;
        };
    }
}