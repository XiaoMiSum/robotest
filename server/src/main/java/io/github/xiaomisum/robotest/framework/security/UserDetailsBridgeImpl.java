package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.admin.SysPermissionMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.security.core.AuthUserDetails;
import xyz.migoo.framework.security.core.authentication.UserDetailsBridge;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class UserDetailsBridgeImpl implements UserDetailsBridge {

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private SysRoleMapper roleMapper;
    @Resource
    private SysPermissionMapper permissionMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;

    @Override
    public AuthUserDetails<?, ?> loadByUsername(String username) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapperX<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .or()
                        .eq(SysUser::getEmail, username)
        );
        return user != null ? toLoginUser(user) : null;
    }

    @Override
    public AuthUserDetails<?, ?> loadByUserId(String userId) {
        SysUser user = userMapper.selectById(UUID.fromString(userId));
        return user != null ? toLoginUser(user) : null;
    }

    private LoginUser toLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setName(user.getUsername());
        loginUser.setEmail(user.getEmail());
        loginUser.setPassword(user.getPasswordHash());
        loginUser.setEnabled(Constants.Status.ACTIVE.equals(user.getStatus()));
        List<SysRole> roles = loadRoles(user.getId());
        loginUser.setAuthorities(buildAuthorities(roles));
        boolean hasWorkspace = workspaceUserMapper.selectCount(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, user.getId())) > 0;
        loginUser.setHasWorkspace(hasWorkspace);
        return loginUser;
    }

    private List<SysRole> loadRoles(UUID userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapperX<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<UUID> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        return roleMapper.selectList(
                new LambdaQueryWrapperX<SysRole>().in(SysRole::getId, roleIds));
    }

    private List<? extends GrantedAuthority> buildAuthorities(List<SysRole> roles) {
        return roles.stream()
                .flatMap(role -> {
                    Stream<GrantedAuthority> roleAuth = Stream.of(
                            new SimpleGrantedAuthority(Constants.Auth.ROLE_PREFIX + role.getName()));
                    Stream<GrantedAuthority> permAuth = role.getPermissions() != null
                            ? role.getPermissions().stream().map(SimpleGrantedAuthority::new)
                            : Stream.empty();
                    return Stream.concat(roleAuth, permAuth);
                })
                .distinct()
                .toList();
    }
}
