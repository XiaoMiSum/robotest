package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.LoginReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.LoginRespDTO;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.SysPermissionMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.model.entity.SysPermission;
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

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthUserDetailsFetcher<LoginUser> authUserDetailsFetcher;
    @Resource
    private SysPermissionMapper permissionMapper;
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
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader(value = "X-Active-Workspace", required = false) String workspaceId) {
        List<String> permissions = new ArrayList<>(loginUser.getPermissions());
        if (workspaceId != null && !workspaceId.isBlank()) {
            List<String> wsPermissions = permissionMapper.selectList(
                    new LambdaQueryWrapperX<SysPermission>()
                            .eq(SysPermission::getScope, "workspace")
                            .ne(SysPermission::getParentCode, null))
                    .stream().map(SysPermission::getCode).toList();
            permissions.addAll(wsPermissions);
        }
        return Result.ok(permissions.stream().distinct().toList());
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
