package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import io.github.xiaomisum.robotest.service.domain.requirement.RequirementSuggestionParser;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 新建缺陷 AI 建议任务适配器（缺陷分析详设 3.4）：通用 AI 任务资源产出单条只读建议提案，
 * 重复缺陷检测在 prepare 阶段同步完成并写入提案 content；前端采纳仅写入表单，不调用提案确认落库。
 */
@Component
public class BugDraftSuggestTypeAdapter extends AbstractOrdinaryTaskTypeAdapter {

    private static final Logger log = LoggerFactory.getLogger(BugDraftSuggestTypeAdapter.class);

    public static final String TYPE = Constants.AiTaskType.BUG_DRAFT_SUGGEST;
    private static final String PROPOSAL_KEY = "bug-draft-1";
    private static final int TITLE_MAX_LENGTH = 300;
    private static final int CASE_DOC_TOPK = 8;
    private static final int CASE_OPTION_LIMIT = 30;
    private static final int KEYWORD_LIMIT = 5;
    private static final int DUPLICATE_LIMIT = 5;
    private static final int BASIS_MAX_LENGTH = 200;
    private static final int RATE_SCALE = 10_000;

    private static final Set<String> BUG_TYPES = Set.of(
            Constants.BugType.CODE_ERROR, Constants.BugType.UI_IMPROVEMENT, Constants.BugType.DESIGN_DEFECT,
            Constants.BugType.CONFIGURATION, Constants.BugType.INSTALLATION, Constants.BugType.SECURITY,
            Constants.BugType.PERFORMANCE, Constants.BugType.STANDARD_SPEC, Constants.BugType.OTHER);
    private static final Set<String> SEVERITIES = Set.of(
            Constants.BugSeverity.FATAL, Constants.BugSeverity.SERIOUS,
            Constants.BugSeverity.GENERAL, Constants.BugSeverity.MINOR);
    private static final Set<String> PRIORITIES = Set.of(
            Constants.BugPriority.HIGH, Constants.BugPriority.MEDIUM, Constants.BugPriority.LOW);

    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private BugMapper bugMapper;
    @Resource
    private VectorSearchService vectorSearchService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    protected String defaultPrompt() {
        return """
                你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                根据缺陷草稿实时给出新建缺陷建议：
                1. title 给出更规范、信息完整的优化后标题（保留原意、现象明确，不超过 60 字）；
                2. reproSteps 给出结构化重现步骤 Markdown 文本，按「##### 执行步骤」「##### 实际结果」「##### 期望结果」三段组织：执行步骤为有序列表，实际结果与期望结果各一段简述；
                3. bugType 只允许 code_error / ui_improvement / design_defect / configuration / installation / security / performance / standard_spec / other；
                4. severity 只允许 fatal / serious / general / minor；priority 只允许 high / medium / low；
                5. moduleId 仅当草稿内容明确属于某模块时给出模块 id，否则 value 为 null；
                6. keywords 给 1~5 个检索关键词；
                7. relatedCase 仅当候选用例清单中存在与缺陷明显对应的用例时给出该用例 id（caseTitle 必须取候选用例清单中的标题原值），否则 value 与 caseTitle 均为 null；不得臆造候选用例之外的 id；
                8. 每个建议字段都给出简短理由 reason。
                输出结构（JSON 对象）：
                {"title":{"value":"…","reason":"…"},"reproSteps":{"value":"…","reason":"…"},"suggestions":{"bugType":{"value":"…","reason":"…"},"severity":{"value":"…","reason":"…"},"priority":{"value":"…","reason":"…"},"moduleId":{"value":null,"reason":"…"},"keywords":{"value":["…"],"reason":"…"},"relatedCase":{"value":null,"caseTitle":null,"reason":"…"}}}

                缺陷草稿：
                {{bugContext}}
                可用模块（id|名称）：
                {{moduleOptions}}
                候选用例（id|标题，无则为空）：
                {{caseOptions}}
                """;
    }

    @Override
    protected String defaultProposalKind() {
        return Constants.AiProposalKind.BUG_DRAFT_SUGGESTION;
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        validateTitle(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        String title = validateTitle(input);
        UUID projectId = requireProjectId(context);
        UUID moduleId = parseUuid(asString(input == null ? null : input.get("moduleId")));
        if (moduleId != null) {
            List<ProjectModule> modules = projectModuleMapper.listByProjectId(projectId);
            boolean exists = modules.stream().anyMatch(module -> moduleId.equals(module.getId()));
            if (!exists) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
            }
        }
        // 归一化标题，避免后续快照/上下文使用未 trim 值
        input.put("title", title);
    }

    @Override
    public String sourceTitle(Map<String, Object> input, TaskSubmitContext context) {
        return asString(input == null ? null : input.get("title"));
    }

