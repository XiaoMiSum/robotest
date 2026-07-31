package io.github.xiaomisum.robotest.service.ai;

import java.util.List;
import java.util.UUID;

/**
 * AI 网关公共模型集合（调用上下文、消息、调用选项与结果载体）
 */
public final class AiModels {

    private AiModels() {
    }

    /**
     * 调用上下文：审计与限流归属（管理端调用 workspaceId/projectId 为空）。
     *
     * @param modelId 交互式功能可携带的对话模型标识（缺省/失效由网关回退系统默认，4.11）；异步任务与建议类传 null
     */
    public record AiCallContext(UUID userId, UUID workspaceId, UUID projectId, UUID modelId) {

        /** 不指定对话模型（后台任务/建议类，走系统默认模型） */
        public AiCallContext(UUID userId, UUID workspaceId, UUID projectId) {
            this(userId, workspaceId, projectId, null);
        }
    }

    /** OpenAI 兼容消息 */
    public record ChatMessage(String role, String content) {

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }
    }

    /**
     * 同步/流式对话调用选项
     *
     * @param maxTokens          为空不传
     * @param temperature        为空不传
     * @param jsonResponseFormat 请求 response_format: json_object
     * @param readTimeoutMillis  功能级读超时覆盖（为空用默认矩阵）
     */
    public record ChatCallOptions(Integer maxTokens, Double temperature,
                                  boolean jsonResponseFormat, Integer readTimeoutMillis) {

        public static ChatCallOptions defaults() {
            return new ChatCallOptions(null, null, false, null);
        }

        public static ChatCallOptions json() {
            return new ChatCallOptions(null, null, true, null);
        }
    }

    /** 同步对话结果（token 取上游 usage，缺失为空） */
    public record ChatResult(String content, Integer promptTokens, Integer completionTokens, String finishReason) {
    }

    /** Embedding 结果 */
    public record EmbedResult(List<float[]> vectors, Integer promptTokens) {
    }

    /** 流式回调：增量透传 + 结束时携带完整文本与用量 */
    public interface StreamCallbacks {

        void onDelta(String content);

        void onFinish(String fullContent, Integer promptTokens, Integer completionTokens);
    }
}
