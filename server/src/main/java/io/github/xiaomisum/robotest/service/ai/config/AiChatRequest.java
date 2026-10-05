package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;

import java.util.UUID;

/**
 * 单次模型调用请求（系统提示词可为空；media 为可选多模态随行，纯文本调用为 null）。
 */
public record AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                            AiModelConfig model, String systemPrompt, String userPrompt, AiChatMedia media) {

    /** 无媒体的常规文本调用 */
    public AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                         AiModelConfig model, String systemPrompt, String userPrompt) {
        this(taskId, projectId, userId, promptScene, model, systemPrompt, userPrompt, null);
    }
}
