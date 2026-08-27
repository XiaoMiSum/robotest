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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    private static final String WRITE_AUTHORITY =
            "hasAnyAuthority('api-func:edit', 'api-func:edit-space', 'api-func:edit-global')";

    @Resource
    private ApiFunctionService apiFunctionService;

    // ========== 内置函数（3.8.1） ==========

    @GetMapping("/builtin")
    @PreAuthorize("hasAuthority('api-func:view')")
    public Result<List<ApiBuiltinFunctionGroupRespDTO>> builtin() {
        return Result.ok(apiFunctionService.builtinCatalog());
    }

    // ========== 试算（3.8.2） ==========

    @PostMapping("/evaluate")
    @PreAuthorize("hasAuthority('api-func:view')")
    public Result<ApiFunctionEvaluateRespDTO> evaluate(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiFunctionEvaluateReqDTO reqDTO) {
        return Result.ok(apiFunctionService.evaluate(workspaceId, projectId, loginUser.getId(), reqDTO));
    }

    // ========== 自定义函数（3.8.3 – 3.8.7） ==========

    @GetMapping("/custom-functions")
    @PreAuthorize("hasAuthority('api-func:view')")
    public Result<List<ApiCustomFunctionListItemRespDTO>> listCustom(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(apiFunctionService.fetchCustomList(workspaceId, projectId, loginUser.getId(),
                enabled, scope, keyword));
    }

    @GetMapping("/custom-functions/{id}")
    @PreAuthorize("hasAuthority('api-func:view')")
    public Result<ApiCustomFunctionDetailRespDTO> getCustomDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(apiFunctionService.fetchCustomDetail(workspaceId, projectId, loginUser.getId(), id));
    }

    @PostMapping("/custom-functions")
    @PreAuthorize(WRITE_AUTHORITY)
    public Result<ApiCustomFunctionIdRespDTO> createCustom(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiCustomFunctionSaveReqDTO reqDTO) {
        return Result.ok(apiFunctionService.createCustom(workspaceId, projectId, loginUser.getId(), reqDTO));
    }

    @PutMapping("/custom-functions/{id}")
    @PreAuthorize(WRITE_AUTHORITY)
    public Result<Boolean> updateCustom(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiCustomFunctionSaveReqDTO reqDTO) {
        apiFunctionService.updateCustom(workspaceId, projectId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PatchMapping("/custom-functions/{id}/toggle")
    @PreAuthorize(WRITE_AUTHORITY)
    public Result<Boolean> toggleCustom(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestParam("enabled") boolean enabled) {
        apiFunctionService.toggleCustom(workspaceId, projectId, loginUser.getId(), id, enabled);
        return Result.ok(true);
    }

    @DeleteMapping("/custom-functions/{id}")
    @PreAuthorize(WRITE_AUTHORITY)
    public Result<Boolean> deleteCustom(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        apiFunctionService.deleteCustom(workspaceId, projectId, loginUser.getId(), id);
        return Result.ok(true);
    }
}
