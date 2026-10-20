package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDraftSuggestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDraftSuggestRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiChatRequest;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptScenes;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import io.github.xiaomisum.robotest.service.domain.requirement.RequirementSuggestionParser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * 新建缺陷实时建议实现（详设 3.5）：同步单跳模型调用，不产生任务、不落库。
 * 上下文装配：项目模块清单 + 固定选项枚举 + 用例候选（向量检索用例文档 top 8 展开用例节点 ≤30，
 * 未就绪静默降级为无用例候选）；不检索相似存量缺陷（相似性判断由录入时重复检测详设 3.8 承担）。
 * 清洗：枚举值非法剔除，模块 / 用例归属校验，relatedCase 至多一条；模型调用失败按总册 4.2 映射。
 */
@Service
public class BugDraftSuggestServiceImpl implements BugDraftSuggestService {

    private static final String SCENE = "bug_draft_suggest";
    private static final String SYSTEM_PROMPT = "你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。";

    private static final int TITLE_MAX_LENGTH = 300;
    /** 用例候选：相似用例文档 top 8 展开为用例节点，总量截断防上下文膨胀（详设 3.5） */
    private static final int CASE_DOC_TOPK = 8;
    private static final int CASE_OPTION_LIMIT = 30;
    private static final int KEYWORD_LIMIT = 5;

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
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private VectorSearchService vectorSearchService;
    @Resource
    private AiSettingsReader settingsReader;
    @Resource
    private AiModelClient modelClient;
    @Resource
    private AiPromptService promptService;

