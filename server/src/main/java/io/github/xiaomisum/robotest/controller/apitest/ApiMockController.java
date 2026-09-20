package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockBatchToggleReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockToggleReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockAddressRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockBatchToggleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockDebugRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockItemRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiMockService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * Mock 管理（Mock服务详细设计 3.1/3.2）；免登录访问入口见 MockAccessFilter
 */
@RestController
@RequestMapping("/api/project/mocks")
public class ApiMockController {

    @Resource
    private ApiMockService apiMockService;

    @GetMapping
    @PreAuthorize("hasAuthority('api-mock:view')")
    public Result<PageResult<ApiMockItemRespDTO>> page(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(value = "interfaceId", required = false) UUID interfaceId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        return Result.ok(apiMockService.fetchPage(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(),
                interfaceId, search, enabled, pageParam));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('api-mock:edit')")
    public Result<ApiMockIdRespDTO> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiMockSaveReqDTO reqDTO) {
        return Result.ok(apiMockService.create(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), reqDTO));
    }

    @PostMapping("/from-interface/{interfaceId}")
    @PreAuthorize("hasAuthority('api-mock:edit')")
    public Result<ApiMockIdRespDTO> createFromInterface(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID interfaceId,
            @RequestBody @Valid ApiMockSaveReqDTO reqDTO) {
        return Result.ok(apiMockService.createFromInterface(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(),
                interfaceId, reqDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('api-mock:view')")
    public Result<ApiMockDetailRespDTO> detail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(apiMockService.getDetail(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('api-mock:edit')")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiMockSaveReqDTO reqDTO) {
        apiMockService.update(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('api-mock:edit')")
    public Result<Boolean> toggle(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiMockToggleReqDTO reqDTO) {
        apiMockService.toggle(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, reqDTO.getEnabled());
        return Result.ok(true);
    }

    @PostMapping("/batch-toggle")
    @PreAuthorize("hasAuthority('api-mock:edit')")
    public Result<ApiMockBatchToggleRespDTO> batchToggle(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiMockBatchToggleReqDTO reqDTO) {
        return Result.ok(apiMockService.batchToggle(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), reqDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('api-mock:edit')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        apiMockService.delete(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAuthority('api-mock:edit')")
    public Result<ApiMockIdRespDTO> duplicate(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(apiMockService.duplicate(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id));
    }

    @PostMapping("/{id}/reset-hit-count")
    @PreAuthorize("hasAuthority('api-mock:edit')")
    public Result<Boolean> resetHitCount(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        apiMockService.resetHitCount(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    @GetMapping("/{id}/address")
    @PreAuthorize("hasAuthority('api-mock:view')")
    public Result<ApiMockAddressRespDTO> address(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(apiMockService.getAddress(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id));
    }

    @PostMapping("/{id}/debug")
    @PreAuthorize("hasAuthority('api-mock:view')")
    public Result<ApiMockDebugRespDTO> debug(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiMockDebugReqDTO reqDTO) {
        // 调试为只读模拟命中，不产生持久化数据（详细设计 3.2.1）
        return Result.ok(apiMockService.debug(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, reqDTO));
    }

}
