package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.request.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RoleUsersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.PermissionTableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleUserRespDTO;
import io.github.xiaomisum.robotest.service.admin.RoleService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
    public Result<List<RoleTreeRespDTO>> getRoleTree() {
        return Result.ok(roleService.getRoleTree());
    }

    @PostMapping
    public Result<String> createRole(@RequestBody @Valid RoleCreateReqDTO reqDTO) {
        return Result.ok(roleService.createRole(reqDTO));
    }

    @PutMapping("/{id}")
    public Result<RoleRespDTO> updateRole(@PathVariable String id,
                                          @RequestBody @Valid RoleUpdateReqDTO reqDTO) {
        return Result.ok(roleService.updateRole(id, reqDTO));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable String id) {
        roleService.deleteRole(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<RoleRespDTO> getRoleDetail(@PathVariable String id) {
        return Result.ok(roleService.getRoleDetail(id));
    }

    @GetMapping("/{id}/users")
    public Result<PageResult<RoleUserRespDTO>> getRoleUsers(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(roleService.getRoleUsers(id, pageNo, pageSize));
    }

    @PostMapping("/{id}/users")
    public Result<Void> addRoleUsers(@PathVariable String id,
                                      @RequestBody @Valid RoleUsersAddReqDTO reqDTO) {
        roleService.addRoleUsers(id, reqDTO.getUserIds().stream().map(UUID::toString).collect(Collectors.toList()));
        return Result.ok();
    }

    @DeleteMapping("/{id}/users/{userId}")
    public Result<Void> removeRoleUser(@PathVariable String id,
                                        @PathVariable String userId) {
        roleService.removeRoleUser(id, userId);
        return Result.ok();
    }

    @PutMapping("/{id}/permissions")
    public Result<RoleRespDTO> updateRolePermissions(@PathVariable String id,
                                                     @RequestBody @Valid RolePermissionsUpdateReqDTO reqDTO) {
        return Result.ok(roleService.updateRolePermissions(id, reqDTO));
    }

    @GetMapping("/permissions/table")
    public Result<List<PermissionTableRespDTO>> getPermissionTable() {
        return Result.ok(roleService.getPermissionTable());
    }
}
