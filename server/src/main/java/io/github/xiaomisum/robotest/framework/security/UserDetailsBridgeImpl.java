package io.github.xiaomisum.robotest.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.Constants;
import io.github.xiaomisum.robotest.model.entity.SysRole;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.SysUserRole;
import io.github.xiaomisum.robotest.repository.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.SysUserRoleMapper;
import jakarta.annotation.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.security.core.AuthUserDetails;
import xyz.migoo.framework.security.core.authentication.UserDetailsBridge;

import java.util.List;
import java.util.stream.Stream;

@Component
public class UserDetailsBridgeImpl implements UserDetailsBridge {

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private SysRoleMapper roleMapper;

    @Override
    public AuthUserDetails<?, ?> loadByUsername(String username) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .or()
                        .eq(SysUser::getEmail, username)
        );
        return user != null ? toLoginUser(user) : null;
    }

    @Override
    public AuthUserDetails<?, ?> loadByUserId(String userId) {
        SysUser user = userMapper.selectById(userId);
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
        loginUser.setAuthorities(loadAuthorities(user.getId().toString()));
        return loginUser;
    }

    private List<? extends GrantedAuthority> loadAuthorities(String userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(SysUserRole::getUserId, userId);
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<String> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        List<SysRole> roles = roleMapper.selectList(SysRole::getId, roleIds);
        return roles.stream()
                .flatMap(role -> Stream.concat(
                        Stream.of(new SimpleGrantedAuthority(Constants.Auth.ROLE_PREFIX + role.getName())),
                        role.getPermissions() != null
                                ? role.getPermissions().stream().map(SimpleGrantedAuthority::new)
                                : Stream.empty()))
                .distinct()
                .toList();
    }
}