    @Override
    public BugDraftSuggestRespDTO suggest(BugDraftSuggestReqDTO reqDTO, UUID projectId, UUID userId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        String title = reqDTO.getTitle() == null ? "" : reqDTO.getTitle().trim();
        if (title.isEmpty() || title.length() > TITLE_MAX_LENGTH) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_SUGGEST_INPUT_INVALID);
        }
        String steps = trimToNull(reqDTO.getSteps());

        List<ProjectModule> modules = projectModuleMapper.listByProjectId(projectId);
        Map<UUID, String> caseOptions = caseOptions(title, projectId, userId);

        AiModelConfig model = settingsReader.resolveModel(null);
        if (model == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED);
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("bugContext", bugContext(reqDTO, title, steps, modules));
        variables.put("moduleOptions", moduleOptions(modules));
        variables.put("caseOptions", caseOptionsText(caseOptions));
        String userPrompt = promptService.render(SCENE, AiPromptScenes.builtin(SCENE), variables);
        AiChatReply reply = modelClient.chat(
                new AiChatRequest(null, projectId, userId, SCENE, model, SYSTEM_PROMPT, userPrompt));
        return sanitize(reply, modules, caseOptions);
    }

    /** 草稿上下文：标题 + 已填重现步骤 + 已选模块名称（仅本项目模块参与） */
    private static String bugContext(BugDraftSuggestReqDTO reqDTO, String title, String steps,
            List<ProjectModule> modules) {
        StringBuilder context = new StringBuilder("标题：").append(title)
                .append("\n重现步骤：\n").append(nvl(steps));
        UUID moduleId = parseUuid(reqDTO.getModuleId());
        if (moduleId != null) {
            modules.stream()
                    .filter(module -> moduleId.equals(module.getId()))
                    .findFirst()
                    .ifPresent(module -> context.append("\n已选模块：").append(module.getName()));
        }
        return context.toString();
    }

    /**
     * 用例候选用例（详设 3.5）：向量检索项目内相似用例文档（top 8）再按命中序展开用例节点
     * （≤30）；向量能力未就绪静默降级为空（不报错、不产出 relatedCase）。
     */
    private Map<UUID, String> caseOptions(String title, UUID projectId, UUID userId) {
        List<UUID> documentIds;
        try {
            documentIds = vectorSearchService.search(title, List.of(projectId),
                            VectorIndexService.TYPE_TESTCASE, CASE_DOC_TOPK, userId).stream()
                    .map(AiVectorSearchHitRespDTO::getEntityId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (ServiceException e) {
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

    // ---------- 产物清洗 ----------

    private BugDraftSuggestRespDTO sanitize(AiChatReply reply, List<ProjectModule> modules,
            Map<UUID, String> caseOptions) {
        Map<String, Object> parsed = asMap(RequirementSuggestionParser.parseJsonObject(reply.content()));

        BugDraftSuggestRespDTO resp = new BugDraftSuggestRespDTO();
        resp.setTitle(fieldSuggestion(parsed.get("title")));
        resp.setReproSteps(fieldSuggestion(parsed.get("reproSteps")));
        resp.setSuggestions(sanitizeSuggestions(asMap(parsed.get("suggestions")), modules, caseOptions));
        return resp;
    }

    /** 建议值仅保留合法枚举与本项目模块 / 候选用例，非法项直接剔除（清洗优先于报错，模型可纠错） */
    private static BugDraftSuggestRespDTO.Suggestions sanitizeSuggestions(Map<String, Object> raw,
            List<ProjectModule> modules, Map<UUID, String> caseOptions) {
        BugDraftSuggestRespDTO.Suggestions suggestions = new BugDraftSuggestRespDTO.Suggestions();
        suggestions.setBugType(fieldSuggestion(raw.get("bugType"), BUG_TYPES));
        suggestions.setSeverity(fieldSuggestion(raw.get("severity"), SEVERITIES));
        suggestions.setPriority(fieldSuggestion(raw.get("priority"), PRIORITIES));

        Map<String, Object> moduleSuggestion = asMap(raw.get("moduleId"));
        Set<UUID> moduleIds = modules.stream().map(ProjectModule::getId).collect(Collectors.toSet());
        UUID moduleId = parseUuid(asString(moduleSuggestion.get("value")));
        if (moduleId != null && moduleIds.contains(moduleId)) {
            BugDraftSuggestRespDTO.FieldSuggestion suggestion = new BugDraftSuggestRespDTO.FieldSuggestion();
            suggestion.setValue(moduleId.toString());
            suggestion.setReason(nvl(asString(moduleSuggestion.get("reason"))));
            suggestions.setModuleId(suggestion);
        }

        Map<String, Object> keywordSuggestion = asMap(raw.get("keywords"));
        List<String> keywords = stringList(keywordSuggestion.get("value"), KEYWORD_LIMIT);
        if (!keywords.isEmpty()) {
            BugDraftSuggestRespDTO.KeywordsSuggestion suggestion = new BugDraftSuggestRespDTO.KeywordsSuggestion();
            suggestion.setValue(keywords);
            suggestion.setReason(nvl(asString(keywordSuggestion.get("reason"))));
            suggestions.setKeywords(suggestion);
        }

        Map<String, Object> caseSuggestion = asMap(raw.get("relatedCase"));
        UUID caseId = parseUuid(asString(caseSuggestion.get("value")));
        if (caseId != null && caseOptions.containsKey(caseId)) {
            BugDraftSuggestRespDTO.RelatedCaseSuggestion suggestion =
                    new BugDraftSuggestRespDTO.RelatedCaseSuggestion();
            suggestion.setValue(caseId.toString());
            suggestion.setCaseTitle(caseOptions.get(caseId));
            suggestion.setReason(nvl(asString(caseSuggestion.get("reason"))));
            suggestions.setRelatedCase(suggestion);
        }
        return suggestions;
    }

    /** 无取值域约束的字段建议（title / reproSteps）：value 非空才保留 */
    private static BugDraftSuggestRespDTO.FieldSuggestion fieldSuggestion(Object raw) {
        Map<String, Object> suggestion = asMap(raw);
        String value = trimToNull(asString(suggestion.get("value")));
        if (value == null) {
            return null;
        }
        BugDraftSuggestRespDTO.FieldSuggestion result = new BugDraftSuggestRespDTO.FieldSuggestion();
        result.setValue(value);
        result.setReason(nvl(asString(suggestion.get("reason"))));
        return result;
    }

    /** 取值域约束的字段建议：非法或缺失直接剔除 */
    private static BugDraftSuggestRespDTO.FieldSuggestion fieldSuggestion(Object raw, Set<String> allowed) {
        Map<String, Object> suggestion = asMap(raw);
        String value = trimToNull(asString(suggestion.get("value")));
        if (value == null || !allowed.contains(value)) {
            return null;
        }
        BugDraftSuggestRespDTO.FieldSuggestion result = new BugDraftSuggestRespDTO.FieldSuggestion();
        result.setValue(value);
        result.setReason(nvl(asString(suggestion.get("reason"))));
        return result;
    }

    // ---------- 上下文装配 ----------

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
