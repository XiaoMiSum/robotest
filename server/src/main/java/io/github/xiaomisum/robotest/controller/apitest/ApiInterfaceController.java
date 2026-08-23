package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchMoveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceStepReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceStepSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceVariablesReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceChangeLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceReferenceRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceVariableRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiInterfaceService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.io.IOException;
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
    public Result<PageResult<ApiInterfaceItemRespDTO>> page(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @Valid PageParam pageParam,
            @RequestParam(value = "moduleId", required = false) UUID moduleId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "view", required = false, defaultValue = "all") String view) {
        return Result.ok(interfaceService.page(projectId, workspaceId, loginUser.getId(),
                moduleId, search, status, view, pageParam));
    }

    @GetMapping("/api/project/interfaces/{id}")
    public Result<ApiInterfaceDetailRespDTO> detail(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(interfaceService.getDetail(projectId, id, loginUser.getId()));
    }

    @PostMapping("/api/project/interfaces")
    public Result<Map<String, UUID>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiInterfaceCreateReqDTO reqDTO) {
        return Result.ok(Map.of("id", interfaceService.create(projectId, workspaceId,
                loginUser.getId(), reqDTO)));
    }

    @PutMapping("/api/project/interfaces/{id}")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiInterfaceUpdateReqDTO reqDTO) {
        interfaceService.update(projectId, workspaceId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/interfaces/{id}")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        interfaceService.delete(projectId, id);
        return Result.ok(true);
    }

    @PostMapping("/api/project/interfaces/{id}/copy")
    public Result<Map<String, UUID>> copy(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        return Result.ok(Map.of("id", interfaceService.copy(projectId, loginUser.getId(), id,
                body == null ? null : body.get("name"))));
    }

    @GetMapping("/api/project/interfaces/{id}/references")
    public Result<ApiInterfaceReferenceRespDTO> references(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(interfaceService.references(projectId, id));
    }

    @GetMapping("/api/project/interfaces/{id}/scenes")
    public Result<List<ApiInterfaceReferenceRespDTO.RefItem>> referenceScenes(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(interfaceService.referenceScenes(projectId, id));
    }

    @PutMapping("/api/project/interfaces/batch/move")
    public Result<Boolean> batchMove(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiInterfaceBatchMoveReqDTO reqDTO) {
        interfaceService.batchMove(projectId, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/interfaces/batch")
    public Result<Boolean> batchDelete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiInterfaceBatchDeleteReqDTO reqDTO) {
        interfaceService.batchDelete(projectId, reqDTO);
        return Result.ok(true);
    }

    @PutMapping("/api/project/interfaces/{id}/status")
    public Result<Boolean> updateStatus(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiInterfaceStatusReqDTO reqDTO) {
        interfaceService.updateStatus(projectId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PostMapping("/api/project/interfaces/{id}/follow")
    public Result<Boolean> follow(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        interfaceService.follow(projectId, loginUser.getId(), id);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/interfaces/{id}/follow")
    public Result<Boolean> unfollow(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        interfaceService.unfollow(projectId, loginUser.getId(), id);
        return Result.ok(true);
    }

    @GetMapping("/api/project/interfaces/{id}/change-logs")
    public Result<PageResult<ApiInterfaceChangeLogRespDTO>> changeLogs(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @Valid PageParam pageParam) {
        return Result.ok(interfaceService.changeLogs(projectId, id, pageParam));
    }

    // ==================== 3.2 公共步骤 ====================

    @PostMapping("/api/project/interfaces/{interfaceId}/steps")
    public Result<Map<String, UUID>> createStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID interfaceId,
            @RequestBody @Valid ApiInterfaceStepReqDTO reqDTO) {
        return Result.ok(Map.of("id", interfaceService.createStep(projectId, interfaceId, reqDTO)));
    }

    @PutMapping("/api/project/interfaces/{interfaceId}/steps/{stepId}")
    public Result<Boolean> updateStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID interfaceId,
            @PathVariable UUID stepId,
            @RequestBody @Valid ApiInterfaceStepReqDTO reqDTO) {
        interfaceService.updateStep(projectId, interfaceId, stepId, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/interfaces/{interfaceId}/steps/{stepId}")
    public Result<Boolean> deleteStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID interfaceId,
            @PathVariable UUID stepId) {
        interfaceService.deleteStep(projectId, interfaceId, stepId);
        return Result.ok(true);
    }

    @PutMapping("/api/project/interfaces/{interfaceId}/steps/{stepId}/sort")
    public Result<Boolean> sortStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID interfaceId,
            @PathVariable UUID stepId,
            @RequestBody @Valid ApiInterfaceStepSortReqDTO reqDTO) {
        interfaceService.sortStep(projectId, interfaceId, stepId, reqDTO);
        return Result.ok(true);
    }

    // ==================== 3.3 接口级变量 ====================

    @GetMapping("/api/project/interfaces/{interfaceId}/variables")
    public Result<List<ApiInterfaceVariableRespDTO>> listVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID interfaceId) {
        return Result.ok(interfaceService.listVariables(projectId, interfaceId));
    }

    @PutMapping("/api/project/interfaces/{interfaceId}/variables")
    public Result<Boolean> updateVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID interfaceId,
            @RequestBody @Valid ApiInterfaceVariablesReqDTO reqDTO) {
        interfaceService.updateVariables(projectId, interfaceId, reqDTO);
        return Result.ok(true);
    }

    // ==================== 3.4 导入 ====================

    @PostMapping(value = "/api/project/interfaces/import/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ApiImportResultRespDTO> importFile(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", required = false) String format) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("读取上传文件失败", exception);
        }
        return Result.ok(interfaceService.importFile(projectId, loginUser.getId(), content,
                file.getOriginalFilename(), format));
    }

    @PostMapping("/api/project/interfaces/import/url")
    public Result<ApiImportResultRespDTO> importUrl(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody Map<String, String> body) {
        return Result.ok(interfaceService.importUrl(projectId, loginUser.getId(),
                body.getOrDefault("url", ""), body.get("format")));
    }

    @PostMapping(value = "/api/project/interfaces/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ApiImportPreviewRespDTO> preview(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", required = false) String format) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("读取上传文件失败", exception);
        }
        return Result.ok(interfaceService.preview(projectId, content, format));
    }
}
