package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneBatchMoveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCreateReqDTO;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepQuickCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepReorderReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScenePageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneQuickCreateRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiSceneService;
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

/** 测试场景管理路由（测试场景详细设计 3.1-3.5、3.9-3.10） */
@RestController
public class ApiSceneController {

    @Resource
    private ApiSceneService sceneService;

    @GetMapping("/api/project/api-scenes")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<PageResult<ApiScenePageItemRespDTO>> fetchPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid PageParam pageParam,
            @RequestParam(value = "moduleId", required = false) UUID moduleId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "followedOnly", required = false) Boolean followedOnly,
            @RequestParam(value = "status", required = false) String status) {
        return Result.ok(sceneService.fetchPage(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(),
                moduleId, search, followedOnly, status, pageParam));
    }

    @GetMapping("/api/project/api-scenes/{id}")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<ApiSceneDetailRespDTO> getDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(sceneService.getDetail(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id));
    }

    @PostMapping("/api/project/api-scenes")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Map<String, String>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiSceneCreateReqDTO reqDTO) {
        return Result.ok(Map.of("id", sceneService.create(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(),
                loginUser.getId(), reqDTO).toString()));
    }

    @PutMapping("/api/project/api-scenes/{id}")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneUpdateReqDTO reqDTO) {
        sceneService.update(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/api-scenes/{id}")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        sceneService.delete(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    // ========== 步骤 ==========

    @PostMapping("/api/project/api-scenes/{id}/steps")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Map<String, String>> createStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneStepSaveReqDTO reqDTO) {
        return Result.ok(Map.of("id", sceneService.createStep(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(),
                loginUser.getId(), id, reqDTO).toString()));
    }

    @PostMapping("/api/project/api-scenes/{id}/steps/quick-create")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<ApiSceneQuickCreateRespDTO> quickCreateSteps(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneStepQuickCreateReqDTO reqDTO) {
        return Result.ok(sceneService.quickCreateSteps(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(),
                loginUser.getId(), id, reqDTO));
    }

    @PutMapping("/api/project/api-scenes/{id}/steps/reorder")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> reorderSteps(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneStepReorderReqDTO reqDTO) {
        sceneService.reorderSteps(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PutMapping("/api/project/api-scenes/{id}/steps/{stepId}")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> updateStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @RequestBody @Valid ApiSceneStepSaveReqDTO reqDTO) {
        sceneService.updateStep(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, stepId, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/api-scenes/{id}/steps/{stepId}")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> deleteStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @PathVariable UUID stepId) {
        sceneService.deleteStep(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, stepId);
        return Result.ok(true);
    }

    @PostMapping("/api/project/api-scenes/{id}/steps/{stepId}/copy")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Map<String, String>> copyStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @RequestBody @Valid ApiSceneStepCopyReqDTO reqDTO) {
        return Result.ok(Map.of("id", sceneService.copyStep(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(),
                loginUser.getId(), id, stepId, reqDTO).toString()));
    }

    // ========== 步骤级变量 ==========

    @GetMapping("/api/project/api-scenes/{id}/steps/{stepId}/variables")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<List<Map<String, Object>>> listStepVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @PathVariable UUID stepId) {
        return Result.ok(sceneService.listStepVariables(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(),
                loginUser.getId(), id, stepId));
    }

    @PutMapping("/api/project/api-scenes/{id}/steps/{stepId}/variables")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> updateStepVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @RequestBody @Valid ApiSceneStepVariableBatchReqDTO reqDTO) {
        sceneService.updateStepVariables(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, stepId, reqDTO);
        return Result.ok(true);
    }

    // ========== 关注 ==========

    @PostMapping("/api/project/api-scenes/{id}/follow")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> follow(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        sceneService.follow(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/api-scenes/{id}/follow")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> unfollow(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        sceneService.unfollow(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    // ========== 批量操作 ==========

    @DeleteMapping("/api/project/api-scenes/batch")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> batchDelete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiSceneBatchDeleteReqDTO reqDTO) {
        sceneService.batchDelete(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), reqDTO);
        return Result.ok(true);
    }

    @PutMapping("/api/project/api-scenes/batch/move")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> batchMove(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiSceneBatchMoveReqDTO reqDTO) {
        sceneService.batchMove(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), reqDTO);
        return Result.ok(true);
    }
}
