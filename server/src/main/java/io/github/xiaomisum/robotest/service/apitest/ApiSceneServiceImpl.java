package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneAssetsImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCreateReqDTO;
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
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceStep;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceVariable;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScenarioVariable;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneFollow;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneStep;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneStepVariable;
import io.github.xiaomisum.robotest.model.entity.apitest.CommonComponent;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceStepMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceVariableMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScenarioVariableMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneFollowMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneStepMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneStepVariableMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.CommonComponentMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCENE_REFERENCED;

/**
 * 测试场景管理实现（测试场景详细设计 3.1-3.5、3.9-3.10）
 */
@Slf4j
@Service
public class ApiSceneServiceImpl implements ApiSceneService {

    private static final String TARGET_TYPE_SCENE = "scene";
    private static final Set<String> SYNC_MODES = Set.of("copy", "link");
    private static final Set<String> FAILURE_RULES = Set.of("all", "continue");

    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ApiSceneStepMapper stepMapper;
    @Resource
    private ApiSceneStepVariableMapper stepVariableMapper;
    @Resource
    private ApiScenarioVariableMapper scenarioVariableMapper;
    @Resource
    private ApiSceneInterfaceMapper sceneInterfaceMapper;
    @Resource
    private ApiExecutionRecordMapper executionRecordMapper;
    @Resource
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Resource
    private ApiInterfaceMapper interfaceMapper;
    @Resource
    private ApiInterfaceStepMapper interfaceStepMapper;
    @Resource
    private ApiInterfaceVariableMapper interfaceVariableMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private ApiScheduledTaskMapper scheduledTaskMapper;
    @Resource
    private CommonComponentMapper componentMapper;
    @Resource
    private ApiSceneFollowMapper sceneFollowMapper;

    // ========== 场景管理 ==========

    @Override
    public PageResult<ApiScenePageItemRespDTO> fetchPage(UUID workspaceId, UUID projectId, UUID userId,
            UUID moduleId, String search, Boolean followedOnly, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        List<UUID> followedIds = null;
        if (Boolean.TRUE.equals(followedOnly)) {
            followedIds = sceneFollowMapper.selectFollowedSceneIdsByUserId(userId);
        }
        PageResult<ApiScene> page = sceneMapper.selectPage(projectId, moduleId, search, followedIds, pageParam);
        List<ApiScene> scenes = page.getList();
        if (scenes.isEmpty()) {
            return new PageResult<>(List.of(), page.getTotal());
        }
        List<UUID> sceneIds = scenes.stream().map(ApiScene::getId).toList();
        Map<UUID, Long> stepCounts = countSteps(sceneIds);
        Map<UUID, ApiExecutionRecord> latestExecutions = latestExecutions(sceneIds);
        Set<UUID> followedSet = new LinkedHashSet<>(sceneFollowMapper.selectFollowedSceneIdsByUserId(userId));
        return new PageResult<>(scenes.stream()
                .map(scene -> toItem(scene, stepCounts, latestExecutions, followedSet)).toList(), page.getTotal());
    }

    private Map<UUID, Long> countSteps(List<UUID> sceneIds) {
        List<ApiSceneStep> steps = stepMapper.selectList(new LambdaQueryWrapperX<ApiSceneStep>()
                .in(ApiSceneStep::getSceneId, sceneIds)
                .select(ApiSceneStep::getId, ApiSceneStep::getSceneId));
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (ApiSceneStep step : steps) {
            counts.merge(step.getSceneId(), 1L, Long::sum);
        }
        return counts;
    }

    /** 列表页最近执行徽标：一次取回页内场景的执行记录，内存中按场景保留最新一条 */
    private Map<UUID, ApiExecutionRecord> latestExecutions(List<UUID> sceneIds) {
        List<ApiExecutionRecord> records = executionRecordMapper.selectList(
                new LambdaQueryWrapperX<ApiExecutionRecord>()
                        .in(ApiExecutionRecord::getSceneId, sceneIds)
                        .orderByDesc(ApiExecutionRecord::getExecutedAt));
        Map<UUID, ApiExecutionRecord> latest = new LinkedHashMap<>();
        for (ApiExecutionRecord record : records) {
            latest.putIfAbsent(record.getSceneId(), record);
        }
        return latest;
    }

    private ApiScenePageItemRespDTO toItem(ApiScene scene, Map<UUID, Long> stepCounts,
            Map<UUID, ApiExecutionRecord> latestExecutions, Set<UUID> followedSet) {
        ApiExecutionRecord last = latestExecutions.get(scene.getId());
        return ApiScenePageItemRespDTO.builder()
                .id(scene.getId())
                .name(scene.getName())
                .moduleId(scene.getModuleId())
                .environmentId(scene.getEnvironmentId())
                .stepCount(stepCounts.getOrDefault(scene.getId(), 0L).intValue())
                .lastExecutedAt(last == null ? null : last.getExecutedAt())
                .lastStatus(last == null ? null : last.getStatus())
                .updatedAt(scene.getUpdatedAt())
                .followed(followedSet.contains(scene.getId()))
                .build();
    }

