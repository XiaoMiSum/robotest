package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabPipelineTriggerReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabPipelineReportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabPipelineRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabPipelineStatusRespDTO;
import io.github.xiaomisum.robotest.service.apitest.GitLabPipelineService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/project/gitlab-repos")
public class GitLabPipelineController {

    @Resource
    private GitLabPipelineService pipelineService;

    @PostMapping("/{id}/trigger-pipeline")
    @PreAuthorize("hasAuthority('api-scene:pipeline')")
    public Result<GitLabPipelineRespDTO> triggerPipeline(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid GitLabPipelineTriggerReqDTO reqDTO) {
        return Result.ok(pipelineService.triggerPipeline(projectId, workspaceId, loginUser.getId(), id, reqDTO));
    }

    @GetMapping("/executions/{executionId}/pipeline-status")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<GitLabPipelineStatusRespDTO> queryPipelineStatus(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID executionId) {
        return Result.ok(pipelineService.queryPipelineStatus(projectId, workspaceId, loginUser.getId(), executionId));
    }

    @PostMapping("/executions/{executionId}/pull-report")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<GitLabPipelineReportRespDTO> pullReport(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID executionId) {
        return Result.ok(pipelineService.pullReport(projectId, workspaceId, loginUser.getId(), executionId));
    }
}
