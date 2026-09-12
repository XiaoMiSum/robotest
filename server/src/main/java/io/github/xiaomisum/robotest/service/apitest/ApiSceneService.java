package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneAssetsImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneBatchMoveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepQuickCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepReorderReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneAssetsImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScenePageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneQuickCreateRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 测试场景管理（测试场景详细设计 3.1-3.5、3.9-3.10）；执行与历史见 SceneExecutionService
 */
public interface ApiSceneService {

    // ========== 场景管理 ==========

    PageResult<ApiScenePageItemRespDTO> fetchPage(UUID workspaceId, UUID projectId, UUID userId,
            UUID moduleId, String search, Boolean followedOnly, String status, PageParam pageParam);

    ApiSceneDetailRespDTO getDetail(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    UUID create(UUID workspaceId, UUID projectId, UUID userId, ApiSceneCreateReqDTO reqDTO);

    void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiSceneUpdateReqDTO reqDTO);

    /** 被定时任务引用时拒绝删除（7203）；定时任务于后续迭代交付，当前无引用来源 */
    void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    /** 批量删除场景（被定时任务引用的拒绝删除） */
    void batchDelete(UUID workspaceId, UUID projectId, UUID userId, ApiSceneBatchDeleteReqDTO reqDTO);

    /** 批量移动场景至目标模块（3.1.7），目标模块空为未分组；任一场景不属于当前项目则整体拒绝 */
    void batchMove(UUID workspaceId, UUID projectId, UUID userId, ApiSceneBatchMoveReqDTO reqDTO);

    // ========== 关注 ==========

    void follow(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId);

    void unfollow(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId);

    // ========== 步骤管理 ==========

    UUID createStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneStepSaveReqDTO reqDTO);

    ApiSceneQuickCreateRespDTO quickCreateSteps(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, ApiSceneStepQuickCreateReqDTO reqDTO);

    void updateStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId,
            ApiSceneStepSaveReqDTO reqDTO);

    void deleteStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId);

    void reorderSteps(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneStepReorderReqDTO reqDTO);

    UUID copyStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId,
            ApiSceneStepCopyReqDTO reqDTO);

    // ========== 步骤级变量 ==========

    List<Map<String, Object>> listStepVariables(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, UUID stepId);

    void updateStepVariables(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId,
            ApiSceneStepVariableBatchReqDTO reqDTO);

    // ========== 全局资产引入 ==========

    ApiSceneAssetsImportRespDTO importAssets(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneAssetsImportReqDTO reqDTO);

}
