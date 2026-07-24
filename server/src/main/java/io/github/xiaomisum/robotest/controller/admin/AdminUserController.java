package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.request.UserBatchStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserPasswordResetReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserStatusUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.UserRespDTO;
import io.github.xiaomisum.robotest.service.admin.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Resource
    private UserService userService;

    @GetMapping
    public Result<PageResult<UserRespDTO>> getUserPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(userService.getUserPage(keyword, status, roleId, workspaceId, pageNo, pageSize));
    }

    @PostMapping
    public Result<String> createUser(@RequestBody @Valid UserCreateReqDTO reqDTO) {
        return Result.ok(userService.createUser(reqDTO));
    }

    @GetMapping("/{id}")
    public Result<UserRespDTO> getUserDetail(@PathVariable UUID id) {
        return Result.ok(userService.getUserDetail(id));
    }

    @PutMapping("/{id}")
    public Result<UserRespDTO> updateUser(@PathVariable UUID id,
                                          @RequestBody @Valid UserUpdateReqDTO reqDTO) {
        return Result.ok(userService.updateUser(id, reqDTO));
    }

    @PatchMapping("/{id}/status")
    public Result<UserRespDTO> updateUserStatus(@PathVariable UUID id,
                                                @RequestBody @Valid UserStatusUpdateReqDTO reqDTO) {
        return Result.ok(userService.updateUserStatus(id, reqDTO.getStatus()));
    }

    @PatchMapping("/batch-status")
    public Result<Void> batchUpdateStatus(@RequestBody @Valid UserBatchStatusReqDTO reqDTO) {
        userService.batchUpdateStatus(reqDTO);
        return Result.ok();
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable UUID id,
                                       @RequestBody @Valid UserPasswordResetReqDTO reqDTO) {
        userService.resetPassword(id, reqDTO.getNewPassword());
        return Result.ok();
    }
}
