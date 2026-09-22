package io.github.xiaomisum.robotest.framework.task;

import java.util.Map;
import java.util.UUID;

/**
 * 任务分发上下文 — 统一各任务类型的入参契约。
 * taskId/projectId/triggerType 为必填；extension 承载任务类型特有的参数。
 */
public record TaskDispatchContext(
        UUID taskId,
        UUID projectId,
        UUID userId,
        String triggerType,
        Map<String, Object> extension
) {
    public static TaskDispatchContext of(UUID taskId, UUID projectId, String triggerType) {
        return new TaskDispatchContext(taskId, projectId, null, triggerType, Map.of());
    }

    public static TaskDispatchContext of(UUID taskId, UUID projectId, UUID userId, String triggerType) {
        return new TaskDispatchContext(taskId, projectId, userId, triggerType, Map.of());
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return extension != null ? (T) extension.get(key) : null;
    }
}
