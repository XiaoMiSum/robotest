package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitRepoConnectionTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitRepoCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitRepoUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitRepoConnectionTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitRepoRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiGitRepoService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 代码仓库配置（GitLab 集成详细设计 3.1），一项目一仓库，无分页。
 */
@RestController
@RequestMapping("/api/project/git-repos")
public class ApiGitRepoController {

    @Resource
    private ApiGitRepoService apiGitRepoService;

    @GetMapping
    @PreAuthorize("hasAuthority('api-env:view')")
    public Result<ApiGitRepoRespDTO> get(
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(apiGitRepoService.getRepo(loginUser.getActiveProjectId(),
                loginUser.getActiveWorkspaceId(), loginUser.getId()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<ApiGitRepoRespDTO> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiGitRepoCreateReqDTO reqDTO) {
        return Result.ok(apiGitRepoService.createRepo(loginUser.getActiveProjectId(),
                loginUser.getActiveWorkspaceId(), loginUser.getId(), reqDTO));
    }

    @PutMapping("/{repoId}")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID repoId,
            @RequestBody @Valid ApiGitRepoUpdateReqDTO reqDTO) {
        apiGitRepoService.updateRepo(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(),
                loginUser.getId(), repoId, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/{repoId}")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID repoId) {
        apiGitRepoService.deleteRepo(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(),
                loginUser.getId(), repoId);
        return Result.ok(true);
    }

    // ========== 连通性测试（3.1，不依赖 repoId，body 缺省字段回落已存配置，不落库） ==========

    @PostMapping("/connections/test")
    @PreAuthorize("hasAuthority('api-env:view')")
    public Result<ApiGitRepoConnectionTestRespDTO> testConnection(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiGitRepoConnectionTestReqDTO reqDTO) {
        return Result.ok(apiGitRepoService.testConnection(loginUser.getActiveProjectId(),
                loginUser.getActiveWorkspaceId(), loginUser.getId(), reqDTO));
    }

}
