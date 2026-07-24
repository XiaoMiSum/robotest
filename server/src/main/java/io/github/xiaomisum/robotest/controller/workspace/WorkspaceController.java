package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceActiveSetReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMyRespDTO;
import io.github.xiaomisum.robotest.service.admin.MyWorkspaceService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    @Resource
    private MyWorkspaceService myWorkspaceService;

    @GetMapping
    public Result<PageResult<WorkspaceMyRespDTO>> getMyWorkspaces(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<WorkspaceMyRespDTO> result = myWorkspaceService.getMyWorkspacePage(
                loginUser.getId(), pageNo, pageSize);
        return Result.ok(result);
    }

    @PutMapping("/active")
    public Result<Void> setActiveWorkspace(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid WorkspaceActiveSetReqDTO reqDTO) {
        myWorkspaceService.setActiveWorkspace(loginUser.getId(), reqDTO.getWorkspaceId());
        return Result.ok();
    }
}
