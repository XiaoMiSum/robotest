package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.admin.LoginReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.PasswordChangeReqDTO;
import io.github.xiaomisum.robotest.service.admin.UserService;
import io.github.xiaomisum.robotest.service.admin.audit.LoginAuditService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;
import xyz.migoo.framework.security.core.authentication.AuthUserDetailsFetcher;
import xyz.migoo.framework.security.core.authentication.AuthUserDetailsFetcher.LoginResult;
import xyz.migoo.framework.web.core.annotation.RateLimit;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthUserDetailsFetcher<LoginUser> authUserDetailsFetcher;
    @Resource
    private UserService userService;
    @Resource
    private LoginAuditService loginAuditService;

    // 账号维度的失败计数由框架登录失败锁定承担（安全规范 6.1）
    @RateLimit(limit = 10, window = 300)
    @PostMapping("/login")
    public Result<LoginResult<LoginUser>> login(@RequestBody @Valid LoginReqDTO reqDTO,
                                                HttpServletRequest request) {
        LoginResult<LoginUser> loginResult = authUserDetailsFetcher.authenticate(
                reqDTO.getIdentifier(), reqDTO.getPassword());
        // 记录登录 IP 供数据概览活跃统计与审计查询消费；写入失败不影响登录
        loginAuditService.recordLogin(loginResult.getUser().getId(),
                loginResult.getUser().getUsername(), request);
        return Result.ok(loginResult);
    }

    @RateLimit(limit = 30, window = 60)
    @PostMapping("/refresh")
    public Result<LoginResult<LoginUser>> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {
        return Result.ok(authUserDetailsFetcher.refreshToken(refreshToken));
    }

    @PostMapping("/permissions")
    public Result<List<String>> getPermissions(
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(loginUser.getPermissionCodes());
    }

    /**
     * 退出登录（认证详细设计 5.1）：分别撤销本次会话的 access 与 refresh Token。
     * 框架 LogoutFilter 只处理 {@code migoo.security.logout-url}（/logout）且仅撤销 access，
     * 业务登出必须撤销双 Token，故由本接口承载。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        revokeToken(bearerToken(authorization));
        revokeToken(refreshToken);
        return Result.ok();
    }

    private String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        int index = authorization.indexOf("Bearer ");
        return index < 0 ? null : authorization.substring(index + 7).trim();
    }

    private void revokeToken(String token) {
        if (token != null && !token.isBlank()) {
            authUserDetailsFetcher.revokeToken(token);
        }
    }

    @RateLimit(limit = 10, window = 300)
    @PostMapping("/change-password")
    public Result<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                       @RequestBody @Valid PasswordChangeReqDTO reqDTO) {
        userService.changePassword(loginUser.getId(), reqDTO.getOldPassword(), reqDTO.getNewPassword());
        return Result.ok();
    }
}
