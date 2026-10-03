package io.github.xiaomisum.robotest.service.ai.config;

/**
 * 模型调用回复：token 以响应账单为准（详设 4.3）。
 */
public record AiChatReply(String content, int tokensIn, int tokensOut) {
}
