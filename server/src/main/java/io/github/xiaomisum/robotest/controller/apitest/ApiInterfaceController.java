package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchMoveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiParsedImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceChangeLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceReferenceRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiInterfaceService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 接口定义管理（接口管理详细设计 3.1–3.4）
 */
@RestController
public class ApiInterfaceController {

    @Resource
    private ApiInterfaceService interfaceService;

    // ==================== 3.1 接口定义 ====================

    @GetMapping("/api/project/interfaces")
    @PreAuthorize("hasAuthority('api-interface:view')")
    public Result<PageResult<ApiInterfaceItemRespDTO>> page(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid PageParam pageParam,
            @RequestParam(value = "moduleId", required = false) UUID moduleId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "view", required = false, defaultValue = "all") String view) {
        return Result.ok(interfaceService.page(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(),
                moduleId, search, status, view, pageParam));
    }

    @GetMapping("/api/project/interfaces/{id}")
    @PreAuthorize("hasAuthority('api-interface:view')")
    public Result<ApiInterfaceDetailRespDTO> detail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(interfaceService.getDetail(loginUser.getActiveProjectId(), id, loginUser.getId()));
    }

    @PostMapping("/api/project/interfaces")
    @PreAuthorize("hasAuthority('api-interface:edit')")
    public Result<Map<String, UUID>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiInterfaceCreateReqDTO reqDTO) {
        return Result.ok(Map.of("id", interfaceService.create(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(),
                loginUser.getId(), reqDTO)));
    }

    @PutMapping("/api/project/interfaces/{id}")
    @PreAuthorize("hasAuthority('api-interface:edit')")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiInterfaceUpdateReqDTO reqDTO) {
        interfaceService.update(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/interfaces/{id}")
    @PreAuthorize("hasAuthority('api-interface:delete')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        interfaceService.delete(loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    @PostMapping("/api/project/interfaces/{id}/copy")
    @PreAuthorize("hasAuthority('api-interface:edit')")
    public Result<Map<String, UUID>> copy(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        return Result.ok(Map.of("id", interfaceService.copy(loginUser.getActiveProjectId(), loginUser.getId(), id,
                body == null ? null : body.get("name"))));
    }

    @GetMapping("/api/project/interfaces/{id}/references")
    @PreAuthorize("hasAuthority('api-interface:view')")
    public Result<ApiInterfaceReferenceRespDTO> references(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(interfaceService.references(loginUser.getActiveProjectId(), loginUser.getId(), id));
    }

    @GetMapping("/api/project/interfaces/{id}/scenes")
    @PreAuthorize("hasAuthority('api-interface:view')")
    public Result<List<ApiInterfaceReferenceRespDTO.RefItem>> referenceScenes(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(interfaceService.referenceScenes(loginUser.getActiveProjectId(), loginUser.getId(), id));
    }

    @PutMapping("/api/project/interfaces/batch/move")
    @PreAuthorize("hasAuthority('api-interface:edit')")
    public Result<Boolean> batchMove(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiInterfaceBatchMoveReqDTO reqDTO) {
        interfaceService.batchMove(loginUser.getActiveProjectId(), loginUser.getId(), reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/interfaces/batch")
    @PreAuthorize("hasAuthority('api-interface:delete')")
    public Result<Boolean> batchDelete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiInterfaceBatchDeleteReqDTO reqDTO) {
        interfaceService.batchDelete(loginUser.getActiveProjectId(), loginUser.getId(), reqDTO);
        return Result.ok(true);
    }

    @PutMapping("/api/project/interfaces/{id}/status")
    @PreAuthorize("hasAuthority('api-interface:edit')")
    public Result<Boolean> updateStatus(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiInterfaceStatusReqDTO reqDTO) {
        interfaceService.updateStatus(loginUser.getActiveProjectId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PostMapping("/api/project/interfaces/{id}/follow")
    @PreAuthorize("hasAuthority('api-interface:edit')")
    public Result<Boolean> follow(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        interfaceService.follow(loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/interfaces/{id}/follow")
    @PreAuthorize("hasAuthority('api-interface:edit')")
    public Result<Boolean> unfollow(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        interfaceService.unfollow(loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    @GetMapping("/api/project/interfaces/{id}/change-logs")
    @PreAuthorize("hasAuthority('api-interface:view')")
    public Result<PageResult<ApiInterfaceChangeLogRespDTO>> changeLogs(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @Valid PageParam pageParam) {
        return Result.ok(interfaceService.changeLogs(loginUser.getActiveProjectId(), loginUser.getId(), id, pageParam));
    }

    // ==================== 3.4 导入 ====================

    @PostMapping("/api/project/interfaces/import/parsed")
    @PreAuthorize("hasAuthority('api-interface:edit')")
    public Result<ApiImportResultRespDTO> importParsed(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiParsedImportReqDTO reqDTO) {
        return Result.ok(interfaceService.importParsed(loginUser.getActiveProjectId(), loginUser.getId(), reqDTO));
    }

    @PostMapping("/api/project/interfaces/import/url")
    @PreAuthorize("hasAuthority('api-interface:edit')")
    public Result<ApiImportResultRespDTO> importUrl(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody Map<String, String> body) {
        return Result.ok(interfaceService.importUrl(loginUser.getActiveProjectId(), loginUser.getId(),
                body.getOrDefault("url", ""), body.get("format")));
    }

    @PostMapping("/api/project/interfaces/import/preview")
    @PreAuthorize("hasAuthority('api-interface:view')")
    public Result<ApiImportPreviewRespDTO> preview(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody Map<String, String> body) {
        return Result.ok(interfaceService.preview(loginUser.getActiveProjectId(), loginUser.getId(),
                body.getOrDefault("url", ""), body.get("format")));
    }
}
