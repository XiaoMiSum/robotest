package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.admin.PermissionScope.SYSTEM;

/**
 * 系统级权限码聚合：user_role → role → permissions 的集合。
 *
 * <p>与 {@code RoleServiceImpl.getUserPermissionCodes} 现状逻辑逐行等价，
 * 重构后改为唯一聚集点；该方法已由 {@code RoleServiceImpl} 委托到本组件。</p>
 */
@Component
public class SystemPermissionChecker implements PermissionChecker {

    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private SysRoleMapper roleMapper;

    @Override
    public Set<String> codes(UUID userId, UUID workspaceId) {
        List<SysUserRole> userRoles = userRoleMapper.listByUserId(userId);
        if (userRoles.isEmpty()) {
            return Set.of();
        }

        List<UUID> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = roleMapper.listByIds(roleIds);

        return roles.stream()
                .flatMap(role -> {
                    List<String> perms = role.getPermissions() != null ? role.getPermissions() : List.of();
                    return perms.stream();
                })
                .collect(Collectors.toSet());
    }

    public PermissionScope scope() {
        return SYSTEM;
    }
}