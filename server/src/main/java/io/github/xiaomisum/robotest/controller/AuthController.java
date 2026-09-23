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

    @PostMapping("/refresh")
    public Result<LoginResult<LoginUser>> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {
        LoginResult<LoginUser> loginResult = authUserDetailsFetcher.refreshToken(refreshToken);
        return Result.ok(loginResult);
    }

    @PostMapping("/permissions")
    public Result<List<String>> getPermissions(
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(loginUser.getPermissionCodes());
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                       @RequestBody @Valid PasswordChangeReqDTO reqDTO) {
        userService.changePassword(loginUser.getId(), reqDTO.getOldPassword(), reqDTO.getNewPassword());
        return Result.ok();
    }
}
