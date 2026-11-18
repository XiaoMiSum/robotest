package io.github.xiaomisum.robotest.service.domain.functional;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;

/**
 * 应用重启后清理残留的 running 重规划任务（详设 3.2 / 4.2）：
 * 进程退出会中断异步线程，若保留 running 状态前端将无限轮询。
 */
@Component
public class ReplanStatusRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReplanStatusRecoveryRunner.class);
    private static final String INTERRUPTED_MESSAGE = "服务重启导致中断";

    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private TestPlanMapper testPlanMapper;

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        int reviews = testReviewMapper.update(null, new LambdaUpdateWrapperX<TestReview>()
                .eq(TestReview::getReplanStatus, Constants.ReplanStatus.RUNNING)
                .set(TestReview::getReplanStatus, Constants.ReplanStatus.FAILED)
                .set(TestReview::getReplanMessage, INTERRUPTED_MESSAGE)
                .set(TestReview::getReplanFinishedAt, now));
        int plans = testPlanMapper.update(null, new LambdaUpdateWrapperX<TestPlan>()
                .eq(TestPlan::getReplanStatus, Constants.ReplanStatus.RUNNING)
                .set(TestPlan::getReplanStatus, Constants.ReplanStatus.FAILED)
                .set(TestPlan::getReplanMessage, INTERRUPTED_MESSAGE)
                .set(TestPlan::getReplanFinishedAt, now));
        if (reviews > 0 || plans > 0) {
            log.info("重规划残留状态清理完成：评审 {} 条，计划 {} 条", reviews, plans);
        }
    }
}
