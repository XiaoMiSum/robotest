package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.CommonComponent;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.CommonComponentMapper;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 全局资产引入规划器（接口测试域重构方案 04 §4.2）：把组件处理器/校验器/提取器解析为步骤内嵌配置，
 * 并完成落库（处理器列/步骤列部分更新，C9）。写变更历史由场景服务负责（C2）。
 */
public final class SceneAssetImporter {

    private SceneAssetImporter() {
    }

    private static final Set<String> VALID_TARGETS = Set.of("scene_processor", "step_validator", "step_extractor");

    /** 引入规划结果：orderedProcessors 为按组件 sortOrder 升序的处理器（场景级目标），step 目标就地变更 */
    public record Plan(List<Map<String, Object>> orderedProcessors, int stepTargetCount) {
    }

    /** 校验目标合法性并解析目标步骤；场景级目标返回 null，步骤级目标返回场景中对应步骤 */
    public static Map<String, Object> resolveTarget(ApiScene scene, String target, UUID stepId) {
        if (!VALID_TARGETS.contains(target)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "target 不合法");
        }
        if (("step_validator".equals(target) || "step_extractor".equals(target)) && stepId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED,
                    "step_validator/step_extractor 需要 stepId");
        }
        if ("step_validator".equals(target) || "step_extractor".equals(target)) {
            return SceneStepUtil.requireStep(scene.getSteps(), stepId);
        }
        return null;
    }

    /** 组合规划与落库：返回实际引入数量（处理器按组件 sortOrder 升序并入处理器列） */
    public static int apply(ApiSceneMapper sceneMapper, CommonComponentMapper componentMapper,
            ApiScene scene, Map<String, Object> step, String target, List<UUID> assetIds) {
        Plan plan = plan(componentMapper, step, target, assetIds);
        int imported = 0;
        if ("scene_processor".equals(target) && !plan.orderedProcessors().isEmpty()) {
            List<Map<String, Object>> processors = new ArrayList<>(
                    Objects.requireNonNullElse(scene.getProcessors(), List.of()));
            processors.addAll(plan.orderedProcessors());

            ApiScene carrier = new ApiScene();
            carrier.setId(scene.getId());
            carrier.setProcessors(processors);
            sceneMapper.updateById(carrier);
            imported += plan.orderedProcessors().size();
        } else if (step != null) {
            ApiScene carrier = new ApiScene();
            carrier.setId(scene.getId());
            carrier.setSteps(scene.getSteps());
            sceneMapper.updateById(carrier);
        }
        imported += plan.stepTargetCount();
        return imported;
    }

    public static Plan plan(CommonComponentMapper componentMapper, Map<String, Object> step,
            String target, List<UUID> assetIds) {
        int stepTargetCount = 0;
        List<Map<String, Object>> processorConfigs = new ArrayList<>();
        List<Integer> processorSortOrders = new ArrayList<>();
        for (UUID assetId : assetIds) {
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
                    stepTargetCount++;
                }
                case "step_extractor" -> {
                    List<Map<String, Object>> extractors = new ArrayList<>(SceneStepUtil.getList(step, "extractors"));
                    extractors.add(config);
                    step.put("extractors", extractors);
                    stepTargetCount++;
                }
            }
        }
        List<Map<String, Object>> ordered = new ArrayList<>();
        if ("scene_processor".equals(target) && !processorConfigs.isEmpty()) {
            List<Map.Entry<Integer, Map<String, Object>>> pairs = new ArrayList<>();
            for (int i = 0; i < processorConfigs.size(); i++) {
                pairs.add(Map.entry(processorSortOrders.get(i), processorConfigs.get(i)));
            }
            pairs.sort(Comparator.comparingInt(Map.Entry::getKey));
            for (Map.Entry<Integer, Map<String, Object>> pair : pairs) {
                ordered.add(pair.getValue());
            }
        }
        return new Plan(ordered, stepTargetCount);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseConfig(String configJson) {
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
}