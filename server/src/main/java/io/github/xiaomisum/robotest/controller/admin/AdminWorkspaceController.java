package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceMemberRoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceRespDTO;
import io.github.xiaomisum.robotest.service.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/workspaces")
public class AdminWorkspaceController {

    @Resource
    private WorkspaceService workspaceService;

    @GetMapping
    public Result<PageResult<WorkspaceRespDTO>> getWorkspacePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(workspaceService.getWorkspacePage(keyword, status, pageNo, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('workspace:create')")
    public Result<String> createWorkspace(@RequestBody @Valid WorkspaceCreateReqDTO reqDTO) {
        return Result.ok(workspaceService.createWorkspace(reqDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workspace:view')")
    public Result<WorkspaceRespDTO> getWorkspaceDetail(@PathVariable UUID id) {
        return Result.ok(workspaceService.getWorkspaceDetail(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('workspace:edit')")
    public Result<WorkspaceRespDTO> updateWorkspace(@PathVariable UUID id,
                                                    @RequestBody @Valid WorkspaceUpdateReqDTO reqDTO) {
        return Result.ok(workspaceService.updateWorkspace(id, reqDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('workspace:delete')")
    public Result<Void> dissolveWorkspace(@PathVariable UUID id) {
        workspaceService.dissolveWorkspace(id);
        return Result.ok();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('workspace:view')")
    public Result<PageResult<WorkspaceMemberRespDTO>> getWorkspaceMembers(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(workspaceService.getWorkspaceMembers(id, pageNo, pageSize));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('workspace:manage-members')")
    public Result<List<String>> addWorkspaceMembers(@PathVariable UUID id,
                                                    @RequestBody @Valid WorkspaceMembersAddReqDTO reqDTO) {
        return Result.ok(workspaceService.addWorkspaceMembers(id, reqDTO.getMembers()));
    }

    @PutMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('workspace:manage-members')")
    public Result<Void> updateWorkspaceMemberRole(@PathVariable UUID id,
                                                   @PathVariable UUID userId,
                                                   @RequestBody @Valid WorkspaceMemberRoleUpdateReqDTO reqDTO) {
        workspaceService.updateWorkspaceMemberRole(id, userId, reqDTO.getWorkspaceRole());
        return Result.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('workspace:manage-members')")
    public Result<Void> removeWorkspaceMember(@PathVariable UUID id,
                                              @PathVariable UUID userId) {
        workspaceService.removeWorkspaceMember(id, userId);
        return Result.ok();
    }
}
