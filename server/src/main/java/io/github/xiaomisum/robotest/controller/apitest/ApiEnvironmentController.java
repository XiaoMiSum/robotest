package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentProcessorSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDataSourceTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentProcessorRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentSetDefaultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentVariableRevealRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentVariableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiHttpTestRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiEnvironmentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project/environments")
public class ApiEnvironmentController {

    @Resource
    private ApiEnvironmentService apiEnvironmentService;

    @GetMapping
    @PreAuthorize("hasAuthority('api-env:view')")
    public Result<List<ApiEnvironmentListItemRespDTO>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(apiEnvironmentService.fetchEnvironments(projectId, workspaceId, loginUser.getId(), keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<ApiEnvironmentIdRespDTO> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiEnvironmentSaveReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.createEnvironment(projectId, workspaceId, loginUser.getId(), reqDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('api-env:view')")
    public Result<ApiEnvironmentDetailRespDTO> detail(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(apiEnvironmentService.getEnvironment(projectId, workspaceId, loginUser.getId(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiEnvironmentSaveReqDTO reqDTO) {
        apiEnvironmentService.updateEnvironment(projectId, workspaceId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        apiEnvironmentService.deleteEnvironment(projectId, workspaceId, loginUser.getId(), id);
        return Result.ok(true);
    }

    @PatchMapping("/{id}/set-default")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<ApiEnvironmentSetDefaultRespDTO> setDefault(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(apiEnvironmentService.setDefaultEnvironment(projectId, workspaceId, loginUser.getId(), id));
    }

    @PatchMapping("/{id}/sort")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<Boolean> sort(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiEnvironmentSortReqDTO reqDTO) {
        apiEnvironmentService.sortEnvironment(projectId, workspaceId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<ApiEnvironmentIdRespDTO> copy(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiEnvironmentCopyReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.copyEnvironment(projectId, workspaceId, loginUser.getId(), id, reqDTO));
    }

    // ========== 处理器子资源（3.2） ==========

    @GetMapping("/{id}/processors")
    @PreAuthorize("hasAuthority('api-env:view')")
    public Result<List<ApiEnvironmentProcessorRespDTO>> listProcessors(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestParam(value = "processorType", required = false) String processorType) {
        return Result.ok(apiEnvironmentService.listProcessors(projectId, workspaceId, loginUser.getId(),
                id, processorType));
    }

    @PostMapping("/{id}/processors")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<ApiEnvironmentProcessorRespDTO> createProcessor(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiEnvironmentProcessorSaveReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.createProcessor(projectId, workspaceId, loginUser.getId(),
                id, reqDTO));
    }

    @PutMapping("/{id}/processors/{procId}")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<ApiEnvironmentProcessorRespDTO> updateProcessor(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID procId,
            @RequestBody @Valid ApiEnvironmentProcessorSaveReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.updateProcessor(projectId, workspaceId, loginUser.getId(),
                id, procId, reqDTO));
    }

    @DeleteMapping("/{id}/processors/{procId}")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<Boolean> deleteProcessor(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID procId) {
        apiEnvironmentService.deleteProcessor(projectId, workspaceId, loginUser.getId(), id, procId);
        return Result.ok(true);
    }

    // ========== 变量子资源（3.3） ==========

    @PutMapping("/{id}/variables")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<List<ApiEnvironmentVariableRespDTO>> batchReplaceVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiEnvironmentVariableBatchReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.batchReplaceVariables(projectId, workspaceId, loginUser.getId(),
                id, reqDTO));
    }

    @PostMapping("/{id}/variables")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<ApiEnvironmentVariableRespDTO> addVariableFromResult(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiEnvironmentVariableCreateReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.addVariableFromResult(projectId, workspaceId, loginUser.getId(),
                id, reqDTO));
    }

    @PostMapping("/{id}/variables/import")
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<ApiEnvImportResultRespDTO> importVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiEnvironmentVariableImportReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.importVariables(projectId, workspaceId, loginUser.getId(),
                id, reqDTO));
    }

    @GetMapping("/{id}/variables/export")
    @PreAuthorize("hasAuthority('api-env:view')")
    public Result<List<ApiEnvironmentVariableRespDTO>> exportVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(apiEnvironmentService.exportVariables(projectId, workspaceId, loginUser.getId(), id));
    }

    @PostMapping("/{id}/variables/{variableId}/reveal")
    @PreAuthorize("hasAuthority('api-env:view')")
    public Result<ApiEnvironmentVariableRevealRespDTO> revealVariable(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID variableId) {
        return Result.ok(apiEnvironmentService.revealVariable(projectId, workspaceId, loginUser.getId(),
                id, variableId));
    }

    // ========== 连接测试（3.1.7 / 3.1.8） ==========

    @PostMapping("/{id}/data-sources/{dsId}/test")
    @PreAuthorize("hasAuthority('api-env:view')")
    public Result<ApiDataSourceTestRespDTO> testDataSource(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID dsId) {
        return Result.ok(apiEnvironmentService.testDataSource(projectId, workspaceId, loginUser.getId(), id, dsId));
    }

    @PostMapping("/{id}/http-configs/{httpId}/test")
    @PreAuthorize("hasAuthority('api-env:view')")
    public Result<ApiHttpTestRespDTO> testHttpConfig(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID httpId) {
        return Result.ok(apiEnvironmentService.testHttpConfig(projectId, workspaceId, loginUser.getId(), id, httpId));
    }

    // ========== 环境导入导出（3.1.9 / 3.1.10） ==========

    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('api-env:view')")
    public Result<ApiEnvironmentDetailRespDTO> exportEnvironment(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(apiEnvironmentService.exportEnvironment(projectId, workspaceId, loginUser.getId(), id));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('api-env:edit')")
    public Result<ApiEnvImportResultRespDTO> importEnvironment(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        return Result.ok(apiEnvironmentService.importEnvironment(projectId, workspaceId, loginUser.getId(),
                file, overwrite));
    }
}
