package io.github.xiaomisum.robotest.service.ai.config;

/**
 * 模型返回的单个工具调用（详设 4.1）：arguments 为模型生成的参数 JSON 原文，
 * 由循环解析为入参后分发（解析失败按意图解析失败处理）。
 */
public record AiToolCall(String id, String name, String arguments) {
}
