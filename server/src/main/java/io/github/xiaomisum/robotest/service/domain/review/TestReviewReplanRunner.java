package io.github.xiaomisum.robotest.service.domain.review;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 评审用例重规划异步起跑（详设 3.2）：独立组件承载 @Async，避免 Service 自调用使异步失效。
 */
@Component
public class TestReviewReplanRunner {

    private static final Logger log = LoggerFactory.getLogger(TestReviewReplanRunner.class);

    @Resource
    private TestReviewService testReviewService;

    @Async
    public void run(UUID projectId, UUID reviewId) {
        try {
            String message = testReviewService.executeReplan(projectId, reviewId);
            testReviewService.markReplanSucceeded(projectId, reviewId, message);
        } catch (Exception e) {
            // 事务内的快照更新由 executeReplan 回滚，失败状态单独回写，避免轮询一直停在 running
            log.warn("评审用例重规划失败：reviewId={}", reviewId, e);
            try {
                testReviewService.markReplanFailed(projectId, reviewId, e.getMessage());
            } catch (Exception markException) {
                log.error("评审重规划失败状态回写失败：reviewId={}", reviewId, markException);
            }
        }
    }
}
