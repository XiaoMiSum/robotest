package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabExecutableImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabRepoSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabExecutableImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabFileTreeNodeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabRepoListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabRepoTestConnectionRespDTO;
import io.github.xiaomisum.robotest.service.apitest.GitLabExecutableImportService;
import io.github.xiaomisum.robotest.service.apitest.GitLabRepoService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project/gitlab-repos")
public class GitLabRepoController {

    @Resource
    private GitLabRepoService gitLabRepoService;

    @Resource
    private GitLabExecutableImportService gitLabExecutableImportService;

    @GetMapping
    @PreAuthorize("hasAuthority('api-gitlab:view')")
    public Result<PageResult<GitLabRepoListItemRespDTO>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword) {
        PageParam pageParam = new PageParam() {{
            setPageNo(pageNo);
            setPageSize(pageSize);
        }};
        return Result.ok(gitLabRepoService.fetchPage(projectId, workspaceId, loginUser.getId(), keyword, pageParam));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('api-gitlab:edit')")
    public Result<UUID> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid GitLabRepoSaveReqDTO reqDTO) {
        return Result.ok(gitLabRepoService.create(projectId, workspaceId, loginUser.getId(), reqDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('api-gitlab:edit')")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid GitLabRepoSaveReqDTO reqDTO) {
        gitLabRepoService.update(projectId, workspaceId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('api-gitlab:edit')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        gitLabRepoService.delete(projectId, workspaceId, loginUser.getId(), id);
        return Result.ok(true);
    }

    @PostMapping("/{id}/test-connection")
    @PreAuthorize("hasAuthority('api-gitlab:view')")
    public Result<GitLabRepoTestConnectionRespDTO> testConnection(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(gitLabRepoService.testConnection(projectId, workspaceId, loginUser.getId(), id));
    }

    @GetMapping("/{id}/branches")
    @PreAuthorize("hasAuthority('api-gitlab:view')")
    public Result<List<String>> listBranches(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(gitLabRepoService.listBranches(projectId, workspaceId, loginUser.getId(), id));
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasAuthority('api-gitlab:view')")
    public Result<GitLabFileTreeNodeRespDTO> browseFiles(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestParam(required = false) String path) {
        return Result.ok(gitLabExecutableImportService.browseFiles(
                projectId, workspaceId, loginUser.getId(), id, path));
    }

    @PostMapping("/{id}/executable-import")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<GitLabExecutableImportRespDTO> importExecutable(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid GitLabExecutableImportReqDTO reqDTO) {
        return Result.ok(gitLabExecutableImportService.importExecutable(
                projectId, workspaceId, loginUser.getId(), id, reqDTO));
    }

    @GetMapping("/{id}/executable-import/latest")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<GitLabExecutableImportRespDTO> fetchLatestImport(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(gitLabExecutableImportService.fetchLatestImport(
                projectId, workspaceId, loginUser.getId(), id));
    }
}
