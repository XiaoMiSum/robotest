package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 报告保留期清理（定时任务详细设计 4.4）：
 * 每日 03:00 一步物理删除超过保留期的接口测试报告及同期定时任务执行记录
 */
@Slf4j
@Component
public class ApiTestRetentionCleaner {

    @Resource
    private ApiReportMapper reportMapper;
    @Resource
    private ApiScheduledTaskExecutionMapper executionMapper;

    @Value("${robotest.api-test.scheduler.report-retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 0 3 * * ?")
    public void clean() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int reports = reportMapper.deletePhysicallyOlderThan(cutoff);
        int executions = executionMapper.deletePhysicallyOlderThan(cutoff);
        if (reports > 0 || executions > 0) {
            log.info("接口测试保留期清理完成：删除 {} 条报告与 {} 条定时任务执行记录（截止 {}）",
                    reports, executions, cutoff);
        }
    }
}
