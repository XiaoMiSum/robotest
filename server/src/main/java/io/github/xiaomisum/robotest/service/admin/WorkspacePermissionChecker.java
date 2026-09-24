package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.github.xiaomisum.robotest.service.admin.PermissionScope.WORKSPACE;

/**
 * 工作区级权限码聚合：workspace_user → 工作区角色 → permissions 的集合。
 *
 * <p>与 {@code WorkspaceRoleInterceptor} 注入 {@code workspaceAuthorities} 的
 * 权限码部分一致（不含 ROLE_ 前缀）；workspaceId 必填，为空视同无权限。</p>
 */
@Component
public class WorkspacePermissionChecker implements PermissionChecker {

    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private SysRoleMapper roleMapper;

    @Override
    public Set<String> codes(UUID userId, UUID workspaceId) {
        if (workspaceId == null) {
            return Set.of();
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null || workspaceUser.getWorkspaceRole() == null) {
            return Set.of();
        }

        SysRole role = roleMapper.selectById(workspaceUser.getWorkspaceRole());
        if (role == null) {
            return Set.of();
        }

        List<String> perms = role.getPermissions() != null ? role.getPermissions() : List.of();
        return Set.copyOf(perms);
    }

    public PermissionScope scope() {
        return WORKSPACE;
    }
}