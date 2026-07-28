package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.LoginReqDTO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;
import xyz.migoo.framework.security.core.authentication.AuthUserDetailsFetcher;
import xyz.migoo.framework.security.core.authentication.AuthUserDetailsFetcher.LoginResult;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthUserDetailsFetcher<LoginUser> authUserDetailsFetcher;

    @PostMapping("/login")
    public Result<LoginResult<LoginUser>> login(@RequestBody @Valid LoginReqDTO reqDTO) {
        LoginResult<LoginUser> loginResult = authUserDetailsFetcher.authenticate(
                reqDTO.getIdentifier(), reqDTO.getPassword());
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
        // getAuthorities() 合并了系统权限 + WorkspaceRoleInterceptor 注入的工作空间权限
        List<String> permissions = loginUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                .filter(auth -> !auth.startsWith("ROLE_"))
                .distinct()
                .toList();
        return Result.ok(permissions);
    }
}
