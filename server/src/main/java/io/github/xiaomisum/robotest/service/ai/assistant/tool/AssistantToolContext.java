package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工具执行上下文：全部取自服务端已校验的请求上下文（详设 3.1 头分层），
 * 工具不得从请求头之外的任何渠道推断作用域——项目维一律以模型显式入参 + 引用校验为准。
 *
 * @param userId      当前用户（进度查询等按人校验的入参）
 * @param workspaceId 活跃工作空间（查询类限权边界）
 * @param attachments 本条消息的页面附件（查询用例时用于聚焦当前文档）
 */
public record AssistantToolContext(
        UUID userId,
        UUID workspaceId,
        List<Map<String, Object>> attachments) {
}
