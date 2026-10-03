package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;

import java.util.UUID;

/**
 * 单次模型调用请求（系统提示词可为空）。
 */
public record AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                            AiModelConfig model, String systemPrompt, String userPrompt) {
}
