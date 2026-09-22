package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneDraftExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiChangeHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDraftExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneStepDebugRespDTO;
import io.github.xiaomisum.robotest.service.apitest.execution.SceneExecutionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/** 场景执行/调试/历史路由（测试场景详细设计 3.6/3.11、基础设施详细设计 3.2） */
@RestController
public class ApiSceneExecutionController {

    @Resource
    private SceneExecutionService executionService;

    @PostMapping("/api/project/api-scenes/{sceneId}/executions")
    @PreAuthorize("hasAuthority('api-scene:execute')")
    public Result<ApiExecutionStartRespDTO> execute(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID sceneId,
            @RequestBody(required = false) @Valid ApiSceneExecuteReqDTO reqDTO) {
        return Result.ok(executionService.execute(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(),
                loginUser.getId(), sceneId, reqDTO));
    }

    @GetMapping("/api/project/api-scenes/{sceneId}/executions")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<PageResult<ApiExecutionHistoryItemRespDTO>> pageExecutions(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID sceneId,
            @Valid PageParam pageParam) {
        return Result.ok(executionService.pageExecutions(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(),
                loginUser.getId(), sceneId, pageParam));
    }

    @GetMapping("/api/project/api-scenes/{sceneId}/change-history")
    @PreAuthorize("hasAuthority('api-scene:view')")
    public Result<PageResult<ApiChangeHistoryItemRespDTO>> pageChangeHistory(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID sceneId,
            @Valid PageParam pageParam) {
        return Result.ok(executionService.pageChangeHistory(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(),
                loginUser.getId(), sceneId, pageParam));
    }

    // ========== 单步调试 ==========

    @PostMapping("/api/project/api-scenes/{sceneId}/steps/{stepId}/debug")
    @PreAuthorize("hasAuthority('api-scene:execute')")
    public Result<ApiSceneStepDebugRespDTO> debugStep(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID sceneId,
            @PathVariable UUID stepId,
            @RequestBody @Valid ApiSceneStepDebugReqDTO reqDTO) {
        return Result.ok(executionService.debugStep(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(),
                loginUser.getId(), sceneId, stepId, reqDTO));
    }

    @PostMapping("/api/project/api-scenes/draft/execute")
    @PreAuthorize("hasAuthority('api-scene:execute')")
    public Result<ApiSceneDraftExecuteRespDTO> draftExecute(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiSceneDraftExecuteReqDTO reqDTO) {
        return Result.ok(executionService.draftExecute(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(),
                loginUser.getId(), reqDTO));
    }
}