    @Override
    public ApiSceneDetailRespDTO getDetail(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, id);
        List<ApiSceneStep> steps = stepMapper.listBySceneId(id);
        List<ApiScenarioVariable> variables = scenarioVariableMapper.listBySceneId(id);
        Map<UUID, List<Map<String, Object>>> stepVariables = loadStepVariables(
                steps.stream().map(ApiSceneStep::getId).toList());
        boolean followed = sceneFollowMapper.selectBySceneAndUser(id, userId) != null;
        return toDetail(scene, steps, variables, stepVariables, followed);
    }

    private Map<UUID, List<Map<String, Object>>> loadStepVariables(List<UUID> stepIds) {
        Map<UUID, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (stepIds.isEmpty()) {
            return result;
        }
        for (ApiSceneStepVariable variable : stepVariableMapper.listByStepIds(stepIds)) {
            result.computeIfAbsent(variable.getStepId(), k -> new ArrayList<>()).add(toVariableMap(variable));
        }
        return result;
    }

    private ApiSceneDetailRespDTO toDetail(ApiScene scene, List<ApiSceneStep> steps,
            List<ApiScenarioVariable> variables,
            Map<UUID, List<Map<String, Object>>> stepVariables, boolean followed) {
        return ApiSceneDetailRespDTO.builder()
                .id(scene.getId())
                .name(scene.getName())
                .moduleId(scene.getModuleId())
                .description(scene.getDescription())
                .environmentId(scene.getEnvironmentId())
                .followed(followed)
                .variables(variables.stream().map(v -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", v.getName());
                    item.put("value", v.getValue());
                    item.put("description", v.getDescription());
                    return item;
                }).toList())
                .processors(Objects.requireNonNullElse(scene.getProcessors(), List.of()))
                .failureRule(scene.getFailureRule())
                .cookieConfig(Objects.requireNonNullElse(scene.getCookieConfig(), Map.of()))
                .changeVersion(scene.getChangeVersion())
                .steps(steps.stream().map(step -> toStepDetail(step,
                        stepVariables.getOrDefault(step.getId(), List.of()))).toList())
                .build();
    }

    private ApiSceneDetailRespDTO.Step toStepDetail(ApiSceneStep step, List<Map<String, Object>> variables) {
        return ApiSceneDetailRespDTO.Step.builder()
                .id(step.getId())
                .name(step.getName())
                .stepType(step.getStepType())
                .sortOrder(step.getSortOrder())
                .enabled(step.getEnabled())
                .sourceType(step.getSourceType() == null ? "custom" : step.getSourceType())
                .sourceId(step.getSourceId())
                .sourceInterfaceId(step.getSourceInterfaceId())
                .sourceInterfaceName(step.getSourceInterfaceName())
                .sourceMissing(isLinkSourceMissing(step))
                .requestConfig(step.getRequestConfig())
                .variables(variables)
                .processors(Objects.requireNonNullElse(step.getProcessors(), List.of()))
                .validators(Objects.requireNonNullElse(step.getValidators(), List.of()))
                .extractors(Objects.requireNonNullElse(step.getExtractors(), List.of()))
                .build();
    }

    /** 链接引用源被删除时置灰展示（测试场景详细设计 4.5） */
    private Boolean isLinkSourceMissing(ApiSceneStep step) {
        if (!"link".equals(step.getSourceType()) || step.getSourceId() == null) {
            return false;
        }
        return "public_step".equals(step.getSourceType())
                ? interfaceStepMapper.selectById(step.getSourceId()) == null
                // system/copy/link 主步骤来源均为接口定义
                : interfaceMapper.selectById(step.getSourceId()) == null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID create(UUID workspaceId, UUID projectId, UUID userId, ApiSceneCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        validateFailureRule(reqDTO.getFailureRule());
        ApiScene scene = new ApiScene();
        scene.setId(UUID.randomUUID());
        scene.setProjectId(projectId);
        applyCreateFields(scene, reqDTO);
        scene.setFailureRule(Objects.requireNonNullElse(reqDTO.getFailureRule(), "all"));
        scene.setChangeVersion(1);
        sceneMapper.insert(scene);

        replaceVariables(scene.getId(), reqDTO.getVariables());
        syncVariablesSnapshot(scene.getId());
        writeHistory(projectId, scene.getId(), "create", "创建场景", userId);
        return scene.getId();
    }

    private void applyCreateFields(ApiScene scene, ApiSceneCreateReqDTO reqDTO) {
        scene.setName(reqDTO.getName());
        scene.setModuleId(reqDTO.getModuleId());
        scene.setDescription(reqDTO.getDescription());
        scene.setEnvironmentId(reqDTO.getEnvironmentId());
        scene.setProcessors(Objects.requireNonNullElse(reqDTO.getProcessors(), List.of()));
        scene.setCookieConfig(Objects.requireNonNullElse(reqDTO.getCookieConfig(), Map.of()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiSceneUpdateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, id);
        validateFailureRule(reqDTO.getFailureRule());

        int nextVersion = reqDTO.getChangeVersion() + 1;
        ApiScene carrier = new ApiScene();
        carrier.setId(id);
        carrier.setName(reqDTO.getName());
        carrier.setModuleId(reqDTO.getModuleId());
        carrier.setDescription(reqDTO.getDescription());
        carrier.setEnvironmentId(reqDTO.getEnvironmentId());
        carrier.setProcessors(reqDTO.getProcessors());
        carrier.setCookieConfig(reqDTO.getCookieConfig());
        carrier.setFailureRule(reqDTO.getFailureRule());
        carrier.setChangeVersion(nextVersion);
        // 乐观锁：版本号不匹配即 0 行更新（测试场景详细设计 3.1.4）
        int rows = sceneMapper.update(carrier, new LambdaUpdateWrapperX<ApiScene>()
                .eq(ApiScene::getId, id)
                .eq(ApiScene::getChangeVersion, reqDTO.getChangeVersion()));
        if (rows == 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_VERSION_CONFLICT);
        }
        if (reqDTO.getVariables() != null) {
            replaceVariables(id, reqDTO.getVariables());
            syncVariablesSnapshot(id);
        }
        writeHistory(projectId, id, "update", "更新场景", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, id);
        // 删除保护（7203）：被定时任务绑定的场景不可删除（定时任务详细设计 4.2）
        Long boundCount = scheduledTaskMapper.selectCountBound("scene_execute", id);
        if (boundCount != null && boundCount > 0) {
            throw ServiceExceptionUtil.get(API_SCENE_REFERENCED);
        }
        sceneMapper.deleteById(id);
        scenarioVariableMapper.deleteBySceneId(id);
        List<ApiSceneStep> steps = stepMapper.listBySceneId(id);
        steps.forEach(step -> stepVariableMapper.deleteByStepId(step.getId()));
        stepMapper.deleteBySceneId(id);
        sceneInterfaceMapper.deleteBySceneId(id);
        sceneFollowMapper.deleteBySceneId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID copy(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiSceneCopyReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene origin = requireScene(projectId, id);
        String name = reqDTO != null && reqDTO.getName() != null && !reqDTO.getName().isBlank()
                ? reqDTO.getName() : origin.getName() + "（副本）";

        ApiScene copy = new ApiScene();
        copy.setId(UUID.randomUUID());
        copy.setProjectId(projectId);
        copy.setModuleId(origin.getModuleId());
        copy.setName(name);
        copy.setDescription(origin.getDescription());
        copy.setEnvironmentId(origin.getEnvironmentId());
        copy.setVariables(origin.getVariables());
        copy.setProcessors(origin.getProcessors());
        copy.setFailureRule(origin.getFailureRule());
        copy.setCookieConfig(origin.getCookieConfig());
        copy.setChangeVersion(1);
        sceneMapper.insert(copy);

        // 复制模式：步骤与变量全部独立副本，不带链接引用语义（测试场景详细设计 3.1.6）
        List<ApiScenarioVariable> variables = scenarioVariableMapper.listBySceneId(id);
        if (!variables.isEmpty()) {
            scenarioVariableMapper.insertBatch(variables.stream().map(v -> {
                ApiScenarioVariable row = new ApiScenarioVariable();
                row.setId(UUID.randomUUID());
                row.setSceneId(copy.getId());
                row.setName(v.getName());
                row.setValue(v.getValue());
                row.setDescription(v.getDescription());
                row.setSortOrder(v.getSortOrder());
                return row;
            }).toList());
        }
        List<ApiSceneStep> steps = stepMapper.listBySceneId(id);
        Map<UUID, List<ApiSceneStepVariable>> varsByStep = groupStepVariables(steps);
        for (ApiSceneStep step : steps) {
            ApiSceneStep copied = insertCopiedStep(copy.getId(), step, stepMapper.selectMaxSortOrder(copy.getId()));
            copyStepVariables(varsByStep.getOrDefault(step.getId(), List.of()), copied.getId());
        }
        writeHistory(projectId, copy.getId(), "copy", "复制自场景「" + origin.getName() + "」", userId);
        return copy.getId();
    }

    private Map<UUID, List<ApiSceneStepVariable>> groupStepVariables(List<ApiSceneStep> steps) {
        Map<UUID, List<ApiSceneStepVariable>> result = new LinkedHashMap<>();
        List<UUID> ids = steps.stream().map(ApiSceneStep::getId).toList();
        for (ApiSceneStepVariable variable : stepVariableMapper.listByStepIds(ids)) {
            result.computeIfAbsent(variable.getStepId(), k -> new ArrayList<>()).add(variable);
        }
        return result;
    }

    private ApiSceneStep insertCopiedStep(UUID sceneId, ApiSceneStep origin, Integer baseSortOrder) {
        ApiSceneStep copied = new ApiSceneStep();
        copied.setId(UUID.randomUUID());
        copied.setSceneId(sceneId);
        copied.setName(origin.getName());
        copied.setStepType(origin.getStepType());
        copied.setSortOrder(baseSortOrder + 1);
        copied.setEnabled(origin.getEnabled());
        // 副本一律为 copy 来源，与原步骤无关联（测试场景详细设计 3.10 同语义）
        copied.setSourceType("copy");
        copied.setSourceId(origin.getSourceId());
        copied.setSourceInterfaceId(origin.getSourceInterfaceId());
        copied.setSourceInterfaceName(origin.getSourceInterfaceName());
        copied.setRequestConfig(deepCopyMap(origin.getRequestConfig()));
        copied.setProcessors(copyListWithFreshIds(origin.getProcessors()));
        copied.setValidators(copyListWithFreshIds(origin.getValidators()));
        copied.setExtractors(copyListWithFreshIds(origin.getExtractors()));
        stepMapper.insert(copied);
        return copied;
    }

    private void copyStepVariables(List<ApiSceneStepVariable> origin, UUID targetStepId) {
        if (origin.isEmpty()) {
            return;
        }
        stepVariableMapper.insertBatch(origin.stream().map(v -> {
            ApiSceneStepVariable row = new ApiSceneStepVariable();
            row.setId(UUID.randomUUID());
            row.setStepId(targetStepId);
            row.setName(v.getName());
            row.setValue(v.getValue());
            row.setSource(v.getSource());
            row.setInterfaceVariableId(v.getInterfaceVariableId());
            row.setDescription(v.getDescription());
            row.setSortOrder(v.getSortOrder());
            return row;
        }).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSettings(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneSettingsReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        if (reqDTO.getFailureRule() != null && !FAILURE_RULES.contains(reqDTO.getFailureRule())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_SETTING_INVALID,
                    "失败规则仅支持 all/continue");
        }
        validateCookieConfig(reqDTO.getCookieConfig());

        ApiScene carrier = new ApiScene();
        carrier.setId(sceneId);
        carrier.setFailureRule(reqDTO.getFailureRule());
        carrier.setCookieConfig(reqDTO.getCookieConfig());
        sceneMapper.updateById(carrier);
        writeHistory(projectId, sceneId, "update", "更新场景设置", userId);
    }

    @SuppressWarnings("unchecked")
    private void validateCookieConfig(Map<String, Object> cookieConfig) {
        if (cookieConfig == null) {
            return;
        }
        try {
            Object items = cookieConfig.get("items");
            if (items instanceof List<?> list) {
                for (Object item : list) {
                    Map<String, Object> entry = (Map<String, Object>) item;
                    Object key = entry.get("key");
                    if (key == null || key.toString().isBlank()) {
                        throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_SETTING_INVALID,
                                "Cookie 条目缺少 key");
                    }
                }
            }
        } catch (ClassCastException ex) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_SETTING_INVALID,
                    "Cookie 配置结构不合法");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVariables(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneVariableBatchReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        replaceVariables(sceneId, reqDTO.getVariables());
        syncVariablesSnapshot(sceneId);
        writeHistory(projectId, sceneId, "update", "更新场景变量", userId);
    }

    /** 全量覆盖语义（测试场景详细设计 3.5.1） */
    private void replaceVariables(UUID sceneId, List<ApiSceneVariableBatchReqDTO.Variable> variables) {
        scenarioVariableMapper.deleteBySceneId(sceneId);
        if (variables == null || variables.isEmpty()) {
            return;
        }
        List<ApiScenarioVariable> rows = new ArrayList<>();
        int order = 0;
        for (ApiSceneVariableBatchReqDTO.Variable variable : variables) {
            if (variable.getName() == null || variable.getName().isBlank()) {
                continue;
            }
            ApiScenarioVariable row = new ApiScenarioVariable();
            row.setId(UUID.randomUUID());
            row.setSceneId(sceneId);
            row.setName(variable.getName().trim());
            row.setValue(variable.getValue());
            row.setDescription(variable.getDescription());
            row.setSortOrder(order++);
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            scenarioVariableMapper.insertBatch(rows);
        }
    }

    /** api_scene.variables JSONB 为列表页冗余快照，保存后同步 */
    private void syncVariablesSnapshot(UUID sceneId) {
        List<Map<String, Object>> snapshot = scenarioVariableMapper.listBySceneId(sceneId).stream()
                .map(v -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", v.getName());
                    item.put("value", v.getValue());
                    item.put("description", v.getDescription());
                    return item;
                }).toList();
        ApiScene carrier = new ApiScene();
        carrier.setId(sceneId);
        carrier.setVariables(snapshot);
        sceneMapper.updateById(carrier);
    }

    // ========== 步骤管理 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID createStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneStepSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        validateStepType(reqDTO.getStepType());
        ApiSceneStep step = new ApiSceneStep();
        step.setId(UUID.randomUUID());
        step.setSceneId(sceneId);
        applyStepFields(step, reqDTO);
        step.setSortOrder(reqDTO.getSortOrder() == null
                ? stepMapper.selectMaxSortOrder(sceneId) + 1 : reqDTO.getSortOrder());
        stepMapper.insert(step);
        return step.getId();
    }

    private void applyStepFields(ApiSceneStep step, ApiSceneStepSaveReqDTO reqDTO) {
        step.setName(reqDTO.getName());
        step.setStepType(normalizeStepType(reqDTO.getStepType()));
        step.setEnabled(Objects.requireNonNullElse(reqDTO.getEnabled(), true));
        step.setSourceType(Objects.requireNonNullElse(reqDTO.getSourceType(), "custom"));
        step.setRequestConfig(Objects.requireNonNullElse(reqDTO.getRequestConfig(), Map.of()));
        step.setProcessors(Objects.requireNonNullElse(reqDTO.getProcessors(), List.of()));
        step.setValidators(Objects.requireNonNullElse(reqDTO.getValidators(), List.of()));
        step.setExtractors(Objects.requireNonNullElse(reqDTO.getExtractors(), List.of()));
    }

    private String normalizeStepType(String stepType) {
        return Objects.requireNonNullElse(stepType, "http").toLowerCase();
    }

    /** V1.2 执行引擎仅覆盖 http 取样器（与快速调试域口径一致），jdbc 允许存储但不允许编排为可执行步骤 */
    private void validateStepType(String stepType) {
        String type = normalizeStepType(stepType);
        if (!"http".equals(type)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED,
                    "V1.2 仅支持 http 类型步骤");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiSceneQuickCreateRespDTO quickCreateSteps(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, ApiSceneStepQuickCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        String mode = normalizeMode(reqDTO.getMode());
        ApiInterface apiInterface = interfaceMapper.selectById(reqDTO.getInterfaceId());
        if (apiInterface == null || !projectId.equals(apiInterface.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_INTERFACE_NOT_FOUND);
        }

        ApiSceneInterface association = upsertAssociation(sceneId, apiInterface.getId(), mode);
        int order = stepMapper.selectMaxSortOrder(sceneId) + 1;
        List<ApiSceneQuickCreateRespDTO.CreatedStep> created = new ArrayList<>();

        ApiSceneStep mainStep = buildFromInterface(apiInterface, mode, order++);
        stepMapper.insert(mainStep);
        if (reqDTO.getImportInterfaceVariables() == null || reqDTO.getImportInterfaceVariables()) {
            importInterfaceVariables(mainStep.getId(), apiInterface.getId(), order);
        }
        created.add(toCreatedStep(mainStep));

        for (ApiInterfaceStep publicStep : interfaceStepMapper.selectListByInterfaceId(apiInterface.getId())) {
            ApiSceneStep step = buildFromPublicStep(publicStep, mode, apiInterface, order++);
            stepMapper.insert(step);
            created.add(toCreatedStep(step));
        }
        return ApiSceneQuickCreateRespDTO.builder().steps(created).associationId(association.getId()).build();
    }

    /** 已关联则更新同步模式，否则新建关联（测试场景详细设计 3.3.2 创建逻辑 1） */
    private ApiSceneInterface upsertAssociation(UUID sceneId, UUID interfaceId, String mode) {
        ApiSceneInterface association = sceneInterfaceMapper.selectBySceneAndInterface(sceneId, interfaceId);
        if (association == null) {
            association = new ApiSceneInterface();
            association.setId(UUID.randomUUID());
            association.setSceneId(sceneId);
            association.setInterfaceId(interfaceId);
            association.setSyncMode(mode);
            sceneInterfaceMapper.insert(association);
            return association;
        }
        if (!mode.equals(association.getSyncMode())) {
            ApiSceneInterface carrier = new ApiSceneInterface();
            carrier.setId(association.getId());
            carrier.setSyncMode(mode);
            sceneInterfaceMapper.updateById(carrier);
            association.setSyncMode(mode);
        }
        return association;
    }

    private ApiSceneStep buildFromInterface(ApiInterface apiInterface, String mode, int sortOrder) {
        Map<String, Object> requestConfig = new LinkedHashMap<>();
        requestConfig.put("method", apiInterface.getMethod());
        requestConfig.put("url", apiInterface.getPath());
        requestConfig.put("headers", withEntryIds(apiInterface.getHeaders()));
        requestConfig.put("params", withEntryIds(apiInterface.getQueryParams()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", Objects.requireNonNullElse(apiInterface.getBodyType(), "none"));
        body.put("content", apiInterface.getBody());
        requestConfig.put("body", body);

        ApiSceneStep step = new ApiSceneStep();
        step.setId(UUID.randomUUID());
        step.setName(apiInterface.getName());
        step.setSourceType(mode);
        step.setSourceId(apiInterface.getId());
        step.setSourceInterfaceId(apiInterface.getId());
        step.setSourceInterfaceName(apiInterface.getName());
        step.setRequestConfig(requestConfig);
        step.setStepType("http");
        step.setSortOrder(sortOrder);
        step.setEnabled(true);
        step.setProcessors(List.of());
        step.setValidators(List.of());
        step.setExtractors(List.of());
        return step;
    }

    private ApiSceneStep buildFromPublicStep(ApiInterfaceStep publicStep, String mode,
            ApiInterface apiInterface, int sortOrder) {
        ApiSceneStep step = new ApiSceneStep();
        step.setId(UUID.randomUUID());
        step.setName(publicStep.getName());
        step.setSourceType(mode);
        step.setSourceId(publicStep.getId());
        step.setSourceInterfaceId(apiInterface.getId());
        step.setSourceInterfaceName(apiInterface.getName());
        step.setRequestConfig(deepCopyMap(publicStep.getRequestConfig()));
        step.setProcessors(copyListWithFreshIds(publicStep.getProcessors()));
        step.setValidators(copyListWithFreshIds(publicStep.getValidators()));
        step.setExtractors(copyListWithFreshIds(publicStep.getExtractors()));
        step.setStepType("http");
        step.setSortOrder(sortOrder);
        step.setEnabled(true);
        return step;
    }

    /** 接口级变量 → 步骤级变量（source=interface，记录来源 ID，测试场景详细设计 3.3.2 创建逻辑 4） */
    private void importInterfaceVariables(UUID stepId, UUID interfaceId, int baseOrder) {
        List<ApiInterfaceVariable> sources = interfaceVariableMapper.selectListByInterfaceId(interfaceId);
        if (sources.isEmpty()) {
            return;
        }
        List<ApiSceneStepVariable> rows = new ArrayList<>();
        int order = 0;
        for (ApiInterfaceVariable source : sources) {
            ApiSceneStepVariable row = new ApiSceneStepVariable();
            row.setId(UUID.randomUUID());
            row.setStepId(stepId);
            row.setName(source.getName());
            row.setValue(source.getDefaultValue());
            row.setSource("interface");
            row.setInterfaceVariableId(source.getId());
            row.setDescription(source.getDescription());
            row.setSortOrder(baseOrder + order++);
            rows.add(row);
        }
        stepVariableMapper.insertBatch(rows);
    }

    private ApiSceneQuickCreateRespDTO.CreatedStep toCreatedStep(ApiSceneStep step) {
        return ApiSceneQuickCreateRespDTO.CreatedStep.builder()
                .id(step.getId())
                .name(step.getName())
                .sourceType(step.getSourceType())
                .sourceInterfaceName(step.getSourceInterfaceName())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID addPublicStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneStepPublicStepReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        String mode = normalizeMode(reqDTO.getMode());
        ApiInterfaceStep publicStep = interfaceStepMapper.selectById(reqDTO.getPublicStepId());
        if (publicStep == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "公共步骤不存在");
        }
        ApiInterface apiInterface = requireProjectInterface(projectId, publicStep.getInterfaceId());
        int order = reqDTO.getSortOrder() == null
                ? stepMapper.selectMaxSortOrder(sceneId) + 1 : reqDTO.getSortOrder();
        ApiSceneStep step = buildFromPublicStep(publicStep, mode, apiInterface, order);
        stepMapper.insert(step);
        return step.getId();
    }

    private ApiInterface requireProjectInterface(UUID projectId, UUID interfaceId) {
        ApiInterface apiInterface = interfaceMapper.selectById(interfaceId);
        if (apiInterface == null || !projectId.equals(apiInterface.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_INTERFACE_NOT_FOUND);
        }
        return apiInterface;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId,
            ApiSceneStepSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireStep(projectId, sceneId, stepId);
        ApiSceneStep carrier = new ApiSceneStep();
        carrier.setId(stepId);
        carrier.setName(reqDTO.getName());
        if (reqDTO.getStepType() != null) {
            validateStepType(reqDTO.getStepType());
            carrier.setStepType(normalizeStepType(reqDTO.getStepType()));
        }
        carrier.setEnabled(reqDTO.getEnabled());
        carrier.setRequestConfig(reqDTO.getRequestConfig());
        carrier.setProcessors(reqDTO.getProcessors());
        carrier.setValidators(reqDTO.getValidators());
        carrier.setExtractors(reqDTO.getExtractors());
        if (reqDTO.getSortOrder() != null) {
            carrier.setSortOrder(reqDTO.getSortOrder());
        }
        stepMapper.updateById(carrier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireStep(projectId, sceneId, stepId);
        stepMapper.deleteById(stepId);
        stepVariableMapper.deleteByStepId(stepId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderSteps(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneStepReorderReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        List<ApiSceneStep> existing = stepMapper.listBySceneId(sceneId);
        Set<UUID> owned = existing.stream().map(ApiSceneStep::getId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> incoming = new LinkedHashSet<>(reqDTO.getStepIds());
        if (!owned.containsAll(incoming) || incoming.size() != reqDTO.getStepIds().size()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_STEP_NOT_FOUND);
        }
        // 数组顺序即为新排序（测试场景详细设计 3.3.6）
        stepMapper.reorder(sceneId, reqDTO.getStepIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID copyStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId,
            ApiSceneStepCopyReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiSceneStep origin = requireStep(projectId, sceneId, stepId);
        String name = reqDTO != null && reqDTO.getName() != null && !reqDTO.getName().isBlank()
                ? reqDTO.getName() : origin.getName() + "（副本）";
        ApiSceneStep copied = insertCopiedStep(sceneId, origin, stepMapper.selectMaxSortOrder(sceneId));
        ApiSceneStep rename = new ApiSceneStep();
        rename.setId(copied.getId());
        rename.setName(name);
        stepMapper.updateById(rename);
        return copied.getId();
    }

    // ========== 步骤级变量 ==========

    @Override
    public List<Map<String, Object>> listStepVariables(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, UUID stepId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireStep(projectId, sceneId, stepId);
        return stepVariableMapper.listByStepId(stepId).stream().map(this::toVariableMap).toList();
    }

    private Map<String, Object> toVariableMap(ApiSceneStepVariable variable) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", variable.getId());
        item.put("name", variable.getName());
        item.put("value", variable.getValue());
        item.put("source", variable.getSource());
        item.put("interfaceVariableId", variable.getInterfaceVariableId());
        item.put("description", variable.getDescription());
        item.put("sortOrder", variable.getSortOrder());
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStepVariables(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId,
            ApiSceneStepVariableBatchReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireStep(projectId, sceneId, stepId);
        stepVariableMapper.deleteByStepId(stepId);
        if (reqDTO.getVariables() == null || reqDTO.getVariables().isEmpty()) {
            return;
        }
        List<ApiSceneStepVariable> rows = new ArrayList<>();
        int order = 0;
        for (var variable : reqDTO.getVariables()) {
            if (variable.getName() == null || variable.getName().isBlank()) {
                continue;
            }
            ApiSceneStepVariable row = new ApiSceneStepVariable();
            row.setId(UUID.randomUUID());
            row.setStepId(stepId);
            row.setName(variable.getName().trim());
            row.setValue(variable.getValue());
            // 手动更新的变量置 custom（测试场景详细设计 3.4.2）
            row.setSource("custom");
            row.setDescription(variable.getDescription());
            row.setSortOrder(order++);
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            stepVariableMapper.insertBatch(rows);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> importStepVariables(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, UUID stepId, ApiSceneStepVariableImportReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireStep(projectId, sceneId, stepId);
        requireProjectInterface(projectId, reqDTO.getInterfaceId());
        String strategy = Objects.requireNonNullElse(reqDTO.getStrategy(), "merge");

        List<ApiSceneStepVariable> existing = stepVariableMapper.listByStepId(stepId);
        List<ApiInterfaceVariable> sources = interfaceVariableMapper.selectListByInterfaceId(reqDTO.getInterfaceId());
        if ("replace".equals(strategy)) {
            existing = List.of();
        }

        // merge：更新已有接口变量（source=interface 且 interface_variable_id 匹配），跳过自定义变量，追加新增变量
        Map<UUID, ApiSceneStepVariable> existingByIfaceVarId = existing.stream()
                .filter(v -> "interface".equals(v.getSource()) && v.getInterfaceVariableId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        ApiSceneStepVariable::getInterfaceVariableId, v -> v, (a, b) -> b));
        Set<String> customNames = existing.stream()
                .filter(v -> "custom".equals(v.getSource()))
                .map(ApiSceneStepVariable::getName)
                .collect(java.util.stream.Collectors.toSet());

        // 保留不受影响的自定义变量
        List<ApiSceneStepVariable> rows = existing.stream()
                .filter(v -> "custom".equals(v.getSource()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int order = 0;
        for (ApiInterfaceVariable source : sources) {
            // 已有接口变量 → 更新值与描述
            ApiSceneStepVariable matched = existingByIfaceVarId.get(source.getId());
            if (matched != null) {
                matched.setValue(source.getDefaultValue());
                matched.setDescription(source.getDescription());
                matched.setSortOrder(order++);
                rows.add(matched);
                continue;
            }
            // 同名自定义变量 → 跳过，不覆盖（测试场景详细设计 3.4.3）
            if (customNames.contains(source.getName())) {
                continue;
            }
            // 新变量 → 追加
            ApiSceneStepVariable row = new ApiSceneStepVariable();
            row.setId(UUID.randomUUID());
            row.setStepId(stepId);
            row.setName(source.getName());
            row.setValue(source.getDefaultValue());
            row.setSource("interface");
            row.setInterfaceVariableId(source.getId());
            row.setDescription(source.getDescription());
            row.setSortOrder(order++);
            rows.add(row);
        }
        stepVariableMapper.deleteByStepId(stepId);
        if (!rows.isEmpty()) {
            stepVariableMapper.insertBatch(rows);
        }
        return stepVariableMapper.listByStepId(stepId).stream().map(this::toVariableMap).toList();
    }

    // ========== 场景关联接口 ==========

    @Override
    public List<ApiSceneAssociationItemRespDTO> listAssociations(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        return sceneInterfaceMapper.listBySceneId(sceneId).stream().map(association -> {
            ApiInterface apiInterface = interfaceMapper.selectById(association.getInterfaceId());
            return ApiSceneAssociationItemRespDTO.builder()
                    .id(association.getId())
                    .interfaceId(association.getInterfaceId())
                    .interfaceName(apiInterface == null ? null : apiInterface.getName())
                    .method(apiInterface == null ? null : apiInterface.getMethod())
                    .path(apiInterface == null ? null : apiInterface.getPath())
                    .syncMode(association.getSyncMode())
                    .publicStepCount(apiInterface == null ? 0
                            : interfaceStepMapper.selectListByInterfaceId(apiInterface.getId()).size())
                    .createdAt(association.getCreatedAt())
                    .build();
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void associateInterfaces(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneInterfaceAssociateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        String mode = normalizeMode(reqDTO.getSyncMode());
        for (UUID interfaceId : Objects.requireNonNullElse(reqDTO.getInterfaceIds(), List.<UUID>of())) {
            ApiInterface apiInterface = requireProjectInterface(projectId, interfaceId);
            if (sceneInterfaceMapper.selectBySceneAndInterface(sceneId, interfaceId) != null) {
                // 同一接口只允许关联一次（应用层校验，错误码 7208）
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_INTERFACE_EXISTS);
            }
            ApiSceneInterface association = new ApiSceneInterface();
            association.setId(UUID.randomUUID());
            association.setSceneId(sceneId);
            association.setInterfaceId(apiInterface.getId());
            association.setSyncMode(mode);
            sceneInterfaceMapper.insert(association);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unassociateInterface(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            UUID associationId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiSceneInterface association = sceneInterfaceMapper.selectById(associationId);
        if (association == null || !association.getSceneId().equals(sceneId)) {
            requireScene(projectId, sceneId);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
        sceneInterfaceMapper.deleteById(associationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchSyncMode(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID associationId,
            ApiSceneInterfaceSyncModeReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiSceneInterface association = sceneInterfaceMapper.selectById(associationId);
        if (association == null || !association.getSceneId().equals(sceneId)) {
            requireScene(projectId, sceneId);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
        String mode = normalizeMode(reqDTO.getSyncMode());
        ApiSceneInterface carrier = new ApiSceneInterface();
        carrier.setId(associationId);
        carrier.setSyncMode(mode);
        // link 模式切换后由执行前同步刷新 last_synced_at（测试场景详细设计 3.2.4）
        sceneInterfaceMapper.updateById(carrier);
    }

    // ========== 场景设置 ==========

    @Override
    public ApiSceneSettingsRespDTO getSettings(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        return ApiSceneSettingsRespDTO.builder()
                .failureRule(scene.getFailureRule())
                .cookieConfig(Objects.requireNonNullElse(scene.getCookieConfig(), Map.of()))
                .build();
    }

    // ========== 全局资产引入 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiSceneAssetsImportRespDTO importAssets(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneAssetsImportReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        String target = reqDTO.getTarget();
        if (!Set.of("scene_processor", "step_validator", "step_extractor").contains(target)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "target 不合法");
        }
        if (("step_validator".equals(target) || "step_extractor".equals(target)) && reqDTO.getStepId() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "step_validator/step_extractor 需要 stepId");
        }
        if ("step_validator".equals(target) || "step_extractor".equals(target)) {
            requireStep(projectId, sceneId, reqDTO.getStepId());
        }

        int imported = 0;
        List<Map<String, Object>> processorConfigs = new ArrayList<>();
        List<Integer> processorSortOrders = new ArrayList<>();
        for (UUID assetId : reqDTO.getAssetIds()) {
            CommonComponent component = componentMapper.selectById(assetId);
            if (component == null || !Boolean.TRUE.equals(component.getEnabled())) {
                continue;
            }
            Map<String, Object> config = parseConfig(component.getConfig());
            config.put("id", UUID.randomUUID().toString());
            config.put("name", component.getName());

            if ("scene_processor".equals(target)) {
                // 前置/后置处理器排序使用组件顶层排序字段：收集后统一按 sort_order 升序插入
                processorConfigs.add(config);
                processorSortOrders.add(component.getSortOrder() == null ? 0 : component.getSortOrder());
                continue;
            }
            switch (target) {
                case "step_validator" -> {
                    ApiSceneStep step = stepMapper.selectById(reqDTO.getStepId());
                    List<Map<String, Object>> validators = new ArrayList<>(
                            Objects.requireNonNullElse(step.getValidators(), List.of()));
                    validators.add(config);
                    ApiSceneStep stepCarrier = new ApiSceneStep();
                    stepCarrier.setId(reqDTO.getStepId());
                    stepCarrier.setValidators(validators);
                    stepMapper.updateById(stepCarrier);
                    imported++;
                }
                case "step_extractor" -> {
                    ApiSceneStep step = stepMapper.selectById(reqDTO.getStepId());
                    List<Map<String, Object>> extractors = new ArrayList<>(
                            Objects.requireNonNullElse(step.getExtractors(), List.of()));
                    extractors.add(config);
                    ApiSceneStep stepCarrier = new ApiSceneStep();
                    stepCarrier.setId(reqDTO.getStepId());
                    stepCarrier.setExtractors(extractors);
                    stepMapper.updateById(stepCarrier);
                    imported++;
                }
            }
        }
        if ("scene_processor".equals(target) && !processorConfigs.isEmpty()) {
            List<Map<String, Object>> ordered = new ArrayList<>();
            List<Map.Entry<Integer, Map<String, Object>>> pairs = new ArrayList<>();
            for (int i = 0; i < processorConfigs.size(); i++) {
                pairs.add(Map.entry(processorSortOrders.get(i), processorConfigs.get(i)));
            }
            pairs.sort(Comparator.comparingInt(Map.Entry::getKey));
            for (Map.Entry<Integer, Map<String, Object>> pair : pairs) {
                ordered.add(pair.getValue());
            }
            ApiScene scene = requireScene(projectId, sceneId);
            List<Map<String, Object>> processors = new ArrayList<>(
                    Objects.requireNonNullElse(scene.getProcessors(), List.of()));
            processors.addAll(ordered);
            ApiScene carrier = new ApiScene();
            carrier.setId(sceneId);
            carrier.setProcessors(processors);
            sceneMapper.updateById(carrier);
            imported += processorConfigs.size();
        }
        writeHistory(projectId, sceneId, "update", "从全局资产引入 " + imported + " 个", userId);
        return ApiSceneAssetsImportRespDTO.builder().imported(imported).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = JsonUtils.parseObject(configJson, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
        } catch (Exception ignored) {
        }
        return new LinkedHashMap<>();
    }

    // ========== 内部工具 ==========

    private ApiScene requireScene(UUID projectId, UUID id) {
        ApiScene scene = sceneMapper.selectById(id);
        if (scene == null || !scene.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
        return scene;
    }

    private ApiSceneStep requireStep(UUID projectId, UUID sceneId, UUID stepId) {
        requireScene(projectId, sceneId);
        ApiSceneStep step = stepMapper.selectById(stepId);
        if (step == null || !step.getSceneId().equals(sceneId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_STEP_NOT_FOUND);
        }
        return step;
    }

    private void validateFailureRule(String failureRule) {
        if (failureRule != null && !FAILURE_RULES.contains(failureRule)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_SETTING_INVALID,
                    "失败规则仅支持 all/continue");
        }
    }

    private String normalizeMode(String mode) {
        String normalized = Objects.requireNonNullElse(mode, "copy");
        if (!SYNC_MODES.contains(normalized)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "mode 仅支持 copy/link");
        }
        return normalized;
    }

    /** 变更历史版本号独立递增，同一对象内唯一（基础设施详细设计 2.1.2） */
    private void writeHistory(UUID projectId, UUID targetId, String changeType, String summary, UUID userId) {
        io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory history =
                new io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory();
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

    /** 行内条目启用态过滤并补 id；接口域行结构 {key, value, enabled} */
    private List<Map<String, Object>> withEntryIds(List<Map<String, Object>> entries) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (entries == null) {
            return result;
        }
        for (Map<String, Object> entry : entries) {
            Map<String, Object> copy = deepCopyMap(entry);
            copy.putIfAbsent("id", UUID.randomUUID().toString());
            copy.putIfAbsent("enabled", true);
            result.add(copy);
        }
        return result;
    }

    /** JSON 序列化往返实现深拷贝，避免副本与源共享嵌套可变结构 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> origin) {
        if (origin == null) {
            return new LinkedHashMap<>();
        }
        return JsonUtils.parseObject(JsonUtils.toJsonString(origin), LinkedHashMap.class);
    }

    /** 复制配置列表并为元素重新生成 id，保证副本与源无关联（测试场景详细设计 3.10） */
    private List<Map<String, Object>> copyListWithFreshIds(List<Map<String, Object>> origin) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (origin == null) {
            return result;
        }
        for (Map<String, Object> item : origin) {
            Map<String, Object> copy = deepCopyMap(item);
            copy.put("id", UUID.randomUUID().toString());
            result.add(copy);
        }
        return result;
    }

    // ========== 关注 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !scene.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
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
        ApiScene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !scene.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
        ApiSceneFollow existing = sceneFollowMapper.selectBySceneAndUser(sceneId, userId);
        if (existing != null) {
            sceneFollowMapper.deleteById(existing.getId());
        }
    }

    // ========== 批量删除 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(UUID workspaceId, UUID projectId, UUID userId,
            ApiSceneBatchDeleteReqDTO reqDTO) {
        for (UUID id : reqDTO.getIds()) {
            delete(workspaceId, projectId, userId, id);
        }
    }

    // ========== 公共步骤浏览 ==========

    @Override
    public List<ApiPublicStepBrowseItemRespDTO> browsePublicSteps(UUID workspaceId, UUID projectId,
            UUID userId, UUID sceneId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        List<ApiSceneInterface> associations = sceneInterfaceMapper.selectList(
                new LambdaQueryWrapperX<ApiSceneInterface>()
                        .eq(ApiSceneInterface::getSceneId, sceneId));
        List<ApiPublicStepBrowseItemRespDTO> result = new ArrayList<>();
        for (ApiSceneInterface assoc : associations) {
            List<ApiInterfaceStep> steps = interfaceStepMapper.selectList(
                    new LambdaQueryWrapperX<ApiInterfaceStep>()
                            .eq(ApiInterfaceStep::getInterfaceId, assoc.getInterfaceId()));
            ApiInterface iface = interfaceMapper.selectById(assoc.getInterfaceId());
            String ifaceName = iface != null ? iface.getName() : "";
            for (ApiInterfaceStep step : steps) {
                Map<String, Object> cfg = step.getRequestConfig();
                result.add(ApiPublicStepBrowseItemRespDTO.builder()
                        .id(step.getId().toString())
                        .name(step.getName())
                        .method(cfg != null ? String.valueOf(cfg.getOrDefault("method", "")) : "")
                        .path(cfg != null ? String.valueOf(cfg.getOrDefault("url", "")) : "")
                        .interfaceId(assoc.getInterfaceId().toString())
                        .interfaceName(ifaceName)
                        .build());
            }
        }
        return result;
    }

}
