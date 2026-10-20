package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.bug.BugAttachmentMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatMedia;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.domain.requirement.RequirementSuggestionParser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.stringList;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 缺陷分类建议处理器（缺陷分析详设 3.6）：存量批量模式逐缺陷产出待确认建议（最多 500、
 * 按分片多次调用）。截图入模（详设 3.6）：每缺陷 ≤2 张且全批 ≤8 张按缺陷序截断，
 * 批量缺陷集存在图像类附件时提交期派生 vision 必需能力（无视觉模型走 1000018124）。
 * 新建草稿建议已改实时 SSE 流式接口（详设 3.5，BugDraftSuggestService），不走任务。
 */
@Component
public class BugClassifyHandler implements TaskHandler {

    public static final String TYPE = "bug_classify";

    private static final String SYSTEM_PROMPT = "你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。";

    private static final int KEYWORD_LIMIT = 5;
    /** 批量分类上限（详设 3.6）与单次模型调用的缺陷分片大小 */
    private static final int BATCH_LIMIT = 500;
    private static final int BATCH_SHARD = 20;
    /** 截图入模口径（详设 3.6）：每缺陷 ≤2 张且全批 ≤8 张 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");
    private static final int BUG_IMAGE_LIMIT = 2;
    private static final int BATCH_IMAGE_LIMIT = 8;

    private static final Set<String> BUG_TYPES = Set.of(
            Constants.BugType.CODE_ERROR, Constants.BugType.UI_IMPROVEMENT, Constants.BugType.DESIGN_DEFECT,
            Constants.BugType.CONFIGURATION, Constants.BugType.INSTALLATION, Constants.BugType.SECURITY,
            Constants.BugType.PERFORMANCE, Constants.BugType.STANDARD_SPEC, Constants.BugType.OTHER);
    private static final Set<String> SEVERITIES = Set.of(
            Constants.BugSeverity.FATAL, Constants.BugSeverity.SERIOUS,
            Constants.BugSeverity.GENERAL, Constants.BugSeverity.MINOR);
    private static final Set<String> PRIORITIES = Set.of(
            Constants.BugPriority.HIGH, Constants.BugPriority.MEDIUM, Constants.BugPriority.LOW);

    /** 批量分类默认提示词（详设 3.6 逐缺陷一条产物） */
    private static final String BATCH_PROMPT = """
            你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。
            对下列每条缺陷给出分类建议：
            1. bugType 只允许 code_error / ui_improvement / design_defect / configuration / installation / security / performance / standard_spec / other；
            2. severity 只允许 fatal / serious / general / minor；priority 只允许 high / medium / low；
            3. moduleId 仅当缺陷内容明确属于某模块时给出模块 id，否则 value 为 null；
            4. keywords 给 1~5 个检索关键词；
            5. 每条缺陷恰好输出一个 item，bugId 必须取自清单原值，不得臆造或遗漏；
            6. 缺陷条目附「截图」标注时，结合对应序号的随附图片辅助判断，无标注的仅按文本判断。
            输出结构（JSON 对象，items 即结果清单）：
            {"items":[{"bugId":"…","suggestions":{"bugType":{"value":"…","reason":"…"},"severity":{"value":"…","reason":"…"},"priority":{"value":"…","reason":"…"},"moduleId":{"value":null,"reason":"…"},"keywords":{"value":["…"],"reason":"…"}}}]}

            缺陷清单：
            {{bugContext}}
            可用模块（id|名称）：
            {{moduleOptions}}
            """;

    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private BugMapper bugMapper;
    @Resource
    private BugAttachmentMapper bugAttachmentMapper;
    @Resource
    private BugAttachmentService bugAttachmentService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return BATCH_PROMPT;
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        // 批量模式（详设 3.6）：bugIds 显式圈定或 filter 按状态筛选，二者缺一即输入非法
        List<UUID> bugIds = parseBugIds(input);
        if (bugIds.isEmpty() && (input == null || input.get("filter") == null)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        if (bugIds.size() > BATCH_LIMIT) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /**
     * 截图入模的能力派生（详设 4.2 / 方案 2.3.2）：按项目解析缺陷集（依赖项目范围故走带上下文
     * 重载），存在图像类附件才需 vision，仅查附件元数据、不读字节。
     */
    @Override
    public String requiredCapability(Map<String, Object> input, TaskSubmitContext context) {
        if (context == null || context.projectId() == null) {
            return null;
        }
        List<Bug> bugs;
        try {
            bugs = resolveBugs(input, context.projectId());
        } catch (ServiceException e) {
            // 执行期复验不阻断（非法输入已由提交期校验拦截）：解析失败按无视觉诉求处理
            return null;
        }
        if (bugs.isEmpty()) {
            return null;
        }
        List<UUID> bugIds = bugs.stream().map(Bug::getId).toList();
        return imageAttachmentsByBug(bugIds).isEmpty() ? null : AiSettingsReader.CAPABILITY_VISION;
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        return executeBatch(context);
    }

    /**
     * 存量批量分类（详设 3.6）：bugIds / filter 解析为缺陷集合（上限 500，空集 1000018283），
     * 按缺陷分片多次调用模型，进度 = 已处理 / 总数（详设 4.3）；产物逐缺陷一条，confirmStatus = pending。
     */
    private TaskResult executeBatch(TaskExecutionContext context) {
        context.report(5, "解析缺陷集合");
        List<Bug> bugs = resolveBugs(context.getInput(), context.getProjectId());
        if (bugs.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_INPUT_EMPTY);
        }
        List<ProjectModule> modules = projectModuleMapper.listByProjectId(context.getProjectId());

        // 截图收集（详设 3.6）：每缺陷 ≤2 张、全批 ≤8 张，跨分片共享配额按缺陷序截断
        Map<UUID, List<BugAttachment>> imagesByBug = imageAttachmentsByBug(
                bugs.stream().map(Bug::getId).toList());
        int remainingImages = BATCH_IMAGE_LIMIT;

        List<Map<String, Object>> artifacts = new ArrayList<>();
        int tokensIn = 0;
        int tokensOut = 0;
        int total = bugs.size();
        for (int start = 0; start < total; start += BATCH_SHARD) {
            List<Bug> shard = bugs.subList(start, Math.min(start + BATCH_SHARD, total));
            context.report(5 + 80 * (start + shard.size()) / total, "分类进度 " + (start + shard.size()) + "/" + total);
            List<AiChatMedia> images = new ArrayList<>();
            Map<UUID, String> imageNotes = new LinkedHashMap<>();
            for (Bug bug : shard) {
                if (remainingImages <= 0) {
                    break;
                }
                List<BugAttachment> candidates = imagesByBug.getOrDefault(bug.getId(), List.of());
                int take = Math.min(BUG_IMAGE_LIMIT, Math.min(candidates.size(), remainingImages));
                int first = images.size() + 1;
                int added = 0;
                for (BugAttachment attachment : candidates.subList(0, take)) {
                    byte[] bytes = readAttachmentQuietly(attachment);
                    if (bytes == null) {
                        continue;
                    }
                    images.add(new AiChatMedia(
                            imageMimeType(attachment.getFileName(), attachment.getContentType()), bytes));
                    added++;
                }
                if (added > 0) {
                    remainingImages -= added;
                    imageNotes.put(bug.getId(), imageNote(first, added));
                }
            }
            Map<String, String> variables = new HashMap<>();
            variables.put("bugContext", batchBugContext(shard, imageNotes));
            variables.put("moduleOptions", moduleOptions(modules));
            String userPrompt = context.prompt(BATCH_PROMPT, variables);
            AiChatReply reply = images.isEmpty()
                    ? context.chat(SYSTEM_PROMPT, userPrompt)
                    : context.chat(SYSTEM_PROMPT, userPrompt, images);
            tokensIn += reply.tokensIn();
            tokensOut += reply.tokensOut();
            artifacts.addAll(sanitizeBatch(reply, shard, modules));
        }
        context.report(95, "汇总分类建议");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", artifacts);
        return new TaskResult(result, tokensIn, tokensOut);
    }

