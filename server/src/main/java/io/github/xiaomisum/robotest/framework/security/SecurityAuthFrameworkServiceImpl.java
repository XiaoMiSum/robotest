package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.security.core.service.SecurityAuthFrameworkService;
import xyz.migoo.framework.security.core.service.dto.Authenticated;

@Component
public class SecurityAuthFrameworkServiceImpl implements SecurityAuthFrameworkService<RobotestUserDetails> {

    @Resource
    private UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userService.getUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return toUserDetails(user);
    }

    @Override
    public Authenticated<RobotestUserDetails> authenticate(String username, String password) {
        // loadUserByUsername 会校验用户存在性
        // 这里由框架的 JWT 机制处理 token 生成
        // 实际 authenticate 由框架内部调用 loadUserByUsername + password check
        return null;
    }

    @Override
    public RobotestUserDetails verifyToken(String accessToken) {
        // 由框架 JWT filter 内部处理
        return null;
    }

    @Override
    public Authenticated<RobotestUserDetails> refreshToken(String refreshToken) {
        return null;
    }

    @Override
    public void clean(String accessToken) {
        // Token 清理逻辑
    }

    private RobotestUserDetails toUserDetails(SysUser user) {
        RobotestUserDetails details = new RobotestUserDetails();
        details.setId(user.getId());
        details.setUsername(user.getUsername());
        details.setPassword(user.getPasswordHash());
        details.setName(user.getUsername());
        details.setEnabled("active".equals(user.getStatus()));
        return details;
    }
}
