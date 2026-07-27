package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.model.dto.request.InvitationCheckEmailReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.InvitationJoinReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationCheckEmailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationJoinRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationVerifyRespDTO;
import io.github.xiaomisum.robotest.service.workspace.WorkspaceInvitationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

@RestController
@RequestMapping("/api/workspace/invitations")
public class InvitationPublicController {

    @Resource
    private WorkspaceInvitationService invitationService;

    @GetMapping("/verify")
    public Result<InvitationVerifyRespDTO> verifyInvitation(@RequestParam String token) {
        InvitationVerifyRespDTO result = invitationService.verifyInvitation(token);
        return Result.ok(result);
    }

    @PostMapping("/check-email")
    public Result<InvitationCheckEmailRespDTO> checkEmail(@RequestBody @Valid InvitationCheckEmailReqDTO reqDTO) {
        InvitationCheckEmailRespDTO result = invitationService.checkEmail(reqDTO.getToken(), reqDTO.getEmail());
        return Result.ok(result);
    }

    @PostMapping("/join")
    public Result<InvitationJoinRespDTO> joinByInvitation(@RequestBody @Valid InvitationJoinReqDTO reqDTO) {
        InvitationJoinRespDTO result = invitationService.joinByInvitation(reqDTO);
        return Result.ok(result);
    }
}
