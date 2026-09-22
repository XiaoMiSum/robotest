package io.github.xiaomisum.robotest.service.admin;

import java.util.Set;
import java.util.UUID;

/**
 * 权限裁决唯一入口：集中"用户 × 作用域"的权限码查询与授权校验。
 *
 * <p>SYSTEM 作用域聚合 {@code SysRole.permissions}（与 {@code RoleServiceImpl.getUserPermissionCodes}
 * 现状语义一致）；WORKSPACE 作用域聚合用户在工作区归属角色的权限码（与
 * {@code WorkspaceRoleInterceptor} 注入的 {@code workspaceAuthorities} 一致）。
 * 后续若调整任一聚集链路，必须以本接口为唯一实现点。</p>
 */
public interface PermissionFacade {

    Set<String> permissionsOf(UUID userId, PermissionScope scope, UUID workspaceId);
}