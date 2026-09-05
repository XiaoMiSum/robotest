package io.github.xiaomisum.robotest.framework.common;

import xyz.migoo.framework.common.util.JsonUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 场景步骤 JSONB 工具：对 List<Map<String,Object>> 的 steps 列表做读写辅助
 */
public final class SceneStepUtil {

    private SceneStepUtil() {
    }

    /** 从 steps 列表中按 id 查找步骤 map，找不到返回 null */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> findStep(List<Map<String, Object>> steps, UUID stepId) {
        if (steps == null || stepId == null) {
            return null;
        }
        for (Map<String, Object> step : steps) {
            Object id = step.get("id");
            if (stepId.equals(toUUID(id))) {
                return step;
            }
        }
        return null;
    }

    /** 从 steps 列表中按 id 查找步骤 map 并返回其索引，找不到返回 -1 */
    @SuppressWarnings("unchecked")
    public static int findStepIndex(List<Map<String, Object>> steps, UUID stepId) {
        if (steps == null || stepId == null) {
            return -1;
        }
        for (int i = 0; i < steps.size(); i++) {
            Object id = steps.get(i).get("id");
            if (stepId.equals(toUUID(id))) {
                return i;
            }
        }
        return -1;
    }

    /** 获取步骤中指定 key 的 String 值 */
    public static String getString(Map<String, Object> step, String key, String defaultValue) {
        Object v = step.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    /** 获取步骤中指定 key 的 UUID 值 */
    public static UUID getUUID(Map<String, Object> step, String key) {
        return toUUID(step.get(key));
    }

    /** 获取步骤中指定 key 的 Boolean 值 */
    public static Boolean getBoolean(Map<String, Object> step, String key) {
        Object v = step.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return null;
    }

    /** 获取步骤中指定 key 的 Integer 值 */
    public static Integer getInteger(Map<String, Object> step, String key) {
        Object v = step.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    /** 获取步骤中指定 key 的 Map 值 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> step, String key) {
        Object v = step.get(key);
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    /** 获取步骤中指定 key 的 List 值 */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getList(Map<String, Object> step, String key) {
        Object v = step.get(key);
        if (v instanceof List<?> l) {
            return (List<Map<String, Object>>) (List<?>) l;
        }
        return List.of();
    }

    /** 将 steps 列表中所有步骤 id 收集为 Set<UUID> */
    public static Set<UUID> collectStepIds(List<Map<String, Object>> steps) {
        if (steps == null) {
            return Set.of();
        }
        return steps.stream()
                .map(s -> toUUID(s.get("id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 计算 steps 列表中的最大 sortOrder，空列表返回 -1 */
    public static int maxSortOrder(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return -1;
        }
        return steps.stream()
                .map(s -> getInteger(s, "sortOrder"))
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
    }

    /** 创建一个新的步骤 map，使用指定的 UUID id */
    public static Map<String, Object> newStep(UUID id) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        return step;
    }

    /** 创建一个新的变量 map */
    public static Map<String, Object> newVariable(UUID id, String name, String value,
            String source, UUID interfaceVariableId, String description, Integer sortOrder) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", id);
        v.put("name", name);
        v.put("value", value);
        v.put("source", source);
        v.put("interfaceVariableId", interfaceVariableId);
        v.put("description", description);
        v.put("sortOrder", sortOrder);
        return v;
    }

    /** 对整个步骤列表做深拷贝（通过 JSON 序列化往返） */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> deepCopySteps(List<Map<String, Object>> steps) {
        if (steps == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> step : steps) {
            result.add((Map<String, Object>) JsonUtils.parseObject(
                    JsonUtils.toJsonString(step), LinkedHashMap.class));
        }
        return result;
    }

    /** 对步骤内嵌的 processors/validators/extractors 列表深拷贝并重新生成每个元素的 id */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> copyListWithFreshIds(List<Map<String, Object>> origin) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (origin == null) {
            return result;
        }
        for (Map<String, Object> item : origin) {
            Map<String, Object> copy = JsonUtils.parseObject(
                    JsonUtils.toJsonString(item), LinkedHashMap.class);
            copy.put("id", UUID.randomUUID().toString());
            result.add(copy);
        }
        return result;
    }

    /** 对 Map 做深拷贝（JSON 序列化往返），null 返回空 Map */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepCopyMap(Map<String, Object> origin) {
        if (origin == null) {
            return new LinkedHashMap<>();
        }
        return JsonUtils.parseObject(JsonUtils.toJsonString(origin), LinkedHashMap.class);
    }

    /** 从 steps 列表中按 id 查找步骤并返回，找不到抛出 API_SCENE_STEP_NOT_FOUND */
    public static Map<String, Object> requireStep(List<Map<String, Object>> steps, UUID stepId) {
        Map<String, Object> step = findStep(steps, stepId);
        if (step == null) {
            throw xyz.migoo.framework.common.exception.ServiceExceptionUtil
                    .get(ErrorCodeConstants.API_SCENE_STEP_NOT_FOUND);
        }
        return step;
    }

    private static UUID toUUID(Object obj) {
        if (obj instanceof UUID u) {
            return u;
        }
        if (obj instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
