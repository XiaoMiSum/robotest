package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScenePageItemRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneFollowMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 场景详情/列表装配器（接口测试域重构方案 04 §4.2）：把场景实体及其关联信息组装为响应 DTO。
 * <p>
 * 无状态静态工具，需要数据源时由调用方注入 mapper 或断言函数（C2：装配不直接查询）。
 */
public final class SceneDetailAssembler {

    private SceneDetailAssembler() {
    }

    /** 列表页装配：分页查询 + 步骤数/最近执行/关注标记聚合（测试场景详细设计 3.1.1） */
    public static PageResult<ApiScenePageItemRespDTO> fetchPage(ApiSceneMapper sceneMapper,
            ApiSceneFollowMapper followMapper, ApiExecutionRecordMapper executionRecordMapper,
            UUID projectId, UUID moduleId, String search, Boolean followedOnly, String status,
            PageParam pageParam, UUID userId) {
        List<UUID> followedIds = null;
        if (Boolean.TRUE.equals(followedOnly)) {
            followedIds = followMapper.selectFollowedSceneIdsByUserId(userId);
        }
        PageResult<ApiScene> page = sceneMapper.selectPage(projectId, moduleId, search,
                followedIds, status, pageParam);
        List<ApiScene> scenes = page.getList();
        if (scenes.isEmpty()) {
            return new PageResult<>(List.of(), page.getTotal());
        }
        List<UUID> sceneIds = scenes.stream().map(ApiScene::getId).toList();
        Map<UUID, Long> stepCounts = countSteps(sceneMapper, sceneIds);
        Map<UUID, ApiExecutionRecord> latestExecutions =
                latestExecutions(executionRecordMapper, sceneIds);
        Set<UUID> followedSet = new LinkedHashSet<>(followMapper.selectFollowedSceneIdsByUserId(userId));
        return new PageResult<>(scenes.stream()
                .map(scene -> toItem(scene, stepCounts, latestExecutions, followedSet)).toList(),
                page.getTotal());
    }

    public static Map<UUID, Long> countSteps(ApiSceneMapper sceneMapper, List<UUID> sceneIds) {
        List<ApiScene> scenes = sceneMapper.selectBatchIds(sceneIds);
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (ApiScene scene : scenes) {
            List<Map<String, Object>> steps = scene.getSteps();
            counts.put(scene.getId(), steps == null ? 0L : steps.size());
        }
        return counts;
    }

    /** 列表页最近执行徽标：一次取回页内场景的执行记录，内存中按场景保留最新一条 */
    public static Map<UUID, ApiExecutionRecord> latestExecutions(ApiExecutionRecordMapper executionRecordMapper,
            List<UUID> sceneIds) {
        List<ApiExecutionRecord> records = executionRecordMapper.listLatestBySceneIds(sceneIds);
        Map<UUID, ApiExecutionRecord> latest = new LinkedHashMap<>();
        for (ApiExecutionRecord record : records) {
            latest.putIfAbsent(record.getSceneId(), record);
        }
        return latest;
    }

    public static ApiScenePageItemRespDTO toItem(ApiScene scene, Map<UUID, Long> stepCounts,
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

    public static ApiSceneDetailRespDTO toDetail(ApiScene scene, boolean followed,
            Predicate<UUID> sourceExists) {
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
                        .map(step -> toStepDetail(step, sourceExists)).toList())
                .build();
    }

    public static ApiSceneDetailRespDTO.Step toStepDetail(Map<String, Object> step,
            Predicate<UUID> sourceExists) {
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
                .sourceMissing(isLinkSourceMissing(step, sourceExists))
                .requestConfig(SceneStepUtil.getMap(step, "requestConfig"))
                .variables(SceneStepUtil.getList(step, "variables"))
                .processors(SceneStepUtil.getList(step, "processors"))
                .validators(SceneStepUtil.getList(step, "validators"))
                .extractors(SceneStepUtil.getList(step, "extractors"))
                .build();
    }

    /** 链接引用源被删除时置灰展示（测试场景详细设计 4.5） */
    public static boolean isLinkSourceMissing(Map<String, Object> step, Predicate<UUID> sourceExists) {
        if (!"link".equals(SceneStepUtil.getString(step, "sourceType", null))
                || SceneStepUtil.getUUID(step, "sourceId") == null) {
            return false;
        }
        UUID sourceId = SceneStepUtil.getUUID(step, "sourceId");
        return !sourceExists.test(sourceId);
    }
}