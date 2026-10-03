package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiChatRequest;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

/**
 * 任务执行上下文：handler 经此上报进度、调用模型、解析提示词，不得持有 Security 上下文（异步线程无登录态）。
 */
@Getter
public class TaskExecutionContext {

    private final UUID taskId;
    private final UUID projectId;
    private final UUID workspaceId;
    private final UUID userId;
    /** 提示词场景（等于任务 type），用量按场景归因 */
    private final String promptScene;
    /** 提交时解析并回写的模型（4.1），执行期不随默认模型变更漂移 */
    private final AiModelConfig model;
    private final Map<String, Object> input;

    private final ProgressReporter progress;
    private final AiModelClient modelClient;
    private final AiPromptService promptService;

    public TaskExecutionContext(UUID taskId, UUID projectId, UUID workspaceId, UUID userId, String promptScene,
            AiModelConfig model, Map<String, Object> input, ProgressReporter progress, AiModelClient modelClient,
            AiPromptService promptService) {
        this.taskId = taskId;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.promptScene = promptScene;
        this.model = model;
        this.input = input;
        this.progress = progress;
        this.modelClient = modelClient;
        this.promptService = promptService;
    }

    public void report(int percent, String phase) {
        progress.report(percent, phase);
    }

    /** 单次模型调用（自动短重试与用量记录由客户端负责） */
    public AiChatReply chat(String systemPrompt, String userPrompt) {
        return modelClient.chat(new AiChatRequest(taskId, projectId, userId, promptScene, model,
                systemPrompt, userPrompt));
    }

    /** 场景提示词：自定义行优先，回落 handler 内置默认（详设 3.5） */
    public String prompt(String builtInDefault) {
        return promptService.resolve(promptScene, builtInDefault);
    }
}
