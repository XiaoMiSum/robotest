package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;

import java.util.List;
import java.util.UUID;

/**
 * 单次模型调用请求（系统提示词可为空；media 为多模态随行媒体列表，纯文本调用为空列表）。
 * executionId 为所属执行（详设 2.8 用量归因），任务型调用传入、助手交互等无任务调用为 null。
 */
public record AiChatRequest(UUID taskId, UUID executionId, UUID projectId, UUID userId, String promptScene,
                            AiModelConfig model, String systemPrompt, String userPrompt, List<AiChatMedia> media) {

    public AiChatRequest {
        media = media == null ? List.of() : List.copyOf(media);
    }

    /** 任务执行调用（带 execution 归因，执行核使用） */
    public AiChatRequest(UUID taskId, UUID executionId, UUID projectId, UUID userId, String promptScene,
                         AiModelConfig model, String systemPrompt, String userPrompt) {
        this(taskId, executionId, projectId, userId, promptScene, model, systemPrompt, userPrompt, List.of());
    }

    /** 无媒体的常规文本调用（无执行归因，如助手交互） */
    public AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                         AiModelConfig model, String systemPrompt, String userPrompt) {
        this(taskId, null, projectId, userId, promptScene, model, systemPrompt, userPrompt, List.of());
    }

    /** 带媒体的调用（无执行归因） */
    public AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                         AiModelConfig model, String systemPrompt, String userPrompt, List<AiChatMedia> media) {
        this(taskId, null, projectId, userId, promptScene, model, systemPrompt, userPrompt, media);
    }

    /** 单媒体便捷构造（需求导入等单图场景） */
    public AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                         AiModelConfig model, String systemPrompt, String userPrompt, AiChatMedia media) {
        this(taskId, null, projectId, userId, promptScene, model, systemPrompt, userPrompt,
                media == null ? List.<AiChatMedia>of() : List.of(media));
    }
}
