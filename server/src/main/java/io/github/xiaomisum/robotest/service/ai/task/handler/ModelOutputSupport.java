package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.domain.requirement.RequirementSuggestionParser;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 模型输出解析共用支撑（生成链详设 3.3 各阶段口径一致）：
 * 解析、类型取值与产物清单提取，供生成链与圈选处理器复用。
 */
public final class ModelOutputSupport {

    private ModelOutputSupport() {
    }

    /** 解析模型输出的 artifacts 清单；不可解析由解析入口按 1000018117 落任务失败态 */
    public static List<Map<String, Object>> parsedArtifacts(AiChatReply reply) {
        Map<String, Object> parsed = RequirementSuggestionParser.parseJsonObject(reply.content());
        if (!(parsed.get("artifacts") instanceof List<?> raw)) {
            throw modelFailed("模型未产出产物清单");
        }
        return raw.stream()
                .filter(element -> element instanceof Map)
                .map(ModelOutputSupport::castMap)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** 取产物 content；缺失时就地补空对象（清洗过程可直接写回） */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> contentOf(Map<String, Object> artifact) {
        Object content = artifact.get("content");
        if (content instanceof Map) {
            return (Map<String, Object>) content;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        artifact.put("content", created);
        return created;
    }

    public static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, value) -> converted.put(String.valueOf(key), value));
            return converted;
        }
        return new LinkedHashMap<>();
    }

    public static List<String> stringList(Object raw, int limit) {
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> values = new ArrayList<>();
        for (Object element : list) {
            if (values.size() >= limit) {
                break;
            }
            String value = asString(element);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    public static String asString(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    /** 解析模型输出中的 uuid 字段；缺失或畸形返回 null（按未响应处理） */
    public static UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String nvl(String value) {
        return value == null ? "" : value;
    }

    public static String abbreviate(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    public static ServiceException modelFailed(String message) {
        return ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), message);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object element) {
        return (Map<String, Object>) element;
    }
}
