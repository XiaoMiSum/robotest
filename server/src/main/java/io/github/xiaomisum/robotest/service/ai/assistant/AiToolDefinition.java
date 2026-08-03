package io.github.xiaomisum.robotest.service.ai.assistant;

import java.util.Map;

/**
 * 全局智能助手工具定义（详细设计 4.1）。
 *
 * @param name              唯一名（snake_case），LLM 工具清单标识
 * @param description       供 LLM 理解的用途描述
 * @param paramsSchema      OpenAI tools 参数 JSON Schema（手写常量）
 * @param readOnly          是否只读（写工具触发确认流程）
 * @param requiredPermission 平台权限码，null 表示仅需登录即可调用
 */
public record AiToolDefinition(
        String name,
        String description,
        Map<String, Object> paramsSchema,
        boolean readOnly,
        String requiredPermission) {
}
