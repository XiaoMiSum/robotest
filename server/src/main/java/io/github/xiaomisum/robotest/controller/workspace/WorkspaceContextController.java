package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceDefaultProjectReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceContextRespDTO;
import io.github.xiaomisum.robotest.service.WorkspaceContextService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceContextController {

    @Resource
    private WorkspaceContextService workspaceContextService;

    @GetMapping
    public Result<WorkspaceContextRespDTO> getWorkspaceContext(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId) {
        WorkspaceContextRespDTO result = workspaceContextService.getWorkspaceContext(
                loginUser.getId().toString(), workspaceId);
        return Result.ok(result);
    }

    @PutMapping
    public Result<WorkspaceContextRespDTO> updateWorkspace(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @RequestBody @Valid WorkspaceUpdateReqDTO reqDTO) {
        WorkspaceContextRespDTO result = workspaceContextService.updateWorkspace(
                loginUser.getId().toString(), workspaceId, reqDTO);
        return Result.ok(result);
    }

    @PutMapping("/default-project")
    public Result<WorkspaceContextRespDTO> setDefaultProject(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @RequestBody WorkspaceDefaultProjectReqDTO reqDTO) {
        WorkspaceContextRespDTO result = workspaceContextService.setDefaultProject(
                loginUser.getId().toString(), workspaceId, reqDTO);
        return Result.ok(result);
    }
}
