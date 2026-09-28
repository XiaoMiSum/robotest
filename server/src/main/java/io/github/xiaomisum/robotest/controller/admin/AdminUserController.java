package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.request.admin.UserBatchStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserPasswordResetReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserStatusUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.UserRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.UserSimpleRespDTO;
import io.github.xiaomisum.robotest.service.admin.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;
import xyz.migoo.framework.web.core.annotation.RateLimit;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Resource
    private UserService userService;

    @GetMapping
    // role:view 覆盖角色页“用户”页签的角色用户列表查询
    @PreAuthorize("hasAnyAuthority('user:view', 'role:view')")
    public Result<PageResult<UserRespDTO>> getUserPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(userService.getUserPage(keyword, status, roleId, pageNo, pageSize));
    }

    @GetMapping("/simple")
    // 用户选择器服务于用户页、角色用户、管理端空间详情与创建空间弹窗，按各入口权限取并集
    @PreAuthorize("hasAnyAuthority('user:view', 'workspace:create', 'workspace:manage-members', 'role:view')")
    public Result<List<UserSimpleRespDTO>> getUserSimpleList(
            @RequestParam(required = false) String keyword) {
        return Result.ok(userService.getUserSimpleList(keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public Result<String> createUser(@RequestBody @Valid UserCreateReqDTO reqDTO) {
        return Result.ok(userService.createUser(reqDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:view')")
    public Result<UserRespDTO> getUserDetail(@PathVariable UUID id) {
        return Result.ok(userService.getUserDetail(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:edit')")
    public Result<UserRespDTO> updateUser(@PathVariable UUID id,
                                          @RequestBody @Valid UserUpdateReqDTO reqDTO) {
        return Result.ok(userService.updateUser(id, reqDTO));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:disable')")
    public Result<UserRespDTO> updateUserStatus(@PathVariable UUID id,
                                                @RequestBody @Valid UserStatusUpdateReqDTO reqDTO) {
        return Result.ok(userService.updateUserStatus(id, reqDTO.getStatus()));
    }

    @PatchMapping("/batch-status")
    @PreAuthorize("hasAuthority('user:disable')")
    public Result<Void> batchUpdateStatus(@RequestBody @Valid UserBatchStatusReqDTO reqDTO) {
        userService.batchUpdateStatus(reqDTO);
        return Result.ok();
    }

    @RateLimit(limit = 10, window = 300)
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:reset-password')")
    public Result<Void> resetPassword(@PathVariable UUID id,
                                       @RequestBody @Valid UserPasswordResetReqDTO reqDTO) {
        userService.resetPassword(id, reqDTO.getNewPassword());
        return Result.ok();
    }
}
