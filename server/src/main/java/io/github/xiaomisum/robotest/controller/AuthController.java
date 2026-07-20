package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.LoginReqDTO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;
import xyz.migoo.framework.security.core.authentication.AuthUserDetailsFetcher;
import xyz.migoo.framework.security.core.authentication.AuthUserDetailsFetcher.LoginResult;

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
}
