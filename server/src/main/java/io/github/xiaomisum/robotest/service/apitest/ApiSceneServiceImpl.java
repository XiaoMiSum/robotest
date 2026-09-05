package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneAssetsImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepQuickCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepReorderReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneAssetsImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScenePageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneQuickCreateRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneFollow;
import io.github.xiaomisum.robotest.model.entity.apitest.CommonComponent;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneFollowMapper;
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
    private static final String SCENE_STATUS_DRAFT = "draft";
    private static final Set<String> SYNC_MODES = Set.of("copy", "link");
    private static final Set<String> SCENE_PRIORITIES = Set.of("P0", "P1", "P2", "P3");
    private static final Set<String> SCENE_STATUSES = Set.of("draft", "published");

    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ApiExecutionRecordMapper executionRecordMapper;
    @Resource
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Resource
    private ApiInterfaceMapper interfaceMapper;
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
        List<ApiScene> scenes = sceneMapper.selectBatchIds(sceneIds);
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (ApiScene scene : scenes) {
            List<Map<String, Object>> steps = scene.getSteps();
            counts.put(scene.getId(), steps == null ? 0L : steps.size());
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
                .priority(scene.getPriority())
                .status(scene.getStatus())
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
        boolean followed = sceneFollowMapper.selectBySceneAndUser(id, userId) != null;
        return toDetail(scene, followed);
    }

    private ApiSceneDetailRespDTO toDetail(ApiScene scene, boolean followed) {
        return ApiSceneDetailRespDTO.builder()
                .id(scene.getId())
                .name(scene.getName())
                .moduleId(scene.getModuleId())
                .description(scene.getDescription())
                .environmentId(scene.getEnvironmentId())
                .priority(scene.getPriority())
                .status(scene.getStatus())
                .followed(followed)
                .variables(Objects.requireNonNullElse(scene.getVariables(), List.<Map<String, Object>>of()))
                .processors(Objects.requireNonNullElse(scene.getProcessors(), List.of()))
                .changeVersion(scene.getChangeVersion())
                .steps(Objects.requireNonNullElse(scene.getSteps(), List.<Map<String, Object>>of()).stream()
                        .map(this::toStepDetail).toList())
                .build();
    }

    private ApiSceneDetailRespDTO.Step toStepDetail(Map<String, Object> step) {
        UUID stepId = SceneStepUtil.getUUID(step, "id");
        return ApiSceneDetailRespDTO.Step.builder()
                .id(stepId)
                .name(SceneStepUtil.getString(step, "name", null))
                .stepType(SceneStepUtil.getString(step, "stepType", null))
                .sortOrder(SceneStepUtil.getInteger(step, "sortOrder"))
                .enabled(SceneStepUtil.getBoolean(step, "enabled"))
                .sourceType(SceneStepUtil.getString(step, "sourceType", "custom"))
                .sourceId(SceneStepUtil.getUUID(step, "sourceId"))
                .sourceInterfaceId(SceneStepUtil.getUUID(step, "sourceInterfaceId"))
                .sourceInterfaceName(SceneStepUtil.getString(step, "sourceInterfaceName", null))
                .sourceMissing(isLinkSourceMissing(step))
                .requestConfig(SceneStepUtil.getMap(step, "requestConfig"))
                .variables(SceneStepUtil.getList(step, "variables"))
                .processors(SceneStepUtil.getList(step, "processors"))
                .validators(SceneStepUtil.getList(step, "validators"))
                .extractors(SceneStepUtil.getList(step, "extractors"))
                .build();
    }

    /** 链接引用源被删除时置灰展示（测试场景详细设计 4.5） */
    private Boolean isLinkSourceMissing(Map<String, Object> step) {
        if (!"link".equals(SceneStepUtil.getString(step, "sourceType", null))
                || SceneStepUtil.getUUID(step, "sourceId") == null) {
            return false;
        }
        UUID sourceId = SceneStepUtil.getUUID(step, "sourceId");
        return interfaceMapper.selectById(sourceId) == null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID create(UUID workspaceId, UUID projectId, UUID userId, ApiSceneCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        validatePriority(reqDTO.getPriority());
        validateStatus(reqDTO.getStatus());
        ApiScene scene = new ApiScene();
        scene.setId(UUID.randomUUID());
        scene.setProjectId(projectId);
        applyCreateFields(scene, reqDTO);
        scene.setChangeVersion(1);
        scene.setSteps(buildSteps(reqDTO.getSteps()));
        scene.setVariables(normalizeVariables(reqDTO.getVariables()));
        sceneMapper.insert(scene);

        writeHistory(projectId, scene.getId(), "create", "创建场景", userId);
        return scene.getId();
    }

    private void applyCreateFields(ApiScene scene, ApiSceneCreateReqDTO reqDTO) {
        scene.setName(reqDTO.getName());
        scene.setModuleId(reqDTO.getModuleId());
        scene.setDescription(reqDTO.getDescription());
        scene.setEnvironmentId(reqDTO.getEnvironmentId());
        scene.setPriority(reqDTO.getPriority());
        scene.setStatus(normalizeStatus(reqDTO.getStatus()));
        scene.setProcessors(Objects.requireNonNullElse(reqDTO.getProcessors(), List.of()));
    }

    /** 创建态随场景一并落库的步骤：按传入顺序自 1 起排序（测试场景详细设计 3.1.3） */
    private List<Map<String, Object>> buildSteps(List<ApiSceneStepSaveReqDTO> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int order = 1;
        for (ApiSceneStepSaveReqDTO reqDTO : steps) {
            validateStepType(reqDTO.getStepType());
            Map<String, Object> step = buildNewStep(reqDTO);
            step.put("sortOrder", order++);
            result.add(step);
        }
        return result;
    }

    /** 编辑态场景聚合保存：含 id 的步骤局部更新，无 id（前端 new- 临时）新建；
        与 create 对齐在同一事务内落库，任一步骤失败整事务回滚、change_version 不变（测试场景详细设计 3.1.4） */
    private void syncSceneSteps(UUID sceneId, List<ApiSceneStepSaveReqDTO> steps, List<Map<String, Object>> originSteps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        List<Map<String, Object>> current = originSteps == null ? new ArrayList<>() : new ArrayList<>(originSteps);
        int order = 1;
        for (ApiSceneStepSaveReqDTO reqDTO : steps) {
            validateStepType(reqDTO.getStepType());
            if (reqDTO.getId() != null) {
                int idx = SceneStepUtil.findStepIndex(current, reqDTO.getId());
                if (idx >= 0) {
                    partialUpdateStep(current.get(idx), reqDTO);
                    current.get(idx).put("sortOrder",
                            reqDTO.getSortOrder() != null ? reqDTO.getSortOrder() : order);
                }
            } else {
                Map<String, Object> step = buildNewStep(reqDTO);
                step.put("sortOrder", reqDTO.getSortOrder() == null ? order : reqDTO.getSortOrder());
                current.add(step);
            }
            order++;
        }
        persistSteps(sceneId, current);
    }

    /** 对已存在的步骤 map 局部更新（C9：仅更新 reqDTO 实际传入字段） */
    private void partialUpdateStep(Map<String, Object> step, ApiSceneStepSaveReqDTO reqDTO) {
        step.put("name", reqDTO.getName());
        if (reqDTO.getStepType() != null) {
            validateStepType(reqDTO.getStepType());
            step.put("stepType", normalizeStepType(reqDTO.getStepType()));
        }
        if (reqDTO.getEnabled() != null) {
            step.put("enabled", reqDTO.getEnabled());
        }
        if (reqDTO.getRequestConfig() != null) {
            step.put("requestConfig", reqDTO.getRequestConfig());
        }
        if (reqDTO.getProcessors() != null) {
            step.put("processors", reqDTO.getProcessors());
        }
        if (reqDTO.getValidators() != null) {
            step.put("validators", reqDTO.getValidators());
        }
        if (reqDTO.getExtractors() != null) {
            step.put("extractors", reqDTO.getExtractors());
        }
        if (reqDTO.getSortOrder() != null) {
            step.put("sortOrder", reqDTO.getSortOrder());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiSceneUpdateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, id);
        validatePriority(reqDTO.getPriority());
        validateStatus(reqDTO.getStatus());

        int nextVersion = reqDTO.getChangeVersion() + 1;
        ApiScene carrier = new ApiScene();
        carrier.setId(id);
        carrier.setName(reqDTO.getName());
        carrier.setModuleId(reqDTO.getModuleId());
        carrier.setDescription(reqDTO.getDescription());
        carrier.setEnvironmentId(reqDTO.getEnvironmentId());
        carrier.setPriority(reqDTO.getPriority());
        carrier.setStatus(reqDTO.getStatus());
        carrier.setProcessors(reqDTO.getProcessors());
        carrier.setChangeVersion(nextVersion);
        // 场景变量以 JSONB 随场景整体更新：携带该字段时全量覆盖（C9 部分更新，未携带则保留原值）
        if (reqDTO.getVariables() != null) {
            carrier.setVariables(normalizeVariables(reqDTO.getVariables()));
        }
        // 乐观锁：版本号不匹配即 0 行更新（测试场景详细设计 3.1.4）
        int rows = sceneMapper.update(carrier, new LambdaUpdateWrapperX<ApiScene>()
                .eq(ApiScene::getId, id)
                .eq(ApiScene::getChangeVersion, reqDTO.getChangeVersion()));
        if (rows == 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_VERSION_CONFLICT);
        }
        // 步骤随场景在同一事务内一并落库：任一失败整事务回滚，change_version 不会误增
        syncSceneSteps(id, reqDTO.getSteps(), scene.getSteps());
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
        copy.setPriority(origin.getPriority());
        copy.setVariables(copyVariables(origin.getVariables()));
        copy.setProcessors(origin.getProcessors());
        copy.setChangeVersion(1);
        copy.setStatus(SCENE_STATUS_DRAFT);
        // 复制模式：步骤与变量全部独立副本，不带链接引用语义（测试场景详细设计 3.1.6）
        copy.setSteps(copySteps(origin.getSteps()));
        sceneMapper.insert(copy);

        writeHistory(projectId, copy.getId(), "copy", "复制自场景「" + origin.getName() + "」", userId);
        return copy.getId();
    }

    /** 场景变量以 JSONB 随场景复制：逐元素深拷贝为独立对象，避免副本与源共享引用 */
    private List<Map<String, Object>> copyVariables(List<Map<String, Object>> originVariables) {
        if (originVariables == null || originVariables.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> variable : originVariables) {
            result.add(SceneStepUtil.deepCopyMap(variable));
        }
        return result;
    }

    /** 深拷贝 steps 列表：重新生成每步及其内嵌变量的 id，sourceType 置 copy，requestConfig 深拷贝 */
    private List<Map<String, Object>> copySteps(List<Map<String, Object>> originSteps) {
        if (originSteps == null || originSteps.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> origin : originSteps) {
            Map<String, Object> copied = new LinkedHashMap<>(origin);
            copied.put("id", UUID.randomUUID());
            copied.put("sourceType", "copy");
            copied.put("requestConfig", SceneStepUtil.deepCopyMap(SceneStepUtil.getMap(origin, "requestConfig")));
            copied.put("processors", SceneStepUtil.copyListWithFreshIds(SceneStepUtil.getList(origin, "processors")));
            copied.put("validators", SceneStepUtil.copyListWithFreshIds(SceneStepUtil.getList(origin, "validators")));
            copied.put("extractors", SceneStepUtil.copyListWithFreshIds(SceneStepUtil.getList(origin, "extractors")));
            // 内嵌 variables 重新生成 id
            List<Map<String, Object>> variables = SceneStepUtil.getList(origin, "variables");
            List<Map<String, Object>> copiedVariables = new ArrayList<>();
            for (Map<String, Object> v : variables) {
                Map<String, Object> cv = new LinkedHashMap<>(v);
                cv.put("id", UUID.randomUUID());
                copiedVariables.add(cv);
            }
            copied.put("variables", copiedVariables);
            copied.put("sortOrder", sortOrder++);
            result.add(copied);
        }
        return result;
    }

    /** 场景变量归一化：过滤空名、trim 名称，空列表落空数组默认值（全量覆盖语义，测试场景详细设计 3.5.1） */
    private List<Map<String, Object>> normalizeVariables(List<ApiSceneVariableBatchReqDTO.Variable> variables) {
        if (variables == null || variables.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ApiSceneVariableBatchReqDTO.Variable variable : variables) {
            if (variable.getName() == null || variable.getName().isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", variable.getName().trim());
            row.put("value", variable.getValue());
            row.put("description", variable.getDescription());
            result.add(row);
        }
        return result;
    }

    // ========== 步骤管理 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID createStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneStepSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        validateStepType(reqDTO.getStepType());
        List<Map<String, Object>> steps = scene.getSteps() == null ? new ArrayList<>() : new ArrayList<>(scene.getSteps());
        Map<String, Object> step = buildNewStep(reqDTO);
        step.put("sortOrder", reqDTO.getSortOrder() == null
                ? SceneStepUtil.maxSortOrder(steps) + 1 : reqDTO.getSortOrder());
        UUID stepId = SceneStepUtil.getUUID(step, "id");
        steps.add(step);
        persistSteps(sceneId, steps);
        return stepId;
    }

    /** 由 DTO 构建新的步骤 map（默认值：enabled=true、sourceType=custom、stepType=http） */
    private Map<String, Object> buildNewStep(ApiSceneStepSaveReqDTO reqDTO) {
        Map<String, Object> step = SceneStepUtil.newStep(UUID.randomUUID());
        step.put("name", reqDTO.getName());
        step.put("stepType", normalizeStepType(reqDTO.getStepType()));
        step.put("enabled", Objects.requireNonNullElse(reqDTO.getEnabled(), true));
        step.put("sourceType", Objects.requireNonNullElse(reqDTO.getSourceType(), "custom"));
        step.put("sourceId", reqDTO.getSourceId());
        step.put("requestConfig", Objects.requireNonNullElse(reqDTO.getRequestConfig(), Map.of()));
        step.put("processors", Objects.requireNonNullElse(reqDTO.getProcessors(), List.of()));
        step.put("validators", Objects.requireNonNullElse(reqDTO.getValidators(), List.of()));
        step.put("extractors", Objects.requireNonNullElse(reqDTO.getExtractors(), List.of()));
        step.put("variables", List.of());
        return step;
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

    /** 以 carrier（仅 id + steps）向 api_scene 落步骤列（C9 部分更新） */
    private void persistSteps(UUID sceneId, List<Map<String, Object>> steps) {
        ApiScene carrier = new ApiScene();
        carrier.setId(sceneId);
        carrier.setSteps(steps);
        sceneMapper.updateById(carrier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiSceneQuickCreateRespDTO quickCreateSteps(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, ApiSceneStepQuickCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        String mode = normalizeMode(reqDTO.getMode());
        ApiInterface apiInterface = interfaceMapper.selectById(reqDTO.getInterfaceId());
        if (apiInterface == null || !projectId.equals(apiInterface.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_INTERFACE_NOT_FOUND);
        }

        List<Map<String, Object>> steps = scene.getSteps() == null ? new ArrayList<>() : new ArrayList<>(scene.getSteps());
        int order = SceneStepUtil.maxSortOrder(steps) + 1;
        List<ApiSceneQuickCreateRespDTO.CreatedStep> created = new ArrayList<>();

        Map<String, Object> mainStep = buildFromInterface(apiInterface, mode, order++);
        steps.add(mainStep);
        created.add(toCreatedStep(mainStep));
        persistSteps(sceneId, steps);
        return ApiSceneQuickCreateRespDTO.builder().steps(created).build();
    }

    private Map<String, Object> buildFromInterface(ApiInterface apiInterface, String mode, int sortOrder) {
        Map<String, Object> requestConfig = new LinkedHashMap<>();
        requestConfig.put("method", apiInterface.getMethod());
        requestConfig.put("url", apiInterface.getPath());
        requestConfig.put("headers", withEntryIds(apiInterface.getHeaders()));
        requestConfig.put("params", withEntryIds(apiInterface.getQueryParams()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", Objects.requireNonNullElse(apiInterface.getBodyType(), "none"));
        body.put("content", apiInterface.getBody());
        requestConfig.put("body", body);

        Map<String, Object> step = SceneStepUtil.newStep(UUID.randomUUID());
        step.put("name", apiInterface.getName());
        step.put("sourceType", mode);
        step.put("sourceId", apiInterface.getId());
        step.put("sourceInterfaceId", apiInterface.getId());
        step.put("sourceInterfaceName", apiInterface.getName());
        step.put("requestConfig", requestConfig);
        step.put("stepType", "http");
        step.put("sortOrder", sortOrder);
        step.put("enabled", true);
        step.put("processors", List.of());
        step.put("validators", Objects.requireNonNullElse(apiInterface.getValidators(), List.of()));
        step.put("extractors", Objects.requireNonNullElse(apiInterface.getExtractors(), List.of()));
        step.put("variables", List.of());
        return step;
    }

    private ApiSceneQuickCreateRespDTO.CreatedStep toCreatedStep(Map<String, Object> step) {
        return ApiSceneQuickCreateRespDTO.CreatedStep.builder()
                .id(SceneStepUtil.getUUID(step, "id"))
                .name(SceneStepUtil.getString(step, "name", null))
                .sourceType(SceneStepUtil.getString(step, "sourceType", null))
                .sourceInterfaceName(SceneStepUtil.getString(step, "sourceInterfaceName", null))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId, UUID stepId,
            ApiSceneStepSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        Map<String, Object> step = SceneStepUtil.requireStep(scene.getSteps(), stepId);
        partialUpdateStep(step, reqDTO);
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
        Set<UUID> owned = SceneStepUtil.collectStepIds(existing);
        Set<UUID> incoming = new LinkedHashSet<>(reqDTO.getStepIds());
        if (!owned.containsAll(incoming) || incoming.size() != reqDTO.getStepIds().size()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_STEP_NOT_FOUND);
        }
        // 数组顺序即为新排序（测试场景详细设计 3.3.6）
        Map<UUID, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> step : existing) {
            byId.put(SceneStepUtil.getUUID(step, "id"), step);
        }
        List<Map<String, Object>> reordered = new ArrayList<>();
        int order = 0;
        for (UUID stepId : reqDTO.getStepIds()) {
            Map<String, Object> step = byId.get(stepId);
            step.put("sortOrder", order++);
            reordered.add(step);
        }
        persistSteps(sceneId, reordered);
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
        Map<String, Object> copied = new LinkedHashMap<>(origin);
        copied.put("id", UUID.randomUUID());
        copied.put("name", name);
        copied.put("sourceType", "copy");
        copied.put("requestConfig", SceneStepUtil.deepCopyMap(SceneStepUtil.getMap(origin, "requestConfig")));
        copied.put("processors", SceneStepUtil.copyListWithFreshIds(SceneStepUtil.getList(origin, "processors")));
        copied.put("validators", SceneStepUtil.copyListWithFreshIds(SceneStepUtil.getList(origin, "validators")));
        copied.put("extractors", SceneStepUtil.copyListWithFreshIds(SceneStepUtil.getList(origin, "extractors")));
        List<Map<String, Object>> variables = SceneStepUtil.getList(origin, "variables");
        List<Map<String, Object>> copiedVariables = new ArrayList<>();
        for (Map<String, Object> v : variables) {
            Map<String, Object> cv = new LinkedHashMap<>(v);
            cv.put("id", UUID.randomUUID());
            copiedVariables.add(cv);
        }
        copied.put("variables", copiedVariables);
        copied.put("sortOrder", SceneStepUtil.maxSortOrder(scene.getSteps()) + 1);
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
            ApiSceneStepVariableBatchReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        Map<String, Object> step = SceneStepUtil.requireStep(scene.getSteps(), stepId);
        List<Map<String, Object>> variables = new ArrayList<>();
        int order = 0;
        if (reqDTO.getVariables() != null) {
            for (var variable : reqDTO.getVariables()) {
                if (variable.getName() == null || variable.getName().isBlank()) {
                    continue;
                }
                // 手动更新的变量置 custom（测试场景详细设计 3.4.2）
                variables.add(SceneStepUtil.newVariable(UUID.randomUUID(),
                        variable.getName().trim(), variable.getValue(), "custom",
                        null, variable.getDescription(), order++));
            }
        }
        step.put("variables", variables);
        persistSteps(sceneId, scene.getSteps());
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
        ApiScene scene = requireScene(projectId, sceneId);
        Map<String, Object> step = null;
        if ("step_validator".equals(target) || "step_extractor".equals(target)) {
            step = SceneStepUtil.requireStep(scene.getSteps(), reqDTO.getStepId());
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
                    List<Map<String, Object>> validators = new ArrayList<>(SceneStepUtil.getList(step, "validators"));
                    validators.add(config);
                    step.put("validators", validators);
                    imported++;
                }
                case "step_extractor" -> {
                    List<Map<String, Object>> extractors = new ArrayList<>(SceneStepUtil.getList(step, "extractors"));
                    extractors.add(config);
                    step.put("extractors", extractors);
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
            List<Map<String, Object>> processors = new ArrayList<>(
                    Objects.requireNonNullElse(scene.getProcessors(), List.of()));
            processors.addAll(ordered);
            ApiScene carrier = new ApiScene();
            carrier.setId(sceneId);
            carrier.setProcessors(processors);
            sceneMapper.updateById(carrier);
            imported += processorConfigs.size();
        } else if (step != null) {
            persistSteps(sceneId, scene.getSteps());
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

    private void validatePriority(String priority) {
        if (priority != null && !SCENE_PRIORITIES.contains(priority)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_SETTING_INVALID,
                    "优先级仅支持 P0/P1/P2/P3");
        }
    }

    private void validateStatus(String status) {
        if (status != null && !SCENE_STATUSES.contains(status)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_SETTING_INVALID,
                    "状态仅支持 draft/published");
        }
    }

    /** 缺省落草稿；非法取值已在 validateStatus 拦下 */
    private String normalizeStatus(String status) {
        return Objects.requireNonNullElse(status, SCENE_STATUS_DRAFT);
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
            Map<String, Object> copy = SceneStepUtil.deepCopyMap(entry);
            copy.putIfAbsent("id", UUID.randomUUID().toString());
            copy.putIfAbsent("enabled", true);
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

}