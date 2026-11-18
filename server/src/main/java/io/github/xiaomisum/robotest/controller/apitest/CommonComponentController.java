package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.CommonComponentBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.CommonComponentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentListItemRespDTO;
import io.github.xiaomisum.robotest.service.apitest.CommonComponentService;
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
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 公共组件（基础设施详设 3.5）：全局/空间/项目三级作用域组件管理。
 */
@RestController
@RequestMapping("/api/project/components")
public class CommonComponentController {

    @Resource
    private CommonComponentService commonComponentService;

    @GetMapping
    public Result<PageResult<CommonComponentListItemRespDTO>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid PageParam pageParam,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(commonComponentService.fetchList(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(),
                pageParam, type, enabled, scope, keyword));
    }

    @PostMapping
    public Result<CommonComponentIdRespDTO> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid CommonComponentSaveReqDTO reqDTO) {
        return Result.ok(commonComponentService.create(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), reqDTO));
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid CommonComponentSaveReqDTO reqDTO) {
        commonComponentService.update(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PatchMapping("/{id}/toggle")
    public Result<Boolean> toggle(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestParam("enabled") boolean enabled) {
        commonComponentService.toggle(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, enabled);
        return Result.ok(true);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        commonComponentService.delete(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    @PatchMapping("/batch/toggle")
    public Result<Boolean> batchToggle(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid CommonComponentBatchReqDTO reqDTO,
            @RequestParam("enabled") boolean enabled) {
        commonComponentService.batchToggle(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), reqDTO.getIds(), enabled);
        return Result.ok(true);
    }

    @DeleteMapping("/batch")
    public Result<Boolean> batchDelete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid CommonComponentBatchReqDTO reqDTO) {
        commonComponentService.batchDelete(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), reqDTO.getIds());
        return Result.ok(true);
    }
}
