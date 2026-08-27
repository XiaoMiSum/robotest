package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneAssetsImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneInterfaceAssociateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneInterfaceSyncModeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneSettingsReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepPublicStepReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepQuickCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepReorderReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepVariableImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneAssetsImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneAssociationItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScenePageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneQuickCreateRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneSettingsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiPublicStepBrowseItemRespDTO;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @Valid PageParam pageParam,
            @RequestParam(value = "moduleId", required = false) UUID moduleId,
            @RequestParam(value = "search", required = false) String search) {
        return Result.ok(sceneService.fetchPage(workspaceId, projectId, loginUser.getId(),
                moduleId, search, pageParam));
    }

    @GetMapping("/api/project/api-scenes/{id}")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<ApiSceneDetailRespDTO> getDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(sceneService.getDetail(workspaceId, projectId, loginUser.getId(), id));
    }

    @PostMapping("/api/project/api-scenes")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Map<String, String>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiSceneCreateReqDTO reqDTO) {
        return Result.ok(Map.of("id", sceneService.create(workspaceId, projectId,
                loginUser.getId(), reqDTO).toString()));
    }

    @PutMapping("/api/project/api-scenes/{id}")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneUpdateReqDTO reqDTO) {
        sceneService.update(workspaceId, projectId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/api-scenes/{id}")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        sceneService.delete(workspaceId, projectId, loginUser.getId(), id);
        return Result.ok(true);
    }

    @PostMapping("/api/project/api-scenes/{id}/copy")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Map<String, String>> copy(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneCopyReqDTO reqDTO) {
        return Result.ok(Map.of("id", sceneService.copy(workspaceId, projectId,
                loginUser.getId(), id, reqDTO).toString()));
    }

    @PutMapping("/api/project/api-scenes/{id}/settings")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> updateSettings(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneSettingsReqDTO reqDTO) {
        sceneService.updateSettings(workspaceId, projectId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PutMapping("/api/project/api-scenes/{id}/variables")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> updateVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneVariableBatchReqDTO reqDTO) {
        sceneService.updateVariables(workspaceId, projectId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    // ========== 步骤 ==========

    @PostMapping("/api/project/api-scenes/{id}/steps")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Map<String, String>> createStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneStepSaveReqDTO reqDTO) {
        return Result.ok(Map.of("id", sceneService.createStep(workspaceId, projectId,
                loginUser.getId(), id, reqDTO).toString()));
    }

    @PostMapping("/api/project/api-scenes/{id}/steps/quick-create")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<ApiSceneQuickCreateRespDTO> quickCreateSteps(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneStepQuickCreateReqDTO reqDTO) {
        return Result.ok(sceneService.quickCreateSteps(workspaceId, projectId,
                loginUser.getId(), id, reqDTO));
    }

    @PostMapping("/api/project/api-scenes/{id}/steps/public-step")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Map<String, String>> addPublicStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneStepPublicStepReqDTO reqDTO) {
        return Result.ok(Map.of("id", sceneService.addPublicStep(workspaceId, projectId,
                loginUser.getId(), id, reqDTO).toString()));
    }

    @PutMapping("/api/project/api-scenes/{id}/steps/reorder")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> reorderSteps(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneStepReorderReqDTO reqDTO) {
        sceneService.reorderSteps(workspaceId, projectId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PutMapping("/api/project/api-scenes/{id}/steps/{stepId}")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> updateStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @RequestBody @Valid ApiSceneStepSaveReqDTO reqDTO) {
        sceneService.updateStep(workspaceId, projectId, loginUser.getId(), id, stepId, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/api-scenes/{id}/steps/{stepId}")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> deleteStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID stepId) {
        sceneService.deleteStep(workspaceId, projectId, loginUser.getId(), id, stepId);
        return Result.ok(true);
    }

    @PostMapping("/api/project/api-scenes/{id}/steps/{stepId}/copy")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Map<String, String>> copyStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @RequestBody @Valid ApiSceneStepCopyReqDTO reqDTO) {
        return Result.ok(Map.of("id", sceneService.copyStep(workspaceId, projectId,
                loginUser.getId(), id, stepId, reqDTO).toString()));
    }

    // ========== 步骤级变量 ==========

    @GetMapping("/api/project/api-scenes/{id}/steps/{stepId}/variables")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<List<Map<String, Object>>> listStepVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID stepId) {
        return Result.ok(sceneService.listStepVariables(workspaceId, projectId,
                loginUser.getId(), id, stepId));
    }

    @PutMapping("/api/project/api-scenes/{id}/steps/{stepId}/variables")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> updateStepVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @RequestBody @Valid ApiSceneStepVariableBatchReqDTO reqDTO) {
        sceneService.updateStepVariables(workspaceId, projectId, loginUser.getId(), id, stepId, reqDTO);
        return Result.ok(true);
    }

    @PostMapping("/api/project/api-scenes/{id}/steps/{stepId}/variables/import")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<List<Map<String, Object>>> importStepVariables(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @RequestBody @Valid ApiSceneStepVariableImportReqDTO reqDTO) {
        return Result.ok(sceneService.importStepVariables(workspaceId, projectId,
                loginUser.getId(), id, stepId, reqDTO));
    }

    // ========== 场景关联接口 ==========

    @GetMapping("/api/project/api-scenes/{id}/associations")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<List<ApiSceneAssociationItemRespDTO>> listAssociations(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(sceneService.listAssociations(workspaceId, projectId, loginUser.getId(), id));
    }

    @PostMapping("/api/project/api-scenes/{id}/associations")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> associateInterfaces(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneInterfaceAssociateReqDTO reqDTO) {
        sceneService.associateInterfaces(workspaceId, projectId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/api-scenes/{id}/associations/{associationId}")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> unassociateInterface(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID associationId) {
        sceneService.unassociateInterface(workspaceId, projectId, loginUser.getId(), id, associationId);
        return Result.ok(true);
    }

    @PutMapping("/api/project/api-scenes/{id}/associations/{associationId}/sync-mode")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> switchSyncMode(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @PathVariable UUID associationId,
            @RequestBody @Valid ApiSceneInterfaceSyncModeReqDTO reqDTO) {
        sceneService.switchSyncMode(workspaceId, projectId, loginUser.getId(), id, associationId, reqDTO);
        return Result.ok(true);
    }

    // ========== 场景设置 ==========

    @GetMapping("/api/project/api-scenes/{id}/settings")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<ApiSceneSettingsRespDTO> getSettings(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(sceneService.getSettings(workspaceId, projectId, loginUser.getId(), id));
    }

    // ========== 全局资产引入 ==========

    @PostMapping("/api/project/api-scenes/{id}/assets/import")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<ApiSceneAssetsImportRespDTO> importAssets(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSceneAssetsImportReqDTO reqDTO) {
        return Result.ok(sceneService.importAssets(workspaceId, projectId, loginUser.getId(), id, reqDTO));
    }

    // ========== 关注 ==========

    @PostMapping("/api/project/api-scenes/{id}/follow")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> follow(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        sceneService.follow(workspaceId, projectId, loginUser.getId(), id);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/api-scenes/{id}/follow")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> unfollow(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        sceneService.unfollow(workspaceId, projectId, loginUser.getId(), id);
        return Result.ok(true);
    }

    // ========== 批量操作 ==========

    @DeleteMapping("/api/project/api-scenes/batch")
    @PreAuthorize("hasAuthority('api-scene:edit')")
    public Result<Boolean> batchDelete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiSceneBatchDeleteReqDTO reqDTO) {
        sceneService.batchDelete(workspaceId, projectId, loginUser.getId(), reqDTO);
        return Result.ok(true);
    }

    // ========== 公共步骤浏览 ==========

    @GetMapping("/api/project/api-scenes/{id}/public-steps")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<List<ApiPublicStepBrowseItemRespDTO>> browsePublicSteps(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(sceneService.browsePublicSteps(workspaceId, projectId, loginUser.getId(), id));
    }
}
