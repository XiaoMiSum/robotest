package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.SysPermission;
import io.github.xiaomisum.robotest.model.entity.SysRole;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.SysUserRole;
import io.github.xiaomisum.robotest.repository.SysPermissionMapper;
import io.github.xiaomisum.robotest.repository.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.SysUserRoleMapper;
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
        loginUser.setPermissions(buildPermissionCodes(roles));
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
                    if (Boolean.TRUE.equals(role.getFullAccess())) {
                        return Stream.concat(roleAuth,
                                getAllScopePermissions("global").stream().map(SimpleGrantedAuthority::new));
                    }
                    Stream<GrantedAuthority> permAuth = role.getPermissions() != null
                            ? role.getPermissions().stream().map(SimpleGrantedAuthority::new)
                            : Stream.empty();
                    return Stream.concat(roleAuth, permAuth);
                })
                .distinct()
                .toList();
    }

    private List<String> buildPermissionCodes(List<SysRole> roles) {
        return roles.stream()
                .flatMap(role -> {
                    if (Boolean.TRUE.equals(role.getFullAccess())) {
                        return Stream.concat(
                                Stream.of(Constants.Auth.ROLE_PREFIX + role.getName()),
                                getAllScopePermissions("global").stream());
                    }
                    Stream<String> permCodes = role.getPermissions() != null
                            ? role.getPermissions().stream()
                            : Stream.empty();
                    return Stream.concat(
                            Stream.of(Constants.Auth.ROLE_PREFIX + role.getName()),
                            permCodes);
                })
                .distinct()
                .toList();
    }

    private List<String> getAllScopePermissions(String scope) {
        return permissionMapper.selectList(
                new LambdaQueryWrapperX<SysPermission>()
                        .eq(SysPermission::getScope, scope)
                        .ne(SysPermission::getParentCode, null))
                .stream().map(SysPermission::getCode).toList();
    }
}
