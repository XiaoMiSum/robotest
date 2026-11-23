package io.github.xiaomisum.robotest.service.ai.config;

import java.util.List;

/**
 * 模型调用回复：token 以响应账单为准（详设 4.3）；
 * toolCalls 为本跳模型请求的工具调用（无则纯文本跳，详设 4.1）。
 */
public record AiChatReply(String content, int tokensIn, int tokensOut, List<AiToolCall> toolCalls,
                          AiRawResponse rawResponse) {

    public AiChatReply {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** 纯文本调用（无工具声明路径的既有形态） */
    public AiChatReply(String content, int tokensIn, int tokensOut) {
        this(content, tokensIn, tokensOut, List.of(), null);
    }

    /** 工具调用路径的既有形态（无原始响应采集） */
    public AiChatReply(String content, int tokensIn, int tokensOut, List<AiToolCall> toolCalls) {
        this(content, tokensIn, tokensOut, toolCalls, null);
    }
}
