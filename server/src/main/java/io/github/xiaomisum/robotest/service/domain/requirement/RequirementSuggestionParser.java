package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 拆分 / 导入共用的模型输出解析与产物清洗（详设 4.5 采纳入参口径）：
 * 容忍 ```json 代码栅栏与扁平结构；标题缺失的建议丢弃，moduleId / priority 非法置 null；
 * 补 moduleName / newModule：新增模块时清空 moduleId，未标记新增但缺模块名时按命中模块反填；
 * key 统一定序为 item-N，保证确认引用稳定。
 */
public final class RequirementSuggestionParser {

    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}[ \\t]");

    private RequirementSuggestionParser() {
    }

    /**
     * 按 Markdown 标题行切块：块含标题行及其正文，按序拼接严格等于原文；无标题时整篇为一块。
     * 编号只用于模型引用原文，不代表需求边界（详设 4.2）。
     */
    public static List<String> splitDocumentBlocks(String documentText) {
        if (documentText == null || documentText.isEmpty()) {
            return List.of();
        }
        List<String> blocks = new ArrayList<>();
        int start = 0;
        Matcher matcher = HEADING.matcher(documentText);
        while (matcher.find()) {
            if (matcher.start() > start) {
                blocks.add(documentText.substring(start, matcher.start()));
            }
            start = matcher.start();
        }
        if (start < documentText.length()) {
            blocks.add(documentText.substring(start));
        }
        return blocks;
    }

    /** 提示词中的原文标记版；标记仅为引用编号，不属于原文 */
    public static String markBlocks(List<String> blocks) {
        StringBuilder marked = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            marked.append("[B").append(i + 1).append("]\n").append(blocks.get(i));
            if (!blocks.get(i).endsWith("\n")) {
                marked.append('\n');
            }
        }
        return marked.toString();
    }

    /** 解析模型输出为 JSON 对象；容忍前言 / 后记包裹的 JSON 代码块，仍不可解析按 1000018117 落任务失败态 */
    public static Map<String, Object> parseJsonObject(String content) {
        String text = stripCodeFence(content);
        Map<String, Object> parsed = parseOrNull(text);
        if (parsed == null) {
            for (String candidate : extractJsonObjects(text)) {
                parsed = parseOrNull(candidate);
                if (parsed != null) {
                    break;
                }
            }
        }
        if (parsed == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    "模型输出无法解析为 JSON");
        }
        return parsed;
    }

    /** 取出 artifacts 并清洗；缺失或非数组按空产物返回（与拆分详情回读同口径） */
    public static List<Map<String, Object>> sanitizeArtifacts(Map<String, Object> parsed, List<ProjectModule> modules) {
        return sanitizeArtifacts(parsed, modules, null);
    }

    /**
     * 需求导入专用：模型 description 只输出原文引用，按块表回填逐字原文（详设 4.2 / 4.5）。
     * sourceBlocks 为空时保留模型 description（图片来源及其他调用方）。
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> sanitizeArtifacts(Map<String, Object> parsed, List<ProjectModule> modules,
            List<String> sourceBlocks) {
        if (!(parsed.get("artifacts") instanceof List<?> rawArtifacts)) {
            return List.of();
        }
        Set<UUID> moduleIds = modules.stream().map(ProjectModule::getId).collect(Collectors.toSet());
        Map<UUID, String> moduleNames = modules.stream()
                .collect(Collectors.toMap(ProjectModule::getId, ProjectModule::getName, (first, ignored) -> first));
        List<Map<String, Object>> artifacts = new ArrayList<>();
        Set<Integer> referenced = new LinkedHashSet<>();
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
            if (sourceBlocks != null && !sourceBlocks.isEmpty()) {
                referenced.addAll(parseBlockRefs(description, sourceBlocks.size()));
            }
            String moduleId = normalizeModuleId(content.get("moduleId"), moduleIds);
            boolean newModule = Boolean.parseBoolean(asString(content.get("newModule")));
            String moduleName = normalizeModuleName(content.get("moduleName"));
            if (newModule) {
                moduleId = null;
            } else if (moduleName == null && moduleId != null) {
                moduleName = moduleNames.get(UUID.fromString(moduleId));
            }
            content.put("moduleId", moduleId);
            content.put("moduleName", moduleName);
            content.put("newModule", newModule);
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
        fillDescriptions(artifacts, sourceBlocks, referenced);
        return artifacts;
    }

    /** 公共前言 = 未被任何建议认领的块；全部引用非法时每条回退整篇原文，避免文档内容丢失 */
    private static void fillDescriptions(List<Map<String, Object>> artifacts, List<String> sourceBlocks,
            Set<Integer> referenced) {
        if (sourceBlocks == null || sourceBlocks.isEmpty()) {
            return;
        }
        StringBuilder preamble = new StringBuilder();
        for (int i = 0; i < sourceBlocks.size(); i++) {
            if (!referenced.contains(i + 1)) {
                preamble.append(sourceBlocks.get(i));
            }
        }
        String prefix = referenced.isEmpty() ? "" : preamble.toString();
        for (Map<String, Object> artifact : artifacts) {
            Map<String, Object> content = asMap(artifact.get("content"));
            Set<Integer> refs = referenced.isEmpty()
                    ? parseBlockRefs("@full", sourceBlocks.size())
                    : parseBlockRefs(content.get("description"), sourceBlocks.size());
            StringBuilder body = new StringBuilder(prefix);
            for (int i = 0; i < sourceBlocks.size(); i++) {
                if (refs.contains(i + 1)) {
                    body.append(sourceBlocks.get(i));
                }
            }
            content.put("description", body.toString());
        }
    }

    /** 解析 @full / @blocks:B2,B5；非法或缺失引用返回空集合，由调用方决定回退 */
    private static Set<Integer> parseBlockRefs(Object raw, int blockCount) {
        String value = asString(raw);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        String trimmed = value.trim();
        if ("@full".equalsIgnoreCase(trimmed)) {
            Set<Integer> all = new LinkedHashSet<>();
            for (int i = 1; i <= blockCount; i++) {
                all.add(i);
            }
            return all;
        }
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("@blocks:")) {
            return Set.of();
        }
        Set<Integer> refs = new LinkedHashSet<>();
        for (String token : trimmed.substring("@blocks:".length()).split("[,;\\s]+")) {
            String number = token.startsWith("B") || token.startsWith("b") ? token.substring(1) : token;
            if (number.isBlank()) {
                continue;
            }
            try {
                int index = Integer.parseInt(number);
                if (index >= 1 && index <= blockCount) {
                    refs.add(index);
                }
            } catch (NumberFormatException e) {
                // 非法编号忽略；全部无效时由 fillDescriptions 回退整篇原文
            }
        }
        return refs;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    /** 候选模块清单（id|名称），空列表渲染提示语（拆分 / 导入提示词共用变量） */
    public static String moduleOptions(List<ProjectModule> modules) {
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

    public static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            Object parsed = JsonUtils.parseObject(text, Map.class);
            return parsed == null ? new LinkedHashMap<>() : (Map<String, Object>) parsed;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从混杂文本中按花括号配平提取全部完整对象候选；字符串内的括号与转义不参与配平 */
    private static List<String> extractJsonObjects(String text) {
        List<String> candidates = new ArrayList<>();
        if (text == null) {
            return candidates;
        }
        for (int start = text.indexOf('{'); start >= 0; start = text.indexOf('{', start + 1)) {
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        candidates.add(text.substring(start, i + 1));
                        break;
                    }
                }
            }
        }
        return candidates;
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

    private static String normalizeModuleName(Object raw) {
        String value = asString(raw);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeModuleId(Object raw, Set<UUID> moduleIds) {
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
