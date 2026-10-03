package io.github.xiaomisum.robotest.service.ai.task;

import java.util.Map;
import java.util.UUID;

/**
 * 采纳落库结果：createdId 为生成的业务实体 ID（驳回为 null），adoptedRef 随确认记录回写（详设 2.7）。
 */
public record AdoptOutcome(UUID createdId, Map<String, Object> adoptedRef) {
}
