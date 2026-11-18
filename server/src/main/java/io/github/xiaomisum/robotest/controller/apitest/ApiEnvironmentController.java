package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDataSourceTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiHttpConfigTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDataSourceTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentSetDefaultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiHttpTestRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiEnvironmentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
    public Result<List<ApiEnvironmentListItemRespDTO>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(apiEnvironmentService.fetchEnvironments(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), keyword));
    }

    @PostMapping
    public Result<ApiEnvironmentIdRespDTO> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiEnvironmentSaveReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.createEnvironment(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), reqDTO));
    }

    @GetMapping("/{id}")
    public Result<ApiEnvironmentDetailRespDTO> detail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(apiEnvironmentService.getEnvironment(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), id));
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiEnvironmentSaveReqDTO reqDTO) {
        apiEnvironmentService.updateEnvironment(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        apiEnvironmentService.deleteEnvironment(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    @PatchMapping("/{id}/set-default")
    public Result<ApiEnvironmentSetDefaultRespDTO> setDefault(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(apiEnvironmentService.setDefaultEnvironment(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), id));
    }

    @PatchMapping("/{id}/sort")
    public Result<Boolean> sort(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiEnvironmentSortReqDTO reqDTO) {
        apiEnvironmentService.sortEnvironment(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    // ========== 连接测试（3.1.7 / 3.1.8，请求体传配置不落库） ==========

    @PostMapping("/{id}/data-sources/test")
    public Result<ApiDataSourceTestRespDTO> testDataSourceConfig(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @Valid @RequestBody ApiDataSourceTestReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.testDataSourceConfig(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), id, reqDTO));
    }

    @PostMapping("/{id}/http-configs/test")
    public Result<ApiHttpTestRespDTO> testHttpConfig(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @Valid @RequestBody ApiHttpConfigTestReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.testHttpConfig(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), id, reqDTO));
    }

    // ========== 环境导入导出（3.1.9 / 3.1.10） ==========

    @GetMapping("/{id}/export")
    public Result<ApiEnvironmentDetailRespDTO> exportEnvironment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(apiEnvironmentService.exportEnvironment(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(), id));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ApiEnvImportResultRespDTO> importEnvironment(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        return Result.ok(apiEnvironmentService.importEnvironment(loginUser.getActiveProjectId(), loginUser.getActiveWorkspaceId(), loginUser.getId(),
                file, overwrite));
    }
}
