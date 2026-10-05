package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 拆分 / 导入共用的模型输出解析与产物清洗（详设 4.5 采纳入参口径）：
 * 容忍 ```json 代码栅栏与扁平结构；标题缺失的建议丢弃，moduleId / priority 非法置 null；
 * key 统一定序为 item-N，保证确认引用稳定。
 */
public final class RequirementSuggestionParser {

    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");

    private RequirementSuggestionParser() {
    }

    /** 解析模型输出为 JSON 对象；不可解析按模型调用失败（1000018117）落任务失败态 */
    public static Map<String, Object> parseJsonObject(String content) {
        String text = stripCodeFence(content);
        Map<String, Object> parsed;
        try {
            parsed = JsonUtils.parseObject(text, Map.class);
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    "模型输出无法解析为 JSON");
        }
        return parsed == null ? Map.of() : parsed;
    }

    /** 取出 artifacts 并清洗；缺失或非数组按空产物返回（与拆分详情回读同口径） */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> sanitizeArtifacts(Map<String, Object> parsed, List<ProjectModule> modules) {
        if (!(parsed.get("artifacts") instanceof List<?> rawArtifacts)) {
            return List.of();
        }
        Set<UUID> moduleIds = modules.stream().map(ProjectModule::getId).collect(Collectors.toSet());
        List<Map<String, Object>> artifacts = new ArrayList<>();
        int seq = 0;
        for (Object element : rawArtifacts) {
            if (!(element instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> content = new LinkedHashMap<>();
            if (raw.get("content") instanceof Map<?, ?> rawContent) {
                rawContent.forEach((k, v) -> content.put(String.valueOf(k), v));
            } else {
                // 容忍扁平结构（模型未按 content 包裹）：直接取顶层字段
                raw.forEach((k, v) -> content.put(String.valueOf(k), v));
            }
            String title = asString(content.get("title"));
            if (title == null || title.isBlank()) {
                continue;
            }
            content.put("title", title.trim());
            String description = asString(content.get("description"));
            content.put("description", description == null || description.isBlank() ? null : description);
            content.put("moduleId", normalizeModuleId(content.get("moduleId"), moduleIds));
            content.put("priority", normalizePriority(content.get("priority")));

            Map<String, Object> artifact = new LinkedHashMap<>();
            seq++;
            artifact.put("key", "item-" + seq);
            artifact.put("kind", "requirement_suggestion");
            artifact.put("title", content.get("title"));
            artifact.put("content", content);
            String sourceRef = asString(raw.get("sourceRef"));
            if (sourceRef != null && !sourceRef.isBlank()) {
                artifact.put("sourceRef", sourceRef.trim());
            }
            artifacts.add(artifact);
        }
        return artifacts;
    }

    /** 候选模块清单（id|名称），空列表渲染提示语（拆分 / 导入提示词共用变量） */
    static String moduleOptions(List<ProjectModule> modules) {
        if (modules.isEmpty()) {
            return "（当前项目暂无模块，moduleId 一律为 null）";
        }
        StringBuilder builder = new StringBuilder();
        for (ProjectModule module : modules) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(module.getId()).append('|').append(module.getName());
        }
        return builder.toString();
    }

    static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String stripCodeFence(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstLineEnd + 1);
        int closing = body.lastIndexOf("```");
        if (closing >= 0) {
            body = body.substring(0, closing);
        }
        return body.trim();
    }

    private static Object normalizeModuleId(Object raw, Set<UUID> moduleIds) {
        String value = asString(raw);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            UUID id = UUID.fromString(value.trim());
            return moduleIds.contains(id) ? id.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizePriority(Object raw) {
        String value = asString(raw);
        return value != null && PRIORITIES.contains(value.trim()) ? value.trim() : null;
    }
}
