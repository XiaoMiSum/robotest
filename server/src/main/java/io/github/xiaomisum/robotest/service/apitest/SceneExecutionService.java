package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiChangeHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionCancelRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStatusRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneStepDebugRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

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

    PageResult<ApiExecutionHistoryItemRespDTO> pageExecutions(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, PageParam pageParam);

    PageResult<ApiChangeHistoryItemRespDTO> pageChangeHistory(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, PageParam pageParam);

}
