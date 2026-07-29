package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceDefaultProjectReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceContextRespDTO;
import io.github.xiaomisum.robotest.service.workspace.WorkspaceContextService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceContextController {

    @Resource
    private WorkspaceContextService workspaceContextService;

    @GetMapping
    public Result<WorkspaceContextRespDTO> getWorkspaceContext(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId) {
        WorkspaceContextRespDTO result = workspaceContextService.getWorkspaceContext(
                loginUser.getId(), workspaceId);
        return Result.ok(result);
    }

    @PutMapping
    public Result<WorkspaceContextRespDTO> updateWorkspace(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestBody @Valid WorkspaceUpdateReqDTO reqDTO) {
        WorkspaceContextRespDTO result = workspaceContextService.updateWorkspace(
                loginUser.getId(), workspaceId, reqDTO);
        return Result.ok(result);
    }

    @PutMapping("/default-project")
    public Result<WorkspaceContextRespDTO> setDefaultProject(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestBody WorkspaceDefaultProjectReqDTO reqDTO) {
        WorkspaceContextRespDTO result = workspaceContextService.setDefaultProject(
                loginUser.getId(), workspaceId, reqDTO);
        return Result.ok(result);
    }
}
