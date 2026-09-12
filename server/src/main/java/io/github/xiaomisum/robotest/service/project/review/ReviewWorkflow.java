package io.github.xiaomisum.robotest.service.project.review;

import io.github.xiaomisum.robotest.model.entity.review.TestReview;

/**
 * 评审状态机上下文：唯一裁决 status 迁移的域服务
 */
public interface ReviewWorkflow {

    /**
     * 裁决迁移并返回目标状态；非法跃迁抛业务异常（C3）
     */
    ReviewStatus transition(TestReview review, ReviewEvent event);

    /**
     * 仅判定迁移是否合法，不抛错
     */
    boolean can(TestReview review, ReviewEvent event);

    /**
     * 断言迁移合法，非法抛业务异常（C3）
     */
    void assertTransition(TestReview review, ReviewEvent event);
}