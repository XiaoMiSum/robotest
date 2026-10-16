package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import java.util.Map;

/**
 * 工具定义（详设 4.1 固化清单）：服务端注册与模型声明共用同一份定义，
 * paramsSchema 为 OpenAI tools 参数 JSON Schema（手写，经 ToolSchemas 构造）。
 *
 * @param name        唯一名（snake_case）；变更类与 intent kind 同名
 * @param description 供模型理解的用途与调用时机描述
 * @param paramsSchema 参数 JSON Schema
 * @param category    类别，决定循环行为
 */
public record AssistantToolDefinition(
        String name,
        String description,
        Map<String, Object> paramsSchema,
        ToolCategory category) {
}