    /** 缺陷集合解析：bugIds 优先，其次 filter.statuses（未分类 / 分类存疑的状态条件）；一律限定当前项目 */
    private List<Bug> resolveBugs(Map<String, Object> input, UUID projectId) {
        List<UUID> bugIds = parseBugIds(input);
        List<Bug> bugs;
        if (!bugIds.isEmpty()) {
            bugs = bugMapper.listByIds(bugIds).stream()
                    .filter(bug -> projectId.equals(bug.getProjectId()))
                    .toList();
        } else {
            List<String> statuses = stringList(asMap(input.get("filter")).get("statuses"), BATCH_LIMIT);
            if (statuses.isEmpty()) {
                return List.of();
            }
            bugs = bugMapper.listByProjectIdAndStatuses(projectId, statuses, BATCH_LIMIT);
        }
        // 保持提交顺序（同集合重复 id 去重）；已删除 / 越项目的 id 静默跳过
        Map<UUID, Bug> byId = bugs.stream()
                .collect(Collectors.toMap(Bug::getId, bug -> bug, (left, right) -> left));
        LinkedHashSet<UUID> ordered = new LinkedHashSet<>(bugIds.isEmpty()
                ? bugs.stream().map(Bug::getId).toList()
                : bugIds);
        return ordered.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private static List<UUID> parseBugIds(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("bugIds");
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        List<UUID> ids = new ArrayList<>(list.size());
        for (Object element : list) {
            try {
                ids.add(UUID.fromString(String.valueOf(element).trim()));
            } catch (IllegalArgumentException e) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
            }
        }
        return ids;
    }

