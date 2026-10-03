package io.github.xiaomisum.robotest.service.domain.requirement;

import java.util.UUID;

/**
 * 影响分析任务提交出口（详设 4.4）：需求确认态的题 / 描述 / 模块变更在事务提交后异步触发，
 * 提交失败只记日志不回滚需求更新。实现由 AI 任务框架（WP-4.2）提供；框架未接入时无实现 Bean，需求侧静默跳过。
 */
public interface ImpactAnalysisTaskPublisher {

    void publish(UUID requirementId, UUID projectId, UUID operatorId);
}
