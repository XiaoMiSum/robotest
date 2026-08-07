package io.github.xiaomisum.robotest.service.ai.model;

import java.util.List;
import java.util.Map;
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

    /**
     * OpenAI 兼容消息（支持 tool_calls 序列化与 tool_call_id 回填）
     *
     * @param role      角色（system/user/assistant/tool）
     * @param content   文本内容（tool 消息为工具执行结果 JSON）
     * @param toolCalls assistant 消息发起的工具调用载荷，非 assistant 消息为 null
     * @param toolCallId tool 消息对应的调用 ID，非 tool 消息为 null
     */
    public record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

        /** 兼容旧的 2 参数构造 */
        public ChatMessage(String role, String content) {
            this(role, content, null, null);
        }

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        /** assistant 消息（可能携带工具调用） */
        public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", content, toolCalls, null);
        }

        /** tool 结果消息（tool_call_id 回填 LLM） */
        public static ChatMessage tool(String toolCallId, String content) {
            return new ChatMessage("tool", content, null, toolCallId);
        }
    }

    /**
     * LLM 工具调用载荷（assistant 消息中 tool_calls 数组元素）
     *
     * @param id        调用 ID（tool_call_id，LLM 生成，需回填 tool 消息）
     * @param name      工具名（snake_case，与 ToolRegistry 注册名匹配）
     * @param arguments 参数（JSON 字符串经 Jackson 解析为 Map）
     */
    public record ToolCall(String id, String name, Map<String, Object> arguments) {
    }

    /**
     * 工具定义（OpenAI tools 数组元素，供 LLM 选择调用）
     *
     * @param name        唯一名（snake_case）
     * @param description 用途描述（供 LLM 决策）
     * @param parameters  参数 JSON Schema
     */
    public record ToolDefinition(String name, String description, Map<String, Object> parameters) {
    }

    /**
     * 同步/流式对话调用选项
     *
     * @param maxTokens          为空不传
     * @param temperature        为空不传
     * @param jsonResponseFormat 请求 response_format: json_object
     * @param readTimeoutMillis  功能级读超时覆盖（为空用默认矩阵）
     * @param tools              工具定义列表，为空不传（Function Calling 场景注入）
     */
    public record ChatCallOptions(Integer maxTokens, Double temperature,
                                  boolean jsonResponseFormat, Integer readTimeoutMillis,
                                  List<ToolDefinition> tools) {

        /** 兼容旧的 4 参数构造（无 tools） */
        public ChatCallOptions(Integer maxTokens, Double temperature,
                               boolean jsonResponseFormat, Integer readTimeoutMillis) {
            this(maxTokens, temperature, jsonResponseFormat, readTimeoutMillis, null);
        }

        public static ChatCallOptions defaults() {
            return new ChatCallOptions(null, null, false, null);
        }

        public static ChatCallOptions json() {
            return new ChatCallOptions(null, null, true, null);
        }

        /** 构造携带工具定义的选项 */
        public static ChatCallOptions withTools(List<ToolDefinition> tools) {
            return new ChatCallOptions(null, null, false, null, tools);
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

    /**
     * 工具流式回调：在 StreamCallbacks 基础上增加工具调用结果通知。
     * Function Calling 场景使用此回调接收累积的 tool_calls。
     */
    public interface ToolStreamCallbacks extends StreamCallbacks {

        /**
         * 流式结束后通知工具调用列表（空列表表示纯文本响应，无工具调用）。
         * 该方法在 onFinish 之前调用。
         */
        void onToolCalls(List<ToolCall> toolCalls);
    }
}
