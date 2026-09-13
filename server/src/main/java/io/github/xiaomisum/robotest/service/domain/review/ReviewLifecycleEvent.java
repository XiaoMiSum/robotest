package io.github.xiaomisum.robotest.service.domain.review;

import java.util.UUID;

/**
 * 评审生命周期事件：评审离开可检查状态（完成/删除）时发布，
 * 解除 TestReviewServiceImpl → AiTaskService 的反向直接调用（03 §4④ / 06 §5.2）。
 * 消费规则：事件只在 service.ai 内部订阅，域代码只发布。
 */
public record ReviewLifecycleEvent(UUID reviewId) {
}