    @Override
    public AgentExecutionPreparation prepare(PreparationContext context) {
        Map<String, Object> input = context.input() == null ? Map.of() : context.input();
        UUID projectId = context.projectId();
        UUID userId = context.userId();
        String title = trimToNull(asString(input.get("title")));
        String steps = trimToNull(asString(input.get("steps")));
        UUID moduleId = parseUuid(asString(input.get("moduleId")));

        List<ProjectModule> modules = projectId == null ? List.of() : projectModuleMapper.listByProjectId(projectId);
        Map<UUID, String> caseOptions = caseOptions(title, projectId, userId);
        List<Map<String, Object>> duplicates = findDuplicates(title, steps, projectId, userId);

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("bugContext", bugContext(title, steps, moduleId, modules));
        variables.put("moduleOptions", moduleOptions(modules));
        variables.put("caseOptions", caseOptionsText(caseOptions));

        String snapshot = String.join("\n", variables.values());
        return new AgentExecutionPreparation(variables, snapshot, List.of(),
                output -> List.of(buildProposal(output, modules, caseOptions, duplicates, title)),
                output -> null);
    }

    @Override
    public List<String> toolCodes() {
        return List.of();
    }

    @Override
    public List<ProposalDraft> buildProposals(String finalOutput, Map<String, Object> input) {
        return List.of(buildProposal(finalOutput, List.of(), Map.of(), List.of(),
                trimToNull(asString(input == null ? null : input.get("title")))));
    }

    private ProposalDraft buildProposal(String output, List<ProjectModule> modules,
            Map<UUID, String> caseOptions, List<Map<String, Object>> duplicates, String title) {
        Map<String, Object> parsed = asMap(RequirementSuggestionParser.parseJsonObject(output));
        Map<String, Object> content = new LinkedHashMap<>();
        putIfPresent(content, "title", fieldSuggestion(parsed.get("title")));
        putIfPresent(content, "reproSteps", fieldSuggestion(parsed.get("reproSteps")));
        content.put("suggestions", sanitizeSuggestions(asMap(parsed.get("suggestions")), modules, caseOptions));
        content.put("duplicates", duplicates == null ? List.of() : duplicates);
        String proposalTitle = trimToNull(title);
        if (proposalTitle == null) {
            proposalTitle = trimToNull(asString(asMap(content.get("title")).get("value")));
        }
        return new ProposalDraft(PROPOSAL_KEY, Constants.AiProposalKind.BUG_DRAFT_SUGGESTION, null,
                proposalTitle == null ? "缺陷建议" : proposalTitle, content,
                Constants.AiProposalConfirmStatus.NOT_APPLICABLE);
    }

    private static String validateTitle(Map<String, Object> input) {
        String title = trimToNull(asString(input == null ? null : input.get("title")));
        if (title == null || title.length() > TITLE_MAX_LENGTH) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        return title;
    }

