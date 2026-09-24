package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceMemberRoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.UserSimpleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberAddResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.service.admin.UserService;
import io.github.xiaomisum.robotest.service.workspace.WorkspaceMemberService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspace/members")
public class WorkspaceMemberController {

    @Resource
    private WorkspaceMemberService workspaceMemberService;

    @Resource
    private UserService userService;

    /**
     * 添加成员弹窗的候选用户搜索：工作空间侧替代 /api/admin/users/simple，
     * 仅空间管理员（ws-member:manage）可用（SEC-002）
     */
    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('ws-member:manage')")
    public Result<List<UserSimpleRespDTO>> searchMemberCandidates(
            @RequestParam(required = false) String keyword) {
        return Result.ok(userService.getUserSimpleList(keyword));
    }

    @GetMapping
    public Result<PageResult<WorkspaceMemberRespDTO>> getMembers(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID workspaceRole,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<WorkspaceMemberRespDTO> result = workspaceMemberService.getMemberPage(
                loginUser.getId(), loginUser.getActiveWorkspaceId(), keyword, workspaceRole, pageNo, pageSize);
        return Result.ok(result);
    }

    @PostMapping
    public Result<WorkspaceMemberAddResultRespDTO> addMembers(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid WorkspaceMembersAddReqDTO reqDTO) {
        WorkspaceMemberAddResultRespDTO result = workspaceMemberService.addMembers(
                loginUser.getId(), loginUser.getActiveWorkspaceId(), reqDTO);
        return Result.ok(result);
    }

    @PutMapping("/{userId}")
    public Result<Void> updateMemberRole(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID userId,
            @RequestBody WorkspaceMemberRoleUpdateReqDTO reqDTO) {
        workspaceMemberService.updateMemberRole(loginUser.getId(), loginUser.getActiveWorkspaceId(), userId, reqDTO.getWorkspaceRole());
        return Result.ok();
    }

    @DeleteMapping("/{userId}")
    public Result<Void> removeMember(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID userId) {
        workspaceMemberService.removeMember(loginUser.getId(), loginUser.getActiveWorkspaceId(), userId);
        return Result.ok();
    }
}
