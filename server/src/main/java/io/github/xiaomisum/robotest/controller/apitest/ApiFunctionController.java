package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiCustomFunctionSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiFunctionEvaluateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiBuiltinFunctionGroupRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiCustomFunctionDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiCustomFunctionIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiCustomFunctionListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiFunctionEvaluateRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiFunctionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

/**
 * 函数助手（基础设施详设 3.8）：内置函数目录、表达式试算、自定义函数管理。
 */
@RestController
@RequestMapping("/api/project/functions")
public class ApiFunctionController {

    @Resource
    private ApiFunctionService apiFunctionService;

    // ========== 内置函数（3.8.1） ==========

    @GetMapping("/builtin")
    public Result<List<ApiBuiltinFunctionGroupRespDTO>> builtin() {
        return Result.ok(apiFunctionService.builtinCatalog());
    }

    // ========== 试算（3.8.2） ==========

    @PostMapping("/evaluate")
    public Result<ApiFunctionEvaluateRespDTO> evaluate(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiFunctionEvaluateReqDTO reqDTO) {
        return Result.ok(apiFunctionService.evaluate(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), reqDTO));
    }

    // ========== 自定义函数（3.8.3 – 3.8.7） ==========

    @GetMapping("/custom-functions")
    public Result<List<ApiCustomFunctionListItemRespDTO>> listCustom(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(apiFunctionService.fetchCustomList(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(),
                enabled, scope, keyword));
    }

    @GetMapping("/custom-functions/{id}")
    public Result<ApiCustomFunctionDetailRespDTO> getCustomDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(apiFunctionService.fetchCustomDetail(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id));
    }

    @PostMapping("/custom-functions")
    public Result<ApiCustomFunctionIdRespDTO> createCustom(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiCustomFunctionSaveReqDTO reqDTO) {
        return Result.ok(apiFunctionService.createCustom(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), reqDTO));
    }

    @PutMapping("/custom-functions/{id}")
    public Result<Boolean> updateCustom(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiCustomFunctionSaveReqDTO reqDTO) {
        apiFunctionService.updateCustom(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PatchMapping("/custom-functions/{id}/toggle")
    public Result<Boolean> toggleCustom(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestParam("enabled") boolean enabled) {
        apiFunctionService.toggleCustom(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, enabled);
        return Result.ok(true);
    }

    @DeleteMapping("/custom-functions/{id}")
    public Result<Boolean> deleteCustom(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        apiFunctionService.deleteCustom(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }
}
