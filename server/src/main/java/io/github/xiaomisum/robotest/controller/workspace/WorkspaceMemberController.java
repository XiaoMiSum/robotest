package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceMemberRoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberAddResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.service.workspace.WorkspaceMemberService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspace/members")
public class WorkspaceMemberController {

    @Resource
    private WorkspaceMemberService workspaceMemberService;

    @GetMapping
    public Result<PageResult<WorkspaceMemberRespDTO>> getMembers(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<WorkspaceMemberRespDTO> result = workspaceMemberService.getMemberPage(
                loginUser.getId(), workspaceId, keyword, pageNo, pageSize);
        return Result.ok(result);
    }

    @PostMapping
    public Result<WorkspaceMemberAddResultRespDTO> addMembers(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestBody @Valid WorkspaceMembersAddReqDTO reqDTO) {
        WorkspaceMemberAddResultRespDTO result = workspaceMemberService.addMembers(
                loginUser.getId(), workspaceId, reqDTO);
        return Result.ok(result);
    }

    @PutMapping("/{userId}")
    public Result<Void> updateMemberRole(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @PathVariable UUID userId,
            @RequestBody WorkspaceMemberRoleUpdateReqDTO reqDTO) {
        workspaceMemberService.updateMemberRole(loginUser.getId(), workspaceId, userId, reqDTO.getWorkspaceRole());
        return Result.ok();
    }

    @DeleteMapping("/{userId}")
    public Result<Void> removeMember(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @PathVariable UUID userId) {
        workspaceMemberService.removeMember(loginUser.getId(), workspaceId, userId);
        return Result.ok();
    }
}
