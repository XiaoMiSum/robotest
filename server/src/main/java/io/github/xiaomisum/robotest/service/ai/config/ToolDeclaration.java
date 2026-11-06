package io.github.xiaomisum.robotest.service.ai.config;

import java.util.Map;

/**
 * 中性工具声明（任务域执行核用，详设 4.2）：仅承载序列化进模型请求的三要素，
 * 不携带助手循环的类别语义——执行权始终在服务端循环（声明型回调不可执行）。
 *
 * @param name        工具标识（即 ai_task_tool.tool_code，snake_case）
 * @param description 供模型理解的用途与调用时机描述
 * @param paramsSchema 参数 JSON Schema（OpenAI tools 入参结构）
 */
public record ToolDeclaration(String name, String description, Map<String, Object> paramsSchema) {
}
