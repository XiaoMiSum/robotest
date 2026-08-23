package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentSetDefaultRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiEnvironmentService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(apiEnvironmentService.fetchEnvironments(projectId, workspaceId, loginUser.getId(), keyword));
    }

    @PostMapping
    public Result<ApiEnvironmentIdRespDTO> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiEnvironmentSaveReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.createEnvironment(projectId, workspaceId, loginUser.getId(), reqDTO));
    }

    @GetMapping("/{id}")
    public Result<ApiEnvironmentDetailRespDTO> detail(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(apiEnvironmentService.getEnvironment(projectId, workspaceId, loginUser.getId(), id));
    }

    @PutMapping("/{id}")
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
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        apiEnvironmentService.deleteEnvironment(projectId, workspaceId, loginUser.getId(), id);
        return Result.ok(true);
    }

    @PatchMapping("/{id}/set-default")
    public Result<ApiEnvironmentSetDefaultRespDTO> setDefault(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(apiEnvironmentService.setDefaultEnvironment(projectId, workspaceId, loginUser.getId(), id));
    }

    @PatchMapping("/{id}/sort")
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
    public Result<ApiEnvironmentIdRespDTO> copy(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiEnvironmentCopyReqDTO reqDTO) {
        return Result.ok(apiEnvironmentService.copyEnvironment(projectId, workspaceId, loginUser.getId(), id, reqDTO));
    }
}
