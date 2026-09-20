package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneAssetsImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneBatchMoveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepQuickCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepReorderReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneAssetsImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScenePageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneQuickCreateRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneFollow;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneFollowMapper;
import io.github.xiaomisum.robotest.repository.apitest.CommonComponentMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCENE_REFERENCED;

/**
 * 测试场景管理实现（测试场景详细设计 3.1-3.5、3.9-3.10）。
 * <p>
 * 职责边界（接口测试域重构方案 04 §4.2）：仅保留权限/事务编排与变更历史；装配/步骤编辑/移动/资产引入
 * 分别下沉到 SceneDetailAssembler、SceneStepEditor、SceneMoveOrchestrator、SceneAssetImporter。 */
@Service
public class ApiSceneServiceImpl implements ApiSceneService {

    private static final String TARGET_TYPE_SCENE = "scene";

    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ApiExecutionRecordMapper executionRecordMapper;
    @Resource
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Resource
    private ApiInterfaceMapper interfaceMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private CommonComponentMapper componentMapper;
    @Resource
    private TestPlanSceneGuard testPlanSceneGuard;
    @Resource
    private ApiSceneFollowMapper sceneFollowMapper;
    @Resource
    private ProjectModuleMapper moduleMapper;

    // ========== 场景管理 ==========

    @Override
    public PageResult<ApiScenePageItemRespDTO> fetchPage(UUID workspaceId, UUID projectId, UUID userId,
            UUID moduleId, String search, Boolean followedOnly, String status, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        return SceneDetailAssembler.fetchPage(sceneMapper, sceneFollowMapper, executionRecordMapper,
                projectId, moduleId, search, followedOnly, status, pageParam, userId);
    }

