package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.model.entity.ai.AiTask;

import java.util.Map;
import java.util.UUID;

/**
 * 产物采纳上下文：落库目标项目经 X-Active-Project 头解析后传入，不出请求体（C4，详设 3.6.5）。
 */
public record AdoptContext(AiTask task, Map<String, Object> artifact, String action,
                           Map<String, Object> content, String note, UUID targetModuleId, String position,
                           String targetSystemVersion, UUID projectId, UUID operatorId) {
}
