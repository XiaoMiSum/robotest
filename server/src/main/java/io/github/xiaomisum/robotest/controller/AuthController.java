package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.LoginReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.LoginRespDTO;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.security.core.authentication.AuthUserDetailsFetcher;
import xyz.migoo.framework.security.core.authentication.AuthUserDetailsFetcher.LoginResult;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthUserDetailsFetcher<LoginUser> authUserDetailsFetcher;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;

    @PostMapping("/login")
    public Result<LoginRespDTO> login(@RequestBody @Valid LoginReqDTO reqDTO) {
        LoginResult<LoginUser> loginResult = authUserDetailsFetcher.authenticate(
                reqDTO.getIdentifier(), reqDTO.getPassword());
        return Result.ok(toLoginRespDTO(loginResult));
    }

    @PostMapping("/refresh")
    public Result<LoginRespDTO> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {
        LoginResult<LoginUser> loginResult = authUserDetailsFetcher.refreshToken(refreshToken);
        return Result.ok(toLoginRespDTO(loginResult));
    }

    @PostMapping("/permissions")
    public Result<List<String>> getPermissions(
            @AuthenticationPrincipal LoginUser loginUser) {
        // getAuthorities() 合并了系统权限 + WorkspaceRoleInterceptor 注入的工作空间权限
        List<String> permissions = loginUser.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .filter(auth -> !auth.startsWith("ROLE_"))
                .distinct()
                .toList();
        return Result.ok(permissions);
    }

    private LoginRespDTO toLoginRespDTO(LoginResult<LoginUser> loginResult) {
        LoginUser user = loginResult.getUserInfo();
        boolean hasWorkspace = workspaceUserMapper.selectCount(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, user.getId())) > 0;
        user.setHasWorkspace(hasWorkspace);
        return LoginRespDTO.builder()
                .accessToken(loginResult.getAccessToken())
                .refreshToken(loginResult.getRefreshToken())
                .accessExpiry(loginResult.getAccessExpiry() != null ? loginResult.getAccessExpiry().toString() : null)
                .refreshExpiry(loginResult.getRefreshExpiry() != null ? loginResult.getRefreshExpiry().toString() : null)
                .user(user)
                .build();
    }
}
