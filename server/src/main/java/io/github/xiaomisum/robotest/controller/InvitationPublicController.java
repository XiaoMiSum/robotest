package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.model.dto.request.workspace.InvitationCheckEmailReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.InvitationJoinReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationCheckEmailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationJoinRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationVerifyRespDTO;
import io.github.xiaomisum.robotest.service.workspace.WorkspaceInvitationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;
import xyz.migoo.framework.web.core.annotation.RateLimit;

@RestController
@RequestMapping("/api/workspace/invitations")
public class InvitationPublicController {

    @Resource
    private WorkspaceInvitationService invitationService;

    // 各端点按统计键独立计数（类名#方法名:IP，安全规范 6.1）
    @RateLimit(limit = 20, window = 60)
    @GetMapping("/verify")
    public Result<InvitationVerifyRespDTO> verifyInvitation(@RequestParam String token) {
        return Result.ok(invitationService.verifyInvitation(token));
    }

    @RateLimit(limit = 20, window = 60)
    @PostMapping("/check-email")
    public Result<InvitationCheckEmailRespDTO> checkEmail(@RequestBody @Valid InvitationCheckEmailReqDTO reqDTO) {
        return Result.ok(invitationService.checkEmail(reqDTO.getToken(), reqDTO.getEmail()));
    }

    @RateLimit(limit = 20, window = 60)
    @PostMapping("/join")
    public Result<InvitationJoinRespDTO> joinByInvitation(@RequestBody @Valid InvitationJoinReqDTO reqDTO) {
        return Result.ok(invitationService.joinByInvitation(reqDTO));
    }
}
