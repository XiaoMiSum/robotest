package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.security.LoginUser;

import java.util.UUID;

/**
 * 任务提交上下文（详设 3.6.2）：带项目范围的输入校验、按范围取资源权限与快照回写
 * 经此取上下文；系统触发无登录态时 loginUser 为 null（权限校验同 checkPermission 口径跳过）。
 */
public record TaskSubmitContext(UUID projectId, UUID workspaceId, UUID userId, LoginUser loginUser) {
}
