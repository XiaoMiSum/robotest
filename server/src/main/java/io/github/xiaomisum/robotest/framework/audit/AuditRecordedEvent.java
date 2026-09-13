package io.github.xiaomisum.robotest.framework.audit;

import java.util.UUID;

/**
 * 审计记录已落库事件：供 Observer（审计查询/报表/通知）订阅。
 * 由 {@link AuditLogAspect} 在独立事务写入成功后发布，
 * 订阅方不得修改写入语义（REQUIRES_NEW + 脱敏不变量由 Aspect 保证）。
 */
public record AuditRecordedEvent(UUID auditLogId, String entityType,
                                 UUID entityId, String operation, UUID operatorId) {
}