package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;

import java.util.List;
import java.util.UUID;

/**
 * 单次模型调用请求（系统提示词可为空；media 为多模态随行媒体列表，纯文本调用为空列表）。
 * executionId 为所属执行（详设 2.8 用量归因），任务型调用传入、助手交互等无任务调用为 null。
 * onTick 为长流期间的心跳回调（详设 4.2），任务型调用注入以刷新执行心跳，其余调用为 no-op。
 */
public record AiChatRequest(UUID taskId, UUID executionId, UUID projectId, UUID userId, String promptScene,
                            AiModelConfig model, String systemPrompt, String userPrompt, List<AiChatMedia> media,
                            Runnable onTick) {

    public AiChatRequest {
        media = media == null ? List.of() : List.copyOf(media);
        onTick = onTick == null ? () -> { } : onTick;
    }

    /** 完整构造器（含心跳回调） */
    public AiChatRequest(UUID taskId, UUID executionId, UUID projectId, UUID userId, String promptScene,
                         AiModelConfig model, String systemPrompt, String userPrompt, List<AiChatMedia> media) {
        this(taskId, executionId, projectId, userId, promptScene, model, systemPrompt, userPrompt, media, () -> { });
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

    /** 带媒体与心跳回调的调用（无执行归因，助手长流保活） */
    public AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                         AiModelConfig model, String systemPrompt, String userPrompt, List<AiChatMedia> media,
                         Runnable onTick) {
        this(taskId, null, projectId, userId, promptScene, model, systemPrompt, userPrompt, media, onTick);
    }

    /** 单媒体便捷构造（需求导入等单图场景） */
    public AiChatRequest(UUID taskId, UUID projectId, UUID userId, String promptScene,
                         AiModelConfig model, String systemPrompt, String userPrompt, AiChatMedia media) {
        this(taskId, null, projectId, userId, promptScene, model, systemPrompt, userPrompt,
                media == null ? List.<AiChatMedia>of() : List.of(media));
    }
}
