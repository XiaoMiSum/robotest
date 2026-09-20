package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.workspace.InvitationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.InvitationJoinReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationJoinRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationVerifyRespDTO;
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
            @RequestBody @Valid InvitationCreateReqDTO reqDTO) {
        InvitationRespDTO result = invitationService.createInvitation(
                loginUser.getId(), loginUser.getActiveWorkspaceId(), reqDTO);
        return Result.ok(result);
    }

    @GetMapping
    public Result<PageResult<InvitationListRespDTO>> getInvitations(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<InvitationListRespDTO> result = invitationService.getInvitationPage(
                loginUser.getId(), loginUser.getActiveWorkspaceId(), pageNo, pageSize);
        return Result.ok(result);
    }

    @PutMapping("/{id}/revoke")
    public Result<Void> revokeInvitation(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        invitationService.revokeInvitation(loginUser.getId(), loginUser.getActiveWorkspaceId(), id);
        return Result.ok();
    }
}
