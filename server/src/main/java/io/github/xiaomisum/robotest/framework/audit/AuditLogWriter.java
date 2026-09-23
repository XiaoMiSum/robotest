package io.github.xiaomisum.robotest.framework.audit;

import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.repository.admin.AuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 审计写入器：切面注解路径与登录显式写入路径共用。
 * <p>审计是旁路能力，写入失败只丢弃记录本身，绝不影响触发它的业务流程。</p>
 */
@Slf4j
@Component
public class AuditLogWriter {

    private final AuditLogMapper auditLogMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate auditTxTemplate;

    public AuditLogWriter(AuditLogMapper auditLogMapper,
                          PlatformTransactionManager transactionManager,
                          ApplicationEventPublisher eventPublisher) {
        this.auditLogMapper = auditLogMapper;
        this.eventPublisher = eventPublisher;
        // 独立事务：审计不随业务事务回滚，业务回滚也不丢已成功的审计
        this.auditTxTemplate = new TransactionTemplate(transactionManager);
        this.auditTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void write(AuditLog record) {
        try {
            auditTxTemplate.executeWithoutResult(status -> {
                auditLogMapper.insert(record);
                // 落库成功后再发布：消费方（报表/通知）依赖已存在的记录
                eventPublisher.publishEvent(new AuditRecordedEvent(
                        record.getId(), record.getEntityType(),
                        record.getEntityId(), record.getOperation(), record.getOperatorId()));
            });
        } catch (Exception e) {
            log.warn("[AuditLog] Failed to write audit log: {}", e.getMessage());
        }
    }
}
