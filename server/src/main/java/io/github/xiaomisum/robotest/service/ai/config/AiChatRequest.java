package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;

import java.util.List;
import java.util.UUID;

/**
 * 单次模型调用请求（系统提示词可为空；media 为多模态随行媒体列表，纯文本调用为空列表）。
 */
public record AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                            AiModelConfig model, String systemPrompt, String userPrompt, List<AiChatMedia> media) {

    public AiChatRequest {
        media = media == null ? List.of() : List.copyOf(media);
    }

    /** 无媒体的常规文本调用 */
    public AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                         AiModelConfig model, String systemPrompt, String userPrompt) {
        this(taskId, projectId, userId, promptScene, model, systemPrompt, userPrompt, List.of());
    }

    /** 单媒体便捷构造（需求导入等单图场景） */
    public AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                         AiModelConfig model, String systemPrompt, String userPrompt, AiChatMedia media) {
        this(taskId, projectId, userId, promptScene, model, systemPrompt, userPrompt,
                media == null ? List.<AiChatMedia>of() : List.of(media));
    }
}