    @Override
    public ApiSceneDetailRespDTO getDetail(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, id);
        boolean followed = sceneFollowMapper.selectBySceneAndUser(id, userId) != null;
        return SceneDetailAssembler.toDetail(scene, followed, sourceId -> interfaceMapper.selectById(sourceId) != null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID create(UUID workspaceId, UUID projectId, UUID userId, ApiSceneCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        SceneSettingsValidator.validatePriority(reqDTO.getPriority());
        SceneSettingsValidator.validateStatus(reqDTO.getStatus());
        ApiScene scene = SceneSettingsValidator.buildCreateScene(projectId, reqDTO);
        sceneMapper.insert(scene);

        writeHistory(projectId, scene.getId(), "create", "创建场景", userId);
        return scene.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiSceneUpdateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, id);
        SceneSettingsValidator.validatePriority(reqDTO.getPriority());
        SceneSettingsValidator.validateStatus(reqDTO.getStatus());

        // 乐观锁：版本号不匹配即 0 行更新（测试场景详细设计 3.1.4）
        int rows = sceneMapper.update(SceneSettingsValidator.buildUpdateCarrier(id, reqDTO,
                reqDTO.getChangeVersion() + 1), new LambdaUpdateWrapperX<ApiScene>()
                        .eq(ApiScene::getId, id)
                        .eq(ApiScene::getChangeVersion, reqDTO.getChangeVersion()));
        if (rows == 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_VERSION_CONFLICT);
        }
        // 步骤随场景在同一事务内一并落库：任一失败整事务回滚，change_version 不会误增
        if (reqDTO.getSteps() != null && !reqDTO.getSteps().isEmpty()) {
            persistSteps(id, SceneStepEditor.mergeSteps(scene.getSteps(), reqDTO.getSteps()));
        }
        writeHistory(projectId, id, "update", "更新场景", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, id);
        // 删除保护（7302）：被测试计划任务选中的场景不可删除（定时任务详细设计 4.2）
        if (testPlanSceneGuard.isSceneReferenced(projectId, id, scene.getModuleId())) {
            throw ServiceExceptionUtil.get(API_SCENE_REFERENCED);
        }
        sceneMapper.deleteById(id);
        sceneFollowMapper.deleteBySceneId(id);
    }

    // ========== 步骤管理 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID createStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneStepSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        SceneStepEditor.validateStepType(reqDTO.getStepType());
        List<Map<String, Object>> steps = scene.getSteps() == null ? new ArrayList<>() : new ArrayList<>(scene.getSteps());
        Map<String, Object> step = SceneStepEditor.newStep(reqDTO);
        step.put("sortOrder", reqDTO.getSortOrder() == null
                ? SceneStepUtil.maxSortOrder(steps) + 1 : reqDTO.getSortOrder());
        UUID stepId = SceneStepUtil.getUUID(step, "id");
        steps.add(step);
        persistSteps(sceneId, steps);
        return stepId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiSceneQuickCreateRespDTO quickCreateSteps(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, ApiSceneStepQuickCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        String mode = SceneSettingsValidator.normalizeMode(reqDTO.getMode());
        ApiInterface apiInterface = interfaceMapper.selectById(reqDTO.getInterfaceId());
        if (apiInterface == null || !projectId.equals(apiInterface.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_INTERFACE_NOT_FOUND);
        }

        List<Map<String, Object>> steps = scene.getSteps() == null ? new ArrayList<>() : new ArrayList<>(scene.getSteps());
        int order = SceneStepUtil.maxSortOrder(steps) + 1;
        Map<String, Object> mainStep = SceneStepEditor.newStepFromInterface(apiInterface, mode, order++);
        steps.add(mainStep);
        List<ApiSceneQuickCreateRespDTO.CreatedStep> created = new ArrayList<>();
        created.add(SceneStepEditor.toCreatedStep(mainStep));
        persistSteps(sceneId, steps);
        return ApiSceneQuickCreateRespDTO.builder().steps(created).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId,
            ApiSceneStepSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        Map<String, Object> step = SceneStepUtil.requireStep(scene.getSteps(), stepId);
        SceneStepEditor.partialUpdateStep(step, reqDTO);
        persistSteps(sceneId, scene.getSteps());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        int idx = SceneStepUtil.findStepIndex(scene.getSteps(), stepId);
        if (idx < 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_STEP_NOT_FOUND);
        }
        scene.getSteps().remove(idx);
        persistSteps(sceneId, scene.getSteps());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderSteps(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneStepReorderReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        List<Map<String, Object>> existing = scene.getSteps() == null ? List.of() : scene.getSteps();
        persistSteps(sceneId, SceneStepEditor.reorder(existing, reqDTO.getStepIds()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID copyStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId,
            ApiSceneStepCopyReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        Map<String, Object> origin = SceneStepUtil.requireStep(scene.getSteps(), stepId);
        String name = reqDTO != null && reqDTO.getName() != null && !reqDTO.getName().isBlank()
                ? reqDTO.getName() : SceneStepUtil.getString(origin, "name", "步骤") + "（副本）";
        Map<String, Object> copied = SceneStepEditor.copyStep(origin, name, scene.getSteps());
        UUID copiedId = SceneStepUtil.getUUID(copied, "id");
        scene.getSteps().add(copied);
        persistSteps(sceneId, scene.getSteps());
        return copiedId;
    }

    // ========== 步骤级变量 ==========

    @Override
    public List<Map<String, Object>> listStepVariables(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, UUID stepId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        Map<String, Object> step = SceneStepUtil.requireStep(scene.getSteps(), stepId);
        return new ArrayList<>(SceneStepUtil.getList(step, "variables"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStepVariables(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId,
            io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepVariableBatchReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        Map<String, Object> step = SceneStepUtil.requireStep(scene.getSteps(), stepId);
        step.put("variables", SceneStepEditor.buildStepVariables(reqDTO.getVariables()));
        persistSteps(sceneId, scene.getSteps());
    }

    // ========== 全局资产引入 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiSceneAssetsImportRespDTO importAssets(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneAssetsImportReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        Map<String, Object> step = SceneAssetImporter.resolveTarget(scene, reqDTO.getTarget(), reqDTO.getStepId());
        int imported = SceneAssetImporter.apply(sceneMapper, componentMapper, scene, step,
                reqDTO.getTarget(), reqDTO.getAssetIds());
        writeHistory(projectId, sceneId, "update", "从全局资产引入 " + imported + " 个", userId);
        return ApiSceneAssetsImportRespDTO.builder().imported(imported).build();
    }

    // ========== 关注 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        ApiSceneFollow existing = sceneFollowMapper.selectBySceneAndUser(sceneId, userId);
        if (existing == null) {
            ApiSceneFollow follow = new ApiSceneFollow();
            follow.setSceneId(sceneId);
            follow.setUserId(userId);
            sceneFollowMapper.insert(follow);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        ApiSceneFollow existing = sceneFollowMapper.selectBySceneAndUser(sceneId, userId);
        if (existing != null) {
            sceneFollowMapper.deleteById(existing.getId());
        }
    }

    // ========== 批量操作 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMove(UUID workspaceId, UUID projectId, UUID userId, ApiSceneBatchMoveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        SceneMoveOrchestrator.move(sceneMapper, moduleMapper, projectId, reqDTO.getIds(), reqDTO.getModuleId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(UUID workspaceId, UUID projectId, UUID userId,
            ApiSceneBatchDeleteReqDTO reqDTO) {
        for (UUID id : reqDTO.getIds()) {
            delete(workspaceId, projectId, userId, id);
        }
    }

    // ========== 内部工具 ==========

    private ApiScene requireScene(UUID projectId, UUID id) {
        ApiScene scene = sceneMapper.selectById(id);
        if (scene == null || !scene.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
        return scene;
    }

    /** 变更历史版本号独立递增，同一对象内唯一（基础设施详细设计 2.1.2） */
    private void writeHistory(UUID projectId, UUID targetId, String changeType, String summary, UUID userId) {
        ApiChangeHistory history = new ApiChangeHistory();
        history.setId(UUID.randomUUID());
        history.setProjectId(projectId);
        history.setTargetType(TARGET_TYPE_SCENE);
        history.setTargetId(targetId);
        history.setVersion(changeHistoryMapper.selectMaxVersion(TARGET_TYPE_SCENE, targetId) + 1);
        history.setChangeType(changeType);
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("summary", summary);
        history.setContentDiff(diff);
        history.setCreatedBy(userId);
        changeHistoryMapper.insert(history);
    }

    /** 以 carrier（仅 id + steps）向 api_scene 落步骤列（C9 部分更新） */
    private void persistSteps(UUID sceneId, List<Map<String, Object>> steps) {
        ApiScene carrier = new ApiScene();
        carrier.setId(sceneId);
        carrier.setSteps(steps);
        sceneMapper.updateById(carrier);
    }
}