package io.github.xiaomisum.robotest.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.security.core.AuthUserDetails;
import xyz.migoo.framework.security.core.authentication.UserDetailsBridge;

@Component
public class UserDetailsBridgeImpl implements UserDetailsBridge {

    @Resource
    private SysUserMapper userMapper;

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
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setName(user.getUsername());
        loginUser.setEmail(user.getEmail());
        loginUser.setPassword(user.getPasswordHash());
        loginUser.setEnabled("active".equals(user.getStatus()));
        return loginUser;
    }
}
