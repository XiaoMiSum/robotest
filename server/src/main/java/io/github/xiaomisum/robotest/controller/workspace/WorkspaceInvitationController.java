package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.InvitationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.InvitationJoinReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationJoinRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationVerifyRespDTO;
import io.github.xiaomisum.robotest.service.workspace.WorkspaceInvitationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspace/invitations")
public class WorkspaceInvitationController {

    @Resource
    private WorkspaceInvitationService invitationService;

    @PostMapping
    public Result<InvitationRespDTO> createInvitation(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @RequestBody @Valid InvitationCreateReqDTO reqDTO) {
        InvitationRespDTO result = invitationService.createInvitation(
                loginUser.getId(), workspaceId, reqDTO);
        return Result.ok(result);
    }

    @GetMapping
    public Result<PageResult<InvitationRespDTO>> getInvitations(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<InvitationRespDTO> result = invitationService.getInvitationPage(
                workspaceId, pageNo, pageSize);
        return Result.ok(result);
    }

    @PutMapping("/{id}/revoke")
    public Result<Void> revokeInvitation(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @PathVariable UUID id) {
        invitationService.revokeInvitation(loginUser.getId(), workspaceId, id);
        return Result.ok();
    }
}
