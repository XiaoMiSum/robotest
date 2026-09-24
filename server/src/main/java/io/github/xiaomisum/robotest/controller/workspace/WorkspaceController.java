package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.workspace.MyWorkspaceQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyPageRespDTO;
import io.github.xiaomisum.robotest.service.workspace.MyWorkspaceService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    @Resource
    private MyWorkspaceService myWorkspaceService;

    @GetMapping
    public Result<WorkspaceMyPageRespDTO> getMyWorkspaces(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid MyWorkspaceQueryReqDTO query) {
        return Result.ok(myWorkspaceService.getMyWorkspaces(loginUser.getId(), query));
    }

    @PutMapping("/active")
    public Result<Void> setActiveWorkspace(@AuthenticationPrincipal LoginUser loginUser) {
        myWorkspaceService.setActiveWorkspace(loginUser.getId(), loginUser.getActiveWorkspaceId());
        return Result.ok();
    }
}
