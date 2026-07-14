package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.request.WorkspaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceMemberRoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceRespDTO;
import io.github.xiaomisum.robotest.service.WorkspaceService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;

@RestController
@RequestMapping("/api/admin/workspaces")
public class AdminWorkspaceController {

    @Resource
    private WorkspaceService workspaceService;

    @GetMapping
    public Result<PageResult<WorkspaceRespDTO>> getWorkspacePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(workspaceService.getWorkspacePage(keyword, pageNo, pageSize));
    }

    @PostMapping
    public Result<String> createWorkspace(@RequestBody @Valid WorkspaceCreateReqDTO reqDTO) {
        return Result.ok(workspaceService.createWorkspace(reqDTO));
    }

    @GetMapping("/{id}")
    public Result<WorkspaceRespDTO> getWorkspaceDetail(@PathVariable String id) {
        return Result.ok(workspaceService.getWorkspaceDetail(id));
    }

    @PutMapping("/{id}")
    public Result<WorkspaceRespDTO> updateWorkspace(@PathVariable String id,
                                                    @RequestBody @Valid WorkspaceUpdateReqDTO reqDTO) {
        return Result.ok(workspaceService.updateWorkspace(id, reqDTO));
    }

    @DeleteMapping("/{id}")
    public Result<Void> dissolveWorkspace(@PathVariable String id) {
        workspaceService.dissolveWorkspace(id);
        return Result.ok();
    }

    @GetMapping("/{id}/members")
    public Result<PageResult<WorkspaceMemberRespDTO>> getWorkspaceMembers(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(workspaceService.getWorkspaceMembers(id, pageNo, pageSize));
    }

    @PostMapping("/{id}/members")
    public Result<List<String>> addWorkspaceMembers(@PathVariable String id,
                                                    @RequestBody @Valid WorkspaceMembersAddReqDTO reqDTO) {
        return Result.ok(workspaceService.addWorkspaceMembers(id, reqDTO.getMembers()));
    }

    @PutMapping("/{id}/members/{userId}")
    public Result<Void> updateWorkspaceMemberRole(@PathVariable String id,
                                                   @PathVariable String userId,
                                                   @RequestBody @Valid WorkspaceMemberRoleUpdateReqDTO reqDTO) {
        workspaceService.updateWorkspaceMemberRole(id, userId, reqDTO.getWorkspaceRole());
        return Result.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeWorkspaceMember(@PathVariable String id,
                                              @PathVariable String userId) {
        workspaceService.removeWorkspaceMember(id, userId);
        return Result.ok();
    }
}
