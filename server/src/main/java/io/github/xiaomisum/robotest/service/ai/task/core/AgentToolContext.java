package io.github.xiaomisum.robotest.service.ai.task.core;

import java.util.UUID;

/**
 * 工具执行上下文（总册 3.6 装配口径）：作用域标识经请求头传递（C4，不出 URL），
 * 异步执行期由任务提交上下文固化——工具实现按 workspaceId / projectId 做限权过滤。
 *
 * @param userId      执行发起人（工具以该身份取数与鉴权）
 * @param workspaceId 活跃工作空间（RAG 限权过滤依据）
 * @param projectId   活跃项目（null = 不限项目的个人任务）
 */
public record AgentToolContext(UUID userId, UUID workspaceId, UUID projectId) {
}