    private static String batchBugContext(List<Bug> shard, Map<UUID, String> imageNotes) {
        return shard.stream()
                .map(bug -> "id=" + bug.getId()
                        + "\n标题：" + nvl(bug.getTitle())
                        + "\n重现步骤：\n" + nvl(bug.getReproSteps())
                        + (imageNotes.containsKey(bug.getId()) ? "\n" + imageNotes.get(bug.getId()) : ""))
                .collect(Collectors.joining("\n---\n"));
    }

    /** 图像类附件按缺陷分组（上传时间倒序，仅元数据）：能力派生与批量截图收集共用 */
    private Map<UUID, List<BugAttachment>> imageAttachmentsByBug(List<UUID> bugIds) {
        if (bugIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<BugAttachment>> grouped = new LinkedHashMap<>();
        for (BugAttachment attachment : bugAttachmentMapper.listByBugIds(bugIds)) {
            if (!IMAGE_EXTENSIONS.contains(extension(attachment.getFileName()))) {
                continue;
            }
            grouped.computeIfAbsent(attachment.getBugId(), key -> new ArrayList<>()).add(attachment);
        }
        return grouped;
    }

    /** 单张截图读取失败不阻塞批量（附件存储异常非输入错误）：该图退化为文本、其余照常 */
    private byte[] readAttachmentQuietly(BugAttachment attachment) {
        try {
            return bugAttachmentService.readBytes(attachment);
        } catch (ServiceException e) {
            return null;
        }
    }

    /** 随附图片序号标注：与提示词缺陷块一一对应，防图文错配（详设 3.6） */
    private static String imageNote(int first, int count) {
        String range = count == 1 ? "第 " + first + " 张" : "第 " + first + "～" + (first + count - 1) + " 张";
        return "截图 " + count + " 张（随附图片" + range + "）";
    }

    /** 扩展名（小写）判图像白名单（缺陷附件） */
    private static String extension(String fileName) {
        String ext = fileName == null ? null : StringUtils.getFilenameExtension(fileName);
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }

    /** 直传 MIME：优先存储归一化类型，非 image/* 时按扩展名回落（白名单内必命中） */
    private static String imageMimeType(String fileName, String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return contentType;
        }
        return switch (extension(fileName)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/png";
        };
    }

