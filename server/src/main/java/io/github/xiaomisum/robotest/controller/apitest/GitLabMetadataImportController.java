package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabSyncConfigReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabTestScopeSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabMetadataImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabMetadataListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabSyncConfigRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabSyncHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabTestScopeRespDTO;
import io.github.xiaomisum.robotest.service.apitest.GitLabMetadataImportService;
import io.github.xiaomisum.robotest.service.apitest.GitLabTestScopeService;
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
public class GitLabMetadataImportController {

    @Resource
    private GitLabMetadataImportService metadataImportService;

    @Resource
    private GitLabTestScopeService testScopeService;

    @PostMapping("/{id}/metadata-import")
    @PreAuthorize("hasAuthority('api-scene:import')")
    public Result<GitLabMetadataImportRespDTO> importMetadata(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(metadataImportService.importMetadata(projectId, workspaceId, loginUser.getId(), id));
    }

    @GetMapping("/{id}/metadata")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<PageResult<GitLabMetadataListItemRespDTO>> listMetadata(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "isExecutable", required = false) Boolean isExecutable,
            @RequestParam(value = "keyword", required = false) String keyword) {
        PageParam pageParam = new PageParam() {{
            setPageNo(pageNo);
            setPageSize(pageSize);
        }};
        return Result.ok(metadataImportService.fetchMetadataPage(projectId, workspaceId, loginUser.getId(),
                id, isExecutable, keyword, pageParam));
    }

    @PostMapping("/{id}/sync-metadata")
    @PreAuthorize("hasAuthority('api-scene:import')")
    public Result<GitLabMetadataImportRespDTO> syncMetadata(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(metadataImportService.syncMetadata(projectId, workspaceId, loginUser.getId(), id));
    }

    @GetMapping("/{id}/sync-history")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<List<GitLabSyncHistoryItemRespDTO>> listSyncHistory(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(metadataImportService.fetchSyncHistory(projectId, workspaceId, loginUser.getId(), id));
    }

    @GetMapping("/{id}/sync-config")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<GitLabSyncConfigRespDTO> getSyncConfig(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(metadataImportService.fetchSyncConfig(projectId, workspaceId, loginUser.getId(), id));
    }

    @PutMapping("/{id}/sync-config")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> updateSyncConfig(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid GitLabSyncConfigReqDTO config) {
        return Result.ok(metadataImportService.updateSyncConfig(projectId, workspaceId, loginUser.getId(), id, config));
    }

    @GetMapping("/{id}/test-scope")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<List<GitLabTestScopeRespDTO>> listTestScope(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(testScopeService.fetchScopeList(projectId, workspaceId, loginUser.getId(), id));
    }

    @PutMapping("/{id}/test-scope")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> saveTestScope(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid GitLabTestScopeSaveReqDTO reqDTO) {
        reqDTO.setRepositoryId(id);
        return Result.ok(testScopeService.saveScopeList(projectId, workspaceId, loginUser.getId(), reqDTO));
    }
}
