package io.github.xiaomisum.robotest.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.entity.SysRole;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.SysUserRole;
import io.github.xiaomisum.robotest.repository.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.SysUserRoleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import xyz.migoo.framework.common.util.json.JsonUtils;
import xyz.migoo.framework.security.core.AuthUserDetails;
import xyz.migoo.framework.security.core.authentication.UserDetailsBridge;

import java.util.*;
import java.util.stream.Collectors;
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
        if (user == null) {
            return null;
        }
        return toLoginUser(user);
    }

    @Override
    public AuthUserDetails<?, ?> loadByUserId(String userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return toLoginUser(user);
    }

    private LoginUser toLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId().toString());
        loginUser.setUsername(user.getUsername());
        loginUser.setName(user.getUsername());
        loginUser.setEmail(user.getEmail());
        loginUser.setPassword(user.getPasswordHash());
        loginUser.setEnabled("active".equals(user.getStatus()));

        List<SysUserRole> userRoles = userRoleMapper.selectList(SysUserRole::getUserId, user.getId());
        if (!userRoles.isEmpty()) {
            List<String> roleIds = userRoles.stream()
                    .map(SysUserRole::getRoleId)
                    .toList();
            List<SysRole> roles = roleMapper.selectList(SysRole::getId, roleIds);
            loginUser.setRoleNames(roles.stream()
                    .map(SysRole::getName)
                    .collect(Collectors.toSet()));
            loginUser.setPermissionCodes(roles.stream()
                    .flatMap(role -> {
                        List<String> perms = JsonUtils.parseObject(role.getPermissions(),
                                new TypeReference<List<String>>() {});
                        return perms != null ? perms.stream() : Stream.empty();
                    })
                    .distinct()
                    .collect(Collectors.toSet()));
        } else {
            loginUser.setRoleNames(Collections.emptySet());
            loginUser.setPermissionCodes(Collections.emptySet());
        }

        return loginUser;
    }
}
