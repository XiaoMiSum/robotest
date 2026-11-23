package io.github.xiaomisum.robotest.service.ai.config;

import java.util.List;
import java.util.Map;

/**
 * 模型原始响应快照（详设 2.6.3 / 4.2）：随 ai_decision 轨迹事件留存供任务详情下钻。
 * 正文与工具调用参数按原文完整保存，不含 HTTP 报文与密钥。
 */
public record AiRawResponse(String content, List<AiToolCall> toolCalls, String finishReason,
                            Map<String, Object> usage) {

    public AiRawResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Map.of() : Map.copyOf(usage);
    }
}
