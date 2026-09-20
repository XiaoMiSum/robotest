package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneQuickCreateRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 场景步骤编辑器（接口测试域重构方案 04 §4.2）：步骤构建/合并/重排的纯函数，零 IO 零状态。
 * <p>
 * 所有方法与操作均不接触数据源（C2）：持久化由场景服务层负责，这里只负责「怎么变」。
 */
public final class SceneStepEditor {

    private SceneStepEditor() {
    }

    /** 由 DTO 构建新的步骤 map（默认值：enabled=true、sourceType=custom、stepType=http） */
    public static Map<String, Object> newStep(ApiSceneStepSaveReqDTO reqDTO) {
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

    public static String normalizeStepType(String stepType) {
        return Objects.requireNonNullElse(stepType, "http").toLowerCase();
    }

    /** V1.2 执行引擎仅覆盖 http 取样器（与快速调试域口径一致），jdbc 允许存储但不允许编排为可执行步骤 */
    public static void validateStepType(String stepType) {
        String type = normalizeStepType(stepType);
        if (!"http".equals(type)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED,
                    "V1.2 仅支持 http 类型步骤");
        }
    }

    /** 创建态随场景一并落库的步骤：按传入顺序自 1 起排序（测试场景详细设计 3.1.3） */
    public static List<Map<String, Object>> buildSteps(List<ApiSceneStepSaveReqDTO> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int order = 1;
        for (ApiSceneStepSaveReqDTO reqDTO : steps) {
            validateStepType(reqDTO.getStepType());
            Map<String, Object> step = newStep(reqDTO);
            step.put("sortOrder", order++);
            result.add(step);
        }
        return result;
    }

    /** 对已存在的步骤 map 局部更新（C9：仅更新实际传入字段），入参 step 即场景实例持有的步骤 */
    public static void partialUpdateStep(Map<String, Object> step, ApiSceneStepSaveReqDTO reqDTO) {
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

    /** 编辑态步骤聚合合并（测试场景详细设计 3.1.4）：含 id 的步骤局部更新，无 id（前端 new- 临时）新建。
     *  纯合并返回最新步骤列表，由调用方决定是否落库。 */
    public static List<Map<String, Object>> mergeSteps(List<Map<String, Object>> originSteps,
            List<ApiSceneStepSaveReqDTO> steps) {
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
                Map<String, Object> step = newStep(reqDTO);
                step.put("sortOrder", reqDTO.getSortOrder() == null ? order : reqDTO.getSortOrder());
                current.add(step);
            }
            order++;
        }
        return current;
    }

    /** 步骤重排（测试场景详细设计 3.3.6）：数组顺序即新排序，含 id 归属校验 */
    public static List<Map<String, Object>> reorder(List<Map<String, Object>> existing, List<UUID> stepIds) {
        Set<UUID> owned = SceneStepUtil.collectStepIds(existing);
        Set<UUID> incoming = new LinkedHashSet<>(stepIds);
        if (!owned.containsAll(incoming) || incoming.size() != stepIds.size()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_STEP_NOT_FOUND);
        }
        Map<UUID, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> step : existing) {
            byId.put(SceneStepUtil.getUUID(step, "id"), step);
        }
        List<Map<String, Object>> reordered = new ArrayList<>();
        int order = 0;
        for (UUID stepId : stepIds) {
            Map<String, Object> step = byId.get(stepId);
            step.put("sortOrder", order++);
            reordered.add(step);
        }
        return reordered;
    }

    /** 步骤复制：深度拷贝请求配置与处理器/校验器/提取器，条目 id 全部重新生成 */
    public static Map<String, Object> copyStep(Map<String, Object> origin, String name,
            List<Map<String, Object>> existingSteps) {
        Map<String, Object> copied = new LinkedHashMap<>(origin);
        copied.put("id", UUID.randomUUID());
        copied.put("name", name);
        copied.put("sourceType", "copy");
        copied.put("requestConfig", SceneStepUtil.deepCopyMap(SceneStepUtil.getMap(origin, "requestConfig")));
        copied.put("processors", SceneStepUtil.copyListWithFreshIds(SceneStepUtil.getList(origin, "processors")));
        copied.put("validators", SceneStepUtil.copyListWithFreshIds(SceneStepUtil.getList(origin, "validators")));
        copied.put("extractors", SceneStepUtil.copyListWithFreshIds(SceneStepUtil.getList(origin, "extractors")));
        List<Map<String, Object>> copiedVariables = new ArrayList<>();
        for (Map<String, Object> v : SceneStepUtil.getList(origin, "variables")) {
            Map<String, Object> cv = new LinkedHashMap<>(v);
            cv.put("id", UUID.randomUUID());
            copiedVariables.add(cv);
        }
        copied.put("variables", copiedVariables);
        copied.put("sortOrder", SceneStepUtil.maxSortOrder(existingSteps) + 1);
        return copied;
    }

    /** 从接口定义快速生成步骤（测试场景详细设计 3.2.1），link/copy 由 mode 决定 sourceType */
    public static Map<String, Object> newStepFromInterface(ApiInterface apiInterface, String mode, int sortOrder) {
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

    public static ApiSceneQuickCreateRespDTO.CreatedStep toCreatedStep(Map<String, Object> step) {
        return ApiSceneQuickCreateRespDTO.CreatedStep.builder()
                .id(SceneStepUtil.getUUID(step, "id"))
                .name(SceneStepUtil.getString(step, "name", null))
                .sourceType(SceneStepUtil.getString(step, "sourceType", null))
                .sourceInterfaceName(SceneStepUtil.getString(step, "sourceInterfaceName", null))
                .build();
    }

    /** 场景变量归一化：过滤空名、trim 名称，空列表落空数组默认值（全量覆盖语义，测试场景详细设计 3.5.1） */
    public static List<Map<String, Object>> normalizeVariables(List<ApiSceneVariableBatchReqDTO.Variable> variables) {
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

    /** 步骤变量手动更新：过滤空名、trim 名称，source 置 custom（测试场景详细设计 3.4.2） */
    public static List<Map<String, Object>> buildStepVariables(
            List<ApiSceneStepVariableBatchReqDTO.Variable> variables) {
        List<Map<String, Object>> result = new ArrayList<>();
        int order = 0;
        if (variables != null) {
            for (var variable : variables) {
                if (variable.getName() == null || variable.getName().isBlank()) {
                    continue;
                }
                result.add(SceneStepUtil.newVariable(UUID.randomUUID(),
                        variable.getName().trim(), variable.getValue(), "custom",
                        null, variable.getDescription(), order++));
            }
        }
        return result;
    }

    /** 行内条目启用态过滤并补 id；接口域行结构 {key, value, enabled} */
    public static List<Map<String, Object>> withEntryIds(List<Map<String, Object>> entries) {
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
}