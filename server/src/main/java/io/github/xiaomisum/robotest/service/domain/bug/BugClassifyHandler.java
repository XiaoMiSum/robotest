package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
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
 * 缺陷分类建议处理器（缺陷分析详设 3.5 草稿模式）：新建表单草稿 → 分类建议与指派候选；
 * 建议仅作表单标记，不落库（confirmStatus = not_applicable），用户值优先（详设 3.5）。
 * 批量模式（详设 3.6）随确认承接一并实现。
 */
@Component
public class BugClassifyHandler implements TaskHandler {

    public static final String TYPE = "bug_classify";

    private static final String SYSTEM_PROMPT = "你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。";

    private static final int TITLE_MAX_LENGTH = 300;
    private static final int KEYWORD_LIMIT = 5;
    private static final int ASSIGNEE_LIMIT = 3;
    private static final int SOURCE_REF_LIMIT = 5;
    /** 批量分类上限（详设 3.6）与单次模型调用的缺陷分片大小 */
    private static final int BATCH_LIMIT = 500;
    private static final int BATCH_SHARD = 20;
    private static final String PERMISSION = "bug:view";

    private static final Set<String> BUG_TYPES = Set.of(
            Constants.BugType.CODE_ERROR, Constants.BugType.UI_IMPROVEMENT, Constants.BugType.DESIGN_DEFECT,
            Constants.BugType.CONFIGURATION, Constants.BugType.INSTALLATION, Constants.BugType.SECURITY,
            Constants.BugType.PERFORMANCE, Constants.BugType.STANDARD_SPEC, Constants.BugType.OTHER);
    private static final Set<String> SEVERITIES = Set.of(
            Constants.BugSeverity.FATAL, Constants.BugSeverity.SERIOUS,
            Constants.BugSeverity.GENERAL, Constants.BugSeverity.MINOR);
    private static final Set<String> PRIORITIES = Set.of(
            Constants.BugPriority.HIGH, Constants.BugPriority.MEDIUM, Constants.BugPriority.LOW);

    /** 批量模式默认提示词（详设 3.6 逐缺陷一条产物，结构与草稿模式不同，共用 bug_classify 场景） */
    private static final String BATCH_PROMPT = """
            你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。
            对下列每条缺陷给出分类建议：
            1. bugType 只允许 code_error / ui_improvement / design_defect / configuration / installation / security / performance / standard_spec / other；
            2. severity 只允许 fatal / serious / general / minor；priority 只允许 high / medium / low；
            3. moduleId 仅当缺陷内容明确属于某模块时给出模块 id，否则 value 为 null；
            4. keywords 给 1~5 个检索关键词；
            5. 每条缺陷恰好输出一个 item，bugId 必须取自清单原值，不得臆造或遗漏。
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
    private VectorSearchService vectorSearchService;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                根据缺陷草稿给出分类建议：
                1. bugType 只允许 code_error / ui_improvement / design_defect / configuration / installation / security / performance / standard_spec / other；
                2. severity 只允许 fatal / serious / general / minor；priority 只允许 high / medium / low；
                3. moduleId 仅当草稿内容明确属于某模块时给出模块 id，否则 value 为 null；
                4. keywords 给 1~5 个检索关键词；
                5. assigneeCandidates 仅从成员清单中挑选 0~3 名最合适的处理人并给出理由，不得臆造清单外的人；无合适人选时返回空数组。
                输出结构（JSON 对象）：
                {"suggestions":{"bugType":{"value":"…","reason":"…"},"severity":{"value":"…","reason":"…"},"priority":{"value":"…","reason":"…"},"moduleId":{"value":null,"reason":"…"},"keywords":{"value":["…"],"reason":"…"}},"assigneeCandidates":[{"userId":"…","reason":"…"}]}

                缺陷草稿：
                {{bugContext}}
                可用模块（id|名称）：
                {{moduleOptions}}
                候选成员（id|姓名）：
                {{memberOptions}}
                """;
    }

    /** 表单入口在缺陷侧已校验 bug:view；直提任务资源时在此补齐（详设 4.2） */
    @Override
    public void checkPermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains(PERMISSION)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        Map<String, Object> draft = asMap(input == null ? null : input.get("draft"));
        String title = trimToNull(asString(draft.get("title")));
        if (title != null) {
            if (title.length() > TITLE_MAX_LENGTH) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
            }
            return;
        }
        // 批量模式（详设 3.6）：bugIds 显式圈定或 filter 按状态筛选，二者缺一即输入非法
        List<UUID> bugIds = parseBugIds(input);
        if (bugIds.isEmpty() && (input == null || input.get("filter") == null)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        if (bugIds.size() > BATCH_LIMIT) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        Map<String, Object> input = context.getInput();
        Map<String, Object> draft = asMap(input.get("draft"));
        if (trimToNull(asString(draft.get("title"))) != null) {
            return executeDraft(context, draft);
        }
        return executeBatch(context);
    }

    private TaskResult executeDraft(TaskExecutionContext context, Map<String, Object> draft) {
        String title = asString(draft.get("title"));
        String steps = asString(draft.get("steps"));

        context.report(10, "读取模块与成员");
        List<ProjectModule> modules = projectModuleMapper.listByProjectId(context.getProjectId());
        Map<UUID, String> members = memberOptions(context.getWorkspaceId());

        context.report(30, "检索相似缺陷");
        List<Bug> similar = similarBugs(title, steps, context.getProjectId(), context.getUserId());

        context.report(50, "模型生成分类建议");
        Map<String, String> variables = new HashMap<>();
        variables.put("bugContext", "标题：" + nvl(title) + "\n重现步骤：\n" + nvl(steps));
        variables.put("moduleOptions", moduleOptions(modules));
        variables.put("memberOptions", memberOptionsText(members));
        AiChatReply reply = context.chat(SYSTEM_PROMPT, context.prompt(defaultPrompt(), variables));

        context.report(85, "解析分类建议");
        Map<String, Object> artifact = sanitize(reply, modules, members, similar);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", List.of(artifact));
        return new TaskResult(result, reply.tokensIn(), reply.tokensOut());
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

        List<Map<String, Object>> artifacts = new ArrayList<>();
        int tokensIn = 0;
        int tokensOut = 0;
        int total = bugs.size();
        for (int start = 0; start < total; start += BATCH_SHARD) {
            List<Bug> shard = bugs.subList(start, Math.min(start + BATCH_SHARD, total));
            context.report(5 + 80 * (start + shard.size()) / total, "分类进度 " + (start + shard.size()) + "/" + total);
            Map<String, String> variables = new HashMap<>();
            variables.put("bugContext", batchBugContext(shard));
            variables.put("moduleOptions", moduleOptions(modules));
            variables.put("memberOptions", "");
            AiChatReply reply = context.chat(SYSTEM_PROMPT, context.prompt(BATCH_PROMPT, variables));
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

    private static String batchBugContext(List<Bug> shard) {
        return shard.stream()
                .map(bug -> "id=" + bug.getId()
                        + "\n标题：" + nvl(bug.getTitle())
                        + "\n重现步骤：\n" + nvl(bug.getReproSteps()))
                .collect(Collectors.joining("\n---\n"));
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

    private Map<String, Object> sanitize(AiChatReply reply, List<ProjectModule> modules,
            Map<UUID, String> members, List<Bug> similar) {
        Map<String, Object> parsed = asMap(RequirementSuggestionParser.parseJsonObject(reply.content()));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("suggestions", sanitizeSuggestions(asMap(parsed.get("suggestions")), modules));
        content.put("assigneeCandidates", sanitizeCandidates(asMapList(parsed.get("assigneeCandidates")), members));
        content.put("sourceRefs", sourceRefs(similar));

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("key", "draft");
        artifact.put("kind", Constants.AiArtifactKind.BUG_CLASSIFY);
        artifact.put("title", "新建缺陷建议");
        artifact.put("content", content);
        // 表单场景产物不落库、无确认动作（详设 3.5）
        artifact.put("confirmStatus", "not_applicable");
        return artifact;
    }

    private static List<Map<String, Object>> asMapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(element -> element instanceof Map<?, ?>)
                .map(element -> asMap(element))
                .toList();
    }

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

    /** 指派候选服务端过滤：仅保留当前工作空间成员，候选为空不报错（详设 3.5） */
    private List<Map<String, Object>> sanitizeCandidates(List<Map<String, Object>> raw, Map<UUID, String> members) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (Map<String, Object> item : raw) {
            if (candidates.size() >= ASSIGNEE_LIMIT) {
                break;
            }
            UUID userId = null;
            try {
                userId = UUID.fromString(String.valueOf(item.get("userId")).trim());
            } catch (RuntimeException e) {
                continue;
            }
            String name = members.get(userId);
            if (name == null || !seen.add(userId)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("userId", userId.toString());
            normalized.put("name", name);
            normalized.put("reason", nvl(asString(item.get("reason"))));
            normalized.put("memberValid", true);
            candidates.add(normalized);
        }
        return candidates;
    }

    private List<Map<String, Object>> sourceRefs(List<Bug> similar) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Bug bug : similar) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", "bug");
            ref.put("id", bug.getId().toString());
            ref.put("title", nvl(bug.getTitle()));
            ref.put("quote", "");
            refs.add(ref);
        }
        return refs;
    }

    // ---------- 上下文装配 ----------

    /**
     * 相似缺陷检索：向量能力未就绪时静默降级为空引用（建议场景尽力而为，不阻塞新建表单）；
     * 与录入检测入口（详设 3.8 硬失败置灰）口径不同。
     */
    private List<Bug> similarBugs(String title, String steps, UUID projectId, UUID userId) {
        String query = StringUtils.hasText(steps) ? title + "\n" + steps : title;
        List<AiVectorSearchHitRespDTO> hits;
        try {
            hits = vectorSearchService.search(query, List.of(projectId), VectorIndexService.TYPE_BUG,
                    SOURCE_REF_LIMIT, userId);
        } catch (ServiceException e) {
            return List.of();
        }
        List<UUID> ids = hits.stream().map(AiVectorSearchHitRespDTO::getEntityId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return bugMapper.listByIds(ids).stream()
                .filter(bug -> projectId.equals(bug.getProjectId()))
                .limit(SOURCE_REF_LIMIT)
                .toList();
    }

    private Map<UUID, String> memberOptions(UUID workspaceId) {
        // 指派候选来源 = 当前工作空间成员；姓名以用户表为准
        Map<UUID, String> names = new LinkedHashMap<>();
        for (WorkspaceUser row : workspaceUserMapper.listByWorkspaceId(workspaceId)) {
            names.put(row.getUserId(), null);
        }
        if (names.isEmpty()) {
            return names;
        }
        for (SysUser user : sysUserMapper.listByIds(names.keySet())) {
            names.put(user.getId(), user.getName() == null ? user.getUsername() : user.getName());
        }
        names.values().removeIf(Objects::isNull);
        return names;
    }

    private static String moduleOptions(List<ProjectModule> modules) {
        return modules.stream()
                .map(module -> module.getId() + "|" + nvl(module.getName()))
                .collect(Collectors.joining("\n"));
    }

    private static String memberOptionsText(Map<UUID, String> members) {
        return members.entrySet().stream()
                .map(entry -> entry.getKey() + "|" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
