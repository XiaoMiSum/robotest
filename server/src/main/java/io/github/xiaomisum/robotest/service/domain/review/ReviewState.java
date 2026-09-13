package io.github.xiaomisum.robotest.service.domain.review;

/**
 * 评审单状态的迁移决策（无状态变化时返回自身 code）
 */
public interface ReviewState {

    /**
     * 状态的身份标识
     */
    ReviewStatus code();

    /**
     * 状态出发的事件迁移，非法事件返回当前 code（非法跃迁仅 COMPLETED 存在，由 workflow 统一拦截）
     */
    ReviewStatus transition(ReviewEvent event);
}