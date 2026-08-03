package io.github.xiaomisum.robotest.service.ai.assistant;

import java.util.Map;
import java.util.UUID;

/**
 * 工具调用上下文（详细设计 4.1/4.4）。
 *
 * @param userId     当前用户（工具以 LoginUser 走 Service 层，权限/审计与人工操作一致）
 * @param workspaceId 当前工作空间（空间内数据隔离）
 * @param pageContext 页面上下文桥注入 {projectId, documentId, selectedNodeId}，可空
 */
public record AiToolContext(UUID userId, UUID workspaceId, Map<String, Object> pageContext) {
}
