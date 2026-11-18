package io.github.xiaomisum.robotest.service.domain.plan;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 计划用例重规划异步起跑（详设 4.2）：独立组件承载 @Async，避免 Service 自调用使异步失效。
 */
@Component
public class TestPlanReplanRunner {

    private static final Logger log = LoggerFactory.getLogger(TestPlanReplanRunner.class);

    @Resource
    private TestPlanService testPlanService;

    @Async
    public void run(UUID projectId, UUID planId) {
        try {
            String message = testPlanService.executeReplan(projectId, planId);
            testPlanService.markReplanSucceeded(projectId, planId, message);
        } catch (Exception e) {
            // 事务内的快照更新由 executeReplan 回滚，失败状态单独回写，避免轮询一直停在 running
            log.warn("计划用例重规划失败：planId={}", planId, e);
            try {
                testPlanService.markReplanFailed(projectId, planId, e.getMessage());
            } catch (Exception markException) {
                log.error("计划重规划失败状态回写失败：planId={}", planId, markException);
            }
        }
    }
}
