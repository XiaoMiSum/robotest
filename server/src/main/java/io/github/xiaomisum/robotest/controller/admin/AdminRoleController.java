package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.request.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RoleUsersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.PermissionTableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleSimpleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleUserRespDTO;
import io.github.xiaomisum.robotest.service.admin.RoleService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/roles")
public class AdminRoleController {

    @Resource
    private RoleService roleService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('role:view')")
    public Result<List<RoleSimpleRespDTO>> getRoleList() {
        return Result.ok(roleService.getRoleList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:create')")
    public Result<String> createRole(@RequestBody @Valid RoleCreateReqDTO reqDTO) {
        return Result.ok(roleService.createRole(reqDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role:edit')")
    public Result<RoleRespDTO> updateRole(@PathVariable UUID id,
                                          @RequestBody @Valid RoleUpdateReqDTO reqDTO) {
        return Result.ok(roleService.updateRole(id, reqDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:delete')")
    public Result<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('role:view')")
    public Result<RoleRespDTO> getRoleDetail(@PathVariable UUID id) {
        return Result.ok(roleService.getRoleDetail(id));
    }

    @GetMapping("/{id}/users")
    @PreAuthorize("hasAuthority('role:view')")
    public Result<PageResult<RoleUserRespDTO>> getRoleUsers(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(roleService.getRoleUsers(id, pageNo, pageSize));
    }

    @PostMapping("/{id}/users")
    @PreAuthorize("hasAuthority('role:edit')")
    public Result<Void> addRoleUsers(@PathVariable UUID id,
                                      @RequestBody @Valid RoleUsersAddReqDTO reqDTO) {
        roleService.addRoleUsers(id, reqDTO.getUserIds());
        return Result.ok();
    }

    @DeleteMapping("/{id}/users/{userId}")
    @PreAuthorize("hasAuthority('role:edit')")
    public Result<Void> removeRoleUser(@PathVariable UUID id,
                                        @PathVariable UUID userId) {
        roleService.removeRoleUser(id, userId);
        return Result.ok();
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('role:edit')")
    public Result<RoleRespDTO> updateRolePermissions(@PathVariable UUID id,
                                                     @RequestBody @Valid RolePermissionsUpdateReqDTO reqDTO) {
        return Result.ok(roleService.updateRolePermissions(id, reqDTO));
    }

    @GetMapping("/permissions/table")
    @PreAuthorize("hasAuthority('role:view')")
    public Result<List<PermissionTableRespDTO>> getPermissionTable() {
        return Result.ok(roleService.getPermissionTable());
    }
}
