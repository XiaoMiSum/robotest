package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.CommonComponentBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.CommonComponentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentCopyRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentListItemRespDTO;
import io.github.xiaomisum.robotest.service.apitest.CommonComponentService;
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
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 公共组件（基础设施详设 3.5）：全局/空间/项目三级作用域组件管理。
 *
 * <p>写端点放行三种维护码任一，记录实际作用域的精确校验在 Service 内完成。</p>
 */
@RestController
@RequestMapping("/api/project/components")
public class CommonComponentController {

    private static final String WRITE_AUTHORITY =
            "hasAnyAuthority('api-component:edit', 'api-component:edit-space', 'api-component:edit-global')";

    @Resource
    private CommonComponentService commonComponentService;

    @GetMapping
    @PreAuthorize("hasAuthority('api-component:view')")
    public Result<PageResult<CommonComponentListItemRespDTO>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @Valid PageParam pageParam,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(commonComponentService.fetchList(workspaceId, projectId, loginUser.getId(),
                pageParam, type, enabled, scope, keyword));
    }

    @PostMapping
    @PreAuthorize(WRITE_AUTHORITY)
    public Result<CommonComponentIdRespDTO> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid CommonComponentSaveReqDTO reqDTO) {
        return Result.ok(commonComponentService.create(workspaceId, projectId, loginUser.getId(), reqDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_AUTHORITY)
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid CommonComponentSaveReqDTO reqDTO) {
        commonComponentService.update(workspaceId, projectId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize(WRITE_AUTHORITY)
    public Result<Boolean> toggle(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestParam("enabled") boolean enabled) {
        commonComponentService.toggle(workspaceId, projectId, loginUser.getId(), id, enabled);
        return Result.ok(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_AUTHORITY)
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        commonComponentService.delete(workspaceId, projectId, loginUser.getId(), id);
        return Result.ok(true);
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAuthority('api-component:view')")
    public Result<CommonComponentCopyRespDTO> copy(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(commonComponentService.copy(workspaceId, projectId, loginUser.getId(), id));
    }

    @PatchMapping("/batch/toggle")
    @PreAuthorize(WRITE_AUTHORITY)
    public Result<Boolean> batchToggle(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid CommonComponentBatchReqDTO reqDTO,
            @RequestParam("enabled") boolean enabled) {
        commonComponentService.batchToggle(workspaceId, projectId, loginUser.getId(), reqDTO.getIds(), enabled);
        return Result.ok(true);
    }

    @DeleteMapping("/batch")
    @PreAuthorize(WRITE_AUTHORITY)
    public Result<Boolean> batchDelete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid CommonComponentBatchReqDTO reqDTO) {
        commonComponentService.batchDelete(workspaceId, projectId, loginUser.getId(), reqDTO.getIds());
        return Result.ok(true);
    }
}