    private static UUID requireProjectId(TaskSubmitContext context) {
        UUID projectId = context == null ? null : context.projectId();
        if (projectId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_MISSING);
        }
        return projectId;
    }

    private static String bugContext(String title, String steps, UUID moduleId, List<ProjectModule> modules) {
        StringBuilder context = new StringBuilder("标题：").append(nvl(title))
                .append("\n重现步骤：\n").append(nvl(steps));
        if (moduleId != null) {
            modules.stream()
                    .filter(module -> moduleId.equals(module.getId()))
                    .findFirst()
                    .ifPresent(module -> context.append("\n已选模块：").append(nvl(module.getName())));
        }
        return context.toString();
    }

    private Map<UUID, String> caseOptions(String title, UUID projectId, UUID userId) {
        if (!StringUtils.hasText(title) || projectId == null) {
            return Map.of();
        }
        List<UUID> documentIds;
        try {
            documentIds = vectorSearchService.search(title, List.of(projectId),
                            VectorIndexService.TYPE_TESTCASE, CASE_DOC_TOPK, userId).stream()
                    .map(AiVectorSearchHitRespDTO::getEntityId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.debug("[AI] 缺陷建议用例候选检索降级 projectId={}", projectId, e);
            return Map.of();
        }
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<TestCaseNode>> byDocument = testCaseNodeMapper.listCasesByDocumentIds(documentIds).stream()
                .collect(Collectors.groupingBy(TestCaseNode::getDocumentId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, String> options = new LinkedHashMap<>();
        for (UUID documentId : documentIds) {
            for (TestCaseNode node : byDocument.getOrDefault(documentId, List.of())) {
                if (options.size() >= CASE_OPTION_LIMIT) {
                    return options;
                }
                if (StringUtils.hasText(node.getTitle())) {
                    options.put(node.getId(), node.getTitle());
                }
            }
        }
        return options;
    }

    private List<Map<String, Object>> findDuplicates(String title, String steps, UUID projectId, UUID userId) {
        if (!StringUtils.hasText(title) || projectId == null) {
            return List.of();
        }
        String queryText = StringUtils.hasText(steps) ? title + "\n" + steps : title;
        List<AiVectorSearchHitRespDTO> hits;
        try {
            hits = vectorSearchService.search(queryText, List.of(projectId),
                    VectorIndexService.TYPE_BUG, DUPLICATE_LIMIT, userId);
        } catch (Exception e) {
            log.warn("[AI] 缺陷建议重复检索不可用 projectId={}", projectId, e);
            return List.of();
        }
        List<UUID> hitIds = hits.stream()
                .map(AiVectorSearchHitRespDTO::getEntityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Bug> bugById = hitIds.isEmpty() ? Map.of()
                : bugMapper.listByIds(hitIds).stream()
                        .filter(bug -> projectId.equals(bug.getProjectId()))
                        .collect(Collectors.toMap(Bug::getId, bug -> bug, (left, right) -> left));

        Set<UUID> seen = new LinkedHashSet<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (AiVectorSearchHitRespDTO hit : hits) {
            UUID bugId = hit.getEntityId();
            if (bugId == null || !seen.add(bugId)) {
                continue;
            }
            Bug bug = bugById.get(bugId);
            if (bug == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bugId", bug.getId().toString());
            item.put("title", nvl(bug.getTitle()));
            item.put("status", nvl(bug.getStatus()));
            item.put("similarity", hit.getDistance() == null ? null
                    : Math.round((1 - hit.getDistance()) * RATE_SCALE) / (double) RATE_SCALE);
            item.put("basis", basisOf(hit.getContent()));
            items.add(item);
            if (items.size() >= DUPLICATE_LIMIT) {
                break;
            }
        }
        return items;
    }

    private static Map<String, Object> sanitizeSuggestions(Map<String, Object> raw,
            List<ProjectModule> modules, Map<UUID, String> caseOptions) {
        Map<String, Object> suggestions = new LinkedHashMap<>();
        putIfPresent(suggestions, "bugType", fieldSuggestion(raw.get("bugType"), BUG_TYPES));
        putIfPresent(suggestions, "severity", fieldSuggestion(raw.get("severity"), SEVERITIES));
        putIfPresent(suggestions, "priority", fieldSuggestion(raw.get("priority"), PRIORITIES));

        Map<String, Object> moduleSuggestion = asMap(raw.get("moduleId"));
        Set<UUID> moduleIds = modules.stream().map(ProjectModule::getId).collect(Collectors.toSet());
        UUID moduleId = parseUuid(asString(moduleSuggestion.get("value")));
        if (moduleId != null && moduleIds.contains(moduleId)) {
            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("value", moduleId.toString());
            suggestion.put("reason", nvl(asString(moduleSuggestion.get("reason"))));
            suggestions.put("moduleId", suggestion);
        }

        Map<String, Object> keywordSuggestion = asMap(raw.get("keywords"));
        List<String> keywords = stringList(keywordSuggestion.get("value"), KEYWORD_LIMIT);
        if (!keywords.isEmpty()) {
            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("value", keywords);
            suggestion.put("reason", nvl(asString(keywordSuggestion.get("reason"))));
            suggestions.put("keywords", suggestion);
        }

        Map<String, Object> caseSuggestion = asMap(raw.get("relatedCase"));
        UUID caseId = parseUuid(asString(caseSuggestion.get("value")));
        if (caseId != null && caseOptions.containsKey(caseId)) {
            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("value", caseId.toString());
            suggestion.put("caseTitle", caseOptions.get(caseId));
            suggestion.put("reason", nvl(asString(caseSuggestion.get("reason"))));
            suggestions.put("relatedCase", suggestion);
        }
        return suggestions;
    }

    private static Map<String, Object> fieldSuggestion(Object raw) {
        Map<String, Object> suggestion = asMap(raw);
        String value = trimToNull(asString(suggestion.get("value")));
        if (value == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("reason", nvl(asString(suggestion.get("reason"))));
        return result;
    }

    private static Map<String, Object> fieldSuggestion(Object raw, Set<String> allowed) {
        Map<String, Object> suggestion = asMap(raw);
        String value = trimToNull(asString(suggestion.get("value")));
        if (value == null || !allowed.contains(value)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("reason", nvl(asString(suggestion.get("reason"))));
        return result;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Map<String, Object> value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String moduleOptions(List<ProjectModule> modules) {
        return modules.stream()
                .map(module -> module.getId() + "|" + nvl(module.getName()))
                .collect(Collectors.joining("\n"));
    }

    private static String caseOptionsText(Map<UUID, String> caseOptions) {
        return caseOptions.entrySet().stream()
                .map(entry -> entry.getKey() + "|" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static String basisOf(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String trimmed = content.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= BASIS_MAX_LENGTH ? trimmed : trimmed.substring(0, BASIS_MAX_LENGTH) + "…";
    }

    private static UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
