package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportStatusRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiGitImportService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 导入与解析（GitLab 集成详细设计 3.2）：扫描预览、执行导入与进度查询，上下文经请求头传递（C4）。
 */
@RestController
@RequestMapping("/api/project/git-repos/{repoId}")
public class ApiGitImportController {

    @Resource
    private ApiGitImportService apiGitImportService;

    /** 扫描预览：不落库，返回将创建 / 更新 / 解析失败的清单与汇总 */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<ApiGitImportPreviewRespDTO> preview(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID repoId) {
        return Result.ok(apiGitImportService.preview(loginUser.getActiveProjectId(),
                loginUser.getActiveWorkspaceId(), loginUser.getId(), repoId));
    }

    /** 执行导入：并发闸门同步判定，写库为单事务，进度经 status 轮询 */
    @PostMapping("/imports")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<ApiGitImportStatusRespDTO> imports(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID repoId) {
        return Result.ok(apiGitImportService.imports(loginUser.getActiveProjectId(),
                loginUser.getActiveWorkspaceId(), loginUser.getId(), repoId));
    }

    /** 导入进度：阶段、已处理数、总量 */
    @GetMapping("/imports/status")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<ApiGitImportStatusRespDTO> status(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID repoId) {
        return Result.ok(apiGitImportService.status(loginUser.getActiveProjectId(),
                loginUser.getActiveWorkspaceId(), loginUser.getId(), repoId));
    }
}
