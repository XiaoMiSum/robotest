package io.github.xiaomisum.robotest.service.apitest;

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
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 场景执行与历史（测试场景详细设计 3.6/3.11、基础设施详细设计 3.2）
 */
public interface SceneExecutionService {

    /** 异步触发：落 pending 执行记录后入队，前端轮询 getStatus 至终态 */
    ApiExecutionStartRespDTO execute(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneExecuteReqDTO reqDTO);

    ApiExecutionStatusRespDTO getStatus(UUID workspaceId, UUID projectId, UUID userId, UUID executionId);

    /** 取消仅在步骤间生效，当前步骤完成后终止（测试场景详细设计 4.6） */
    ApiExecutionCancelRespDTO cancel(UUID workspaceId, UUID projectId, UUID userId, UUID executionId);

    /** 单步调试：同步执行，不产生执行记录与报告（测试场景详细设计 3.6.3） */
    ApiSceneStepDebugRespDTO debugStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            UUID stepId, ApiSceneStepDebugReqDTO reqDTO);

    /** 草稿单步调试：创建态未保存场景，使用页面实时数据（测试场景详细设计 3.6.3） */
    ApiSceneStepDebugRespDTO draftDebugStep(UUID workspaceId, UUID projectId, UUID userId,
            ApiSceneStepDraftDebugReqDTO reqDTO);

    /** 场景级草稿执行：创建态未保存场景，同步顺序执行全部草稿步骤（测试场景详细设计 3.6.4） */
    ApiSceneDraftExecuteRespDTO draftExecute(UUID workspaceId, UUID projectId, UUID userId,
            ApiSceneDraftExecuteReqDTO reqDTO);

    PageResult<ApiExecutionHistoryItemRespDTO> pageExecutions(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, PageParam pageParam);

    PageResult<ApiChangeHistoryItemRespDTO> pageChangeHistory(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, PageParam pageParam);

    /**
     * 同步执行一个（大）TestSuite 并返回原始 Ryze 结果树，不设超时（定时任务详细设计 4.3）。
     * 供测试计划任务把全部场景组织为单个顶层 TestSuite 一次执行后，按场景子 suite 的 metadata.sceneId
     * 从结果树递归反查各场景结果。异常由调用方捕获。
     */
    io.github.xiaomisum.ryze.Result startSuite(Map<String, Object> suite, UUID projectId) throws Exception;

    /**
     * 由单个场景子 TestSuite 的结果节点构建场景数据集（结果映射，不落库、不改变执行记录）。
     * 调度器用于从单一大 suite 结果树反查各场景数据集（定时任务详细设计 4.3）。
     */
    SceneDatasetSnapshot buildSceneDataset(ApiScene scene, DebugRyzeConverter.EnvSnapshot env,
            io.github.xiaomisum.ryze.Result result, LocalDateTime executedAt);

    /**
     * 前置/后置处理器结果节点 → 处理器执行明细（形状同步骤元素，测试报告详细设计 2.3）。
     * 供调度器把顶层（环境）处理器与各场景处理器纳入套件/场景数据集。
     */
    List<Map<String, Object>> toProcessorEntries(List<io.github.xiaomisum.ryze.Result> nodes);

    /** 场景数据集与状态/计数快照（测试报告详细设计 2.3.1） */
    record SceneDatasetSnapshot(Map<String, Object> dataset, String status, int passed, int failed,
            int skipped, long durationMs) {
    }

}