    /** 批量产物清洗：模型按 bugId 对号入座，越界或重复的条目剔除，未响应的缺陷不产产物 */
    private List<Map<String, Object>> sanitizeBatch(AiChatReply reply, List<Bug> shard,
            List<ProjectModule> modules) {
        Map<String, Object> parsed = asMap(RequirementSuggestionParser.parseJsonObject(reply.content()));
        Map<UUID, Bug> scope = shard.stream()
                .collect(Collectors.toMap(Bug::getId, bug -> bug, (left, right) -> left));
        Set<UUID> emitted = new LinkedHashSet<>();

        List<Map<String, Object>> artifacts = new ArrayList<>();
        if (parsed.get("items") instanceof List<?> raw) {
            for (Object element : raw) {
                if (!(element instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> item = asMap(element);
                UUID bugId = null;
                try {
                    bugId = UUID.fromString(String.valueOf(item.get("bugId")).trim());
                } catch (RuntimeException e) {
                    continue;
                }
                Bug bug = scope.get(bugId);
                if (bug == null || !emitted.add(bugId)) {
                    continue;
                }
                Map<String, Object> content = new LinkedHashMap<>();
                content.put("bugId", bugId.toString());
                content.put("suggestions", sanitizeSuggestions(asMap(item.get("suggestions")), modules));

                Map<String, Object> artifact = new LinkedHashMap<>();
                artifact.put("key", "bug-" + bugId);
                artifact.put("kind", Constants.AiArtifactKind.BUG_CLASSIFY);
                artifact.put("title", nvl(bug.getTitle()));
                artifact.put("content", content);
                // 批量产物待人工逐项确认（详设 3.6）
                artifact.put("confirmStatus", "pending");
                artifacts.add(artifact);
            }
        }
        return artifacts;
    }

    // ---------- 产物清洗 ----------

    /** 建议值仅保留合法枚举与本项目模块，非法项直接剔除（清洗优先于报错，模型可纠错） */
    private Map<String, Object> sanitizeSuggestions(Map<String, Object> raw, List<ProjectModule> modules) {
        Map<String, Object> suggestions = new LinkedHashMap<>();
        pickEnum(raw, "bugType", BUG_TYPES, suggestions);
        pickEnum(raw, "severity", SEVERITIES, suggestions);
        pickEnum(raw, "priority", PRIORITIES, suggestions);

        Map<String, Object> moduleSuggestion = asMap(raw.get("moduleId"));
        Set<UUID> moduleIds = modules.stream().map(ProjectModule::getId).collect(Collectors.toSet());
        UUID moduleId = null;
        try {
            moduleId = UUID.fromString(String.valueOf(moduleSuggestion.get("value")).trim());
        } catch (RuntimeException e) {
            // 模型给出的模块 id 不可解析或为空 → 视为未指定
        }
        if (moduleId != null && moduleIds.contains(moduleId)) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("value", moduleId.toString());
            normalized.put("reason", nvl(asString(moduleSuggestion.get("reason"))));
            suggestions.put("moduleId", normalized);
        }

        Map<String, Object> keywordSuggestion = asMap(raw.get("keywords"));
        List<String> keywords = stringList(keywordSuggestion.get("value"), KEYWORD_LIMIT);
        if (!keywords.isEmpty()) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("value", keywords);
            normalized.put("reason", nvl(asString(keywordSuggestion.get("reason"))));
            suggestions.put("keywords", normalized);
        }
        return suggestions;
    }

    private static void pickEnum(Map<String, Object> raw, String field, Set<String> allowed,
            Map<String, Object> suggestions) {
        Map<String, Object> suggestion = asMap(raw.get(field));
        String value = trimToNull(asString(suggestion.get("value")));
        if (value == null || !allowed.contains(value)) {
            return;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("value", value);
        normalized.put("reason", nvl(asString(suggestion.get("reason"))));
        suggestions.put(field, normalized);
    }

    // ---------- 上下文装配 ----------

    private static String moduleOptions(List<ProjectModule> modules) {
        return modules.stream()
                .map(module -> module.getId() + "|" + nvl(module.getName()))
                .collect(Collectors.joining("\n"));
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
