package io.github.xiaomisum.robotest.service.apitest.execution;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneDraftExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepDraftDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiChangeHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionCancelRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStatusRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDraftExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneStepDebugRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 场景执行门面（04 §4.1 步骤 4）：不承载任何执行逻辑，仅按业务维度委托
 * {@link SceneExecutionLauncher}（异步编排）/ {@link DraftExecutionService}（调试/草稿）/
 * {@link ExecutionRecordQueryService}（历史查询）/ {@link SceneDatasetBuilder}（结果数据集）。
 */
@Service
public class SceneExecutionServiceImpl implements SceneExecutionService {

    @Resource
    private SceneExecutionLauncher launcher;
    @Resource
    private DraftExecutionService draftExecutionService;
    @Resource
    private ExecutionRecordQueryService executionRecordQueryService;
    @Resource
    private SceneDatasetBuilder datasetBuilder;

    @Override
    public ApiExecutionStartRespDTO execute(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneExecuteReqDTO reqDTO) {
        return launcher.execute(workspaceId, projectId, userId, sceneId, reqDTO);
    }

    @Override
    public ApiExecutionStatusRespDTO getStatus(UUID workspaceId, UUID projectId, UUID userId, UUID executionId) {
        return launcher.getStatus(workspaceId, projectId, userId, executionId);
    }

    @Override
    public ApiExecutionCancelRespDTO cancel(UUID workspaceId, UUID projectId, UUID userId, UUID executionId) {
        return launcher.cancel(workspaceId, projectId, userId, executionId);
    }

    @Override
    public ApiSceneStepDebugRespDTO debugStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            UUID stepId, ApiSceneStepDebugReqDTO reqDTO) {
        return draftExecutionService.debugStep(workspaceId, projectId, userId, sceneId, stepId, reqDTO);
    }

    @Override
    public ApiSceneStepDebugRespDTO draftDebugStep(UUID workspaceId, UUID projectId, UUID userId,
            ApiSceneStepDraftDebugReqDTO reqDTO) {
        return draftExecutionService.draftDebugStep(workspaceId, projectId, userId, reqDTO);
    }

    @Override
    public ApiSceneDraftExecuteRespDTO draftExecute(UUID workspaceId, UUID projectId, UUID userId,
            ApiSceneDraftExecuteReqDTO reqDTO) {
        return draftExecutionService.draftExecute(workspaceId, projectId, userId, reqDTO);
    }

    @Override
    public PageResult<ApiExecutionHistoryItemRespDTO> pageExecutions(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, PageParam pageParam) {
        return executionRecordQueryService.pageExecutions(workspaceId, projectId, userId, sceneId, pageParam);
    }

    @Override
    public PageResult<ApiChangeHistoryItemRespDTO> pageChangeHistory(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, PageParam pageParam) {
        return executionRecordQueryService.pageChangeHistory(workspaceId, projectId, userId, sceneId, pageParam);
    }

    @Override
    public MappedResult startSuite(Map<String, Object> suite, UUID projectId) throws Exception {
        return launcher.startSuite(suite, projectId);
    }

    @Override
    public SceneDatasetSnapshot buildSceneDataset(ApiScene scene, EnvSnapshot env,
            MappedResult result, LocalDateTime executedAt) {
        return datasetBuilder.buildSceneDataset(scene, env, result, executedAt);
    }

    @Override
    public List<Map<String, Object>> toProcessorEntries(List<MappedResult> nodes) {
        return datasetBuilder.toProcessorEntries(nodes);
    }
}