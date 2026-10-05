package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptScenes;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.vector.AiEmbeddingGate;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.abbreviate;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.contentOf;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.modelFailed;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parsedArtifacts;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.stringList;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 生成链六阶段处理器（生成链详设 3.2 / 3.3）：解析需求 → 生成模块结构 → 生成脑图文档 →
 * 标记用例节点 → 填充用例属性 → 建立追溯边；阶段 2-5 为 4 次模型调用，阶段 1 装配需求快照
 * 与 RAG 上下文，阶段 6 做来源预校验（追溯边在确认采纳时同事务建立，见 GenerationAdoptServiceImpl）。
 * 阶段子场景提示词按 test_design_generation_{modules,documents,nodes,attributes} 独立配置。
 */
@Slf4j
@Component
public class TestDesignGenerationHandler implements TaskHandler {

    public static final String TYPE = "test_design_generation";

    private static final String PHASE_PARSE = "解析需求";
    private static final String PHASE_MODULES = "生成模块结构";
    private static final String PHASE_DOCUMENTS = "生成脑图文档";
    private static final String PHASE_NODES = "标记用例节点";
    private static final String PHASE_ATTRIBUTES = "填充用例属性";
    private static final String PHASE_EDGES = "建立追溯边";

    private static final String SCENE_MODULES = "test_design_generation_modules";
    private static final String SCENE_DOCUMENTS = "test_design_generation_documents";
    private static final String SCENE_NODES = "test_design_generation_nodes";
    private static final String SCENE_ATTRIBUTES = "test_design_generation_attributes";

    private static final String PERMISSION_ENTRY = "requirement:view";
    private static final Set<String> GRANULARITIES = Set.of("concise", "standard", "detailed");
    private static final String GRANULARITY_DEFAULT = "standard";
    private static final Set<String> PLACEMENTS = Set.of("new_top_level", "attach");
    private static final String PLACEMENT_DEFAULT = "new_top_level";
    private static final String PLACEMENT_ATTACH = "attach";
    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");
    private static final String PRIORITY_DEFAULT = "medium";

    private static final int MAX_REQUIREMENTS = 50;
    /** 单需求描述截断：防单条超长需求撑爆模型上下文（why：导入正文可整篇落库） */
    private static final int MAX_DESCRIPTION_CHARS = 4000;
    private static final int MAX_STEP_ITEMS = 10;
    private static final int MAX_TAGS = 5;
    private static final int MAX_TREE_DEPTH = 4;
    private static final int RAG_TOP_K = 8;
    private static final int RAG_FRAGMENT_CHARS = 800;

    private static final String SYSTEM_PROMPT = "你是资深测试设计专家。严格按用户给出的 JSON 输出结构作答。";

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private AiTaskMapper aiTaskMapper;
    @Resource
    private AiEmbeddingGate embeddingGate;
    @Resource
    private VectorSearchService vectorSearchService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        // 阶段提示词按子场景独立配置（AiPromptScenes），type 场景仅作任务与用量归因
        return "生成链按 解析需求 → 模块结构 → 脑图文档 → 用例节点 → 用例属性 → 追溯边 六阶段编排，"
                + "各阶段提示词独立配置（test_design_generation_modules / _documents / _nodes / _attributes）。";
    }

    @Override
    public void checkPermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains(PERMISSION_ENTRY)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NO_PERMISSION);
        }
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        parseRequirementIds(input);
        String placement = parsePlacement(input);
        if (PLACEMENT_ATTACH.equals(placement) && parseTargetModuleId(input) == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_INPUT_INVALID);
        }
        parseGranularity(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        validateInput(input);
        if (context == null || context.projectId() == null) {
            // 生成链发起必经 X-Active-Project（C4）；缺失视为参数非法
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_INPUT_INVALID);
        }
        UUID projectId = context.projectId();
        List<UUID> requirementIds = parseRequirementIds(input);
        Map<UUID, Requirement> byId = requirementMapper.listByIds(requirementIds).stream()
                .collect(Collectors.toMap(Requirement::getId, item -> item, (left, right) -> left));
        List<Map<String, Object>> snapshots = new ArrayList<>(requirementIds.size());
        for (UUID id : requirementIds) {
            Requirement item = byId.get(id);
            if (item == null || !projectId.equals(item.getProjectId())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_REQUIREMENT_NOT_FOUND);
            }
            if (!Constants.RequirementStatus.CONFIRMED.equals(item.getStatus())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_REQUIREMENT_NOT_CONFIRMED);
            }
            snapshots.add(snapshotOf(item));
        }
        UUID targetModuleId = parseTargetModuleId(input);
        if (targetModuleId != null) {
            ProjectModule module = projectModuleMapper.selectById(targetModuleId);
            if (module == null || !projectId.equals(module.getProjectId())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_TARGET_MODULE_INVALID);
            }
        }
        requireNoInProgressTwin(projectId, input);
        embeddingGate.requireReady();
        // 回写解析后的默认值与需求快照（3.6.2）：执行装配与去重比对读同一份输入
        input.put("placement", parsePlacement(input));
        input.put("granularity", parseGranularity(input));
        input.put("requirementSnapshots", snapshots);
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        Map<String, Object> input = context.getInput();
        List<UUID> requirementIds = parseRequirementIds(input);
        String granularity = parseGranularity(input);

        context.report(5, PHASE_PARSE);
        List<Requirement> requirements = loadRequirements(requirementIds);
        String requirementContext = buildRequirementContext(requirements)
                + ragContext(context, requirements, requirementIds);
        Set<String> scope = requirementIds.stream()
                .map(item -> item.toString().toLowerCase()).collect(Collectors.toSet());

        int tokensIn = 0;
        int tokensOut = 0;

        context.report(15, PHASE_MODULES);
        AiChatReply reply = context.chat(SYSTEM_PROMPT,
                renderStage(context, SCENE_MODULES, Map.of("requirementContext", requirementContext)));
        tokensIn += reply.tokensIn();
        tokensOut += reply.tokensOut();
        List<Map<String, Object>> artifacts = sanitizeModules(reply);
        markModuleDuplicates(artifacts, context.getProjectId());

        context.report(30, PHASE_DOCUMENTS);
        reply = context.chat(SYSTEM_PROMPT, renderStage(context, SCENE_DOCUMENTS, Map.of(
                "requirementContext", requirementContext,
                "moduleOptions", optionsOf(artifacts))));
        tokensIn += reply.tokensIn();
        tokensOut += reply.tokensOut();
        List<Map<String, Object>> documents = sanitizeDocuments(reply, keySetOf(artifacts));
        artifacts.addAll(documents);

        context.report(50, PHASE_NODES);
        reply = context.chat(SYSTEM_PROMPT, renderStage(context, SCENE_NODES, Map.of(
                "requirementContext", requirementContext,
                "documentOptions", optionsOf(documents),
                "granularity", granularity)));
        tokensIn += reply.tokensIn();
        tokensOut += reply.tokensOut();
        List<Map<String, Object>> nodes = sanitizeNodes(reply, keySetOf(documents), scope);
        artifacts.addAll(nodes);

        context.report(75, PHASE_ATTRIBUTES);
        List<Map<String, Object>> cases = nodes.stream()
                .filter(item -> Boolean.TRUE.equals(contentOf(item).get("isTestCase")))
                .toList();
        if (!cases.isEmpty()) {
            reply = context.chat(SYSTEM_PROMPT, renderStage(context, SCENE_ATTRIBUTES, Map.of(
                    "requirementContext", requirementContext,
                    "documentOptions", optionsOf(documents),
                    "caseNodeOptions", caseOptionsOf(cases),
                    "granularity", granularity)));
            tokensIn += reply.tokensIn();
            tokensOut += reply.tokensOut();
            fillCaseAttributes(cases, reply);
        }

        context.report(95, PHASE_EDGES);
        // 来源缺失的产物不得进入待确认态（3.4）；节点在组装期已按来源过滤，此处统一兜底
        artifacts.removeIf(item -> !retainValidSourceRefs(item, scope));
        if (artifacts.isEmpty()) {
            throw modelFailed("全部产物缺少有效来源引用");
        }
        markCaseDuplicates(artifacts, context.getProjectId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", artifacts);
        return new TaskResult(result, tokensIn, tokensOut);
    }

    // ---------- 输入解析与校验 ----------

    private static List<UUID> parseRequirementIds(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("requirementIds");
        if (!(raw instanceof List<?> list) || list.isEmpty() || list.size() > MAX_REQUIREMENTS) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_INPUT_INVALID);
        }
        List<UUID> ids = new ArrayList<>(list.size());
        for (Object element : list) {
            try {
                ids.add(UUID.fromString(String.valueOf(element).trim()));
            } catch (IllegalArgumentException e) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_INPUT_INVALID);
            }
        }
        return ids;
    }

    private static String parsePlacement(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("placement");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return PLACEMENT_DEFAULT;
        }
        String placement = String.valueOf(raw).trim();
        if (!PLACEMENTS.contains(placement)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_INPUT_INVALID);
        }
        return placement;
    }

    private static String parseGranularity(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("granularity");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return GRANULARITY_DEFAULT;
        }
        String granularity = String.valueOf(raw).trim();
        if (!GRANULARITIES.contains(granularity)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_INPUT_INVALID);
        }
        return granularity;
    }

    private static UUID parseTargetModuleId(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("targetModuleId");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_INPUT_INVALID);
        }
    }

    /** 同项目同类型存在同输入的进行中任务时拒绝（1000018209，3.6.2 校验顺序末位） */
    private void requireNoInProgressTwin(UUID projectId, Map<String, Object> input) {
        Map<String, Object> normalized = normalizeSubmission(input);
        List<AiTask> running = aiTaskMapper.listInProgressByType(projectId, TYPE);
        boolean exists = running.stream()
                .anyMatch(item -> normalized.equals(normalizeSubmission(item.getInput())));
        if (exists) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_DUPLICATE_TASK);
        }
    }

    /** 归一化提交要素：仅比对用户输入的四个字段，忽略执行期回写（快照等） */
    private static Map<String, Object> normalizeSubmission(Map<String, Object> input) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("requirementIds", sortedIdStrings(input));
        normalized.put("targetModuleId",
                input == null ? null : asString(input.get("targetModuleId")));
        normalized.put("placement", input == null ? PLACEMENT_DEFAULT : parsePlacement(input));
        normalized.put("granularity", input == null ? GRANULARITY_DEFAULT : parseGranularity(input));
        return normalized;
    }

    private static List<String> sortedIdStrings(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("requirementIds");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(item -> String.valueOf(item).toLowerCase()).sorted().toList();
    }

    private static Map<String, Object> snapshotOf(Requirement item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", String.valueOf(item.getId()));
        snapshot.put("code", item.getCode());
        snapshot.put("title", item.getTitle());
        snapshot.put("systemVersion", item.getSystemVersion());
        snapshot.put("status", item.getStatus());
        snapshot.put("updatedAt", item.getUpdatedAt() == null ? null : String.valueOf(item.getUpdatedAt()));
        return snapshot;
    }

    // ---------- 阶段 1：解析需求 + RAG 装配 ----------

    private List<Requirement> loadRequirements(List<UUID> requirementIds) {
        Map<UUID, Requirement> byId = requirementMapper.listByIds(requirementIds).stream()
                .collect(Collectors.toMap(Requirement::getId, item -> item, (left, right) -> left));
        return requirementIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private static String buildRequirementContext(List<Requirement> requirements) {
        StringBuilder builder = new StringBuilder("需求清单（共 ").append(requirements.size()).append(" 条，")
                .append("每条前缀方括号内为需求 ID，供 sourceRef 引用）：\n");
        for (Requirement item : requirements) {
            builder.append('[').append(item.getId()).append("] ");
            if (item.getCode() != null && !item.getCode().isBlank()) {
                builder.append(item.getCode()).append(' ');
            }
            builder.append(item.getTitle() == null ? "" : item.getTitle()).append('\n');
            if (item.getSystemVersion() != null && !item.getSystemVersion().isBlank()) {
                builder.append("版本：").append(item.getSystemVersion()).append('\n');
            }
            String description = abbreviate(item.getDescription(), MAX_DESCRIPTION_CHARS);
            if (!description.isBlank()) {
                builder.append(description).append('\n');
            }
        }
        return builder.toString();
    }

    /** RAG 装配：检索输入需求的向量分块作上下文增强；向量未就绪 / 重建中降级为无检索执行 */
    private String ragContext(TaskExecutionContext context, List<Requirement> requirements,
            List<UUID> requirementIds) {
        try {
            String query = requirements.stream().map(Requirement::getTitle)
                    .filter(title -> title != null && !title.isBlank())
                    .limit(MAX_REQUIREMENTS).collect(Collectors.joining(" "));
            if (query.isBlank()) {
                return "";
            }
            List<AiVectorSearchHitRespDTO> hits = vectorSearchService.search(query,
                    List.of(context.getProjectId()), Constants.TraceNodeType.REQUIREMENT, RAG_TOP_K,
                    context.getUserId());
            Set<UUID> scope = new HashSet<>(requirementIds);
            List<String> lines = hits.stream()
                    .filter(hit -> hit.getEntityId() != null && scope.contains(hit.getEntityId()))
                    .map(hit -> "- " + abbreviate(String.valueOf(hit.getContent()).replace('\n', ' '),
                            RAG_FRAGMENT_CHARS))
                    .distinct().toList();
            if (lines.isEmpty()) {
                return "";
            }
            return "\n相关需求片段（向量检索命中）：\n" + String.join("\n", lines);
        } catch (ServiceException e) {
            if (e.getCode() == ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED.code()
                    || e.getCode() == ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE.code()) {
                return "";
            }
            throw e;
        }
    }

    // ---------- 阶段 2：模块结构 ----------

    private static List<Map<String, Object>> sanitizeModules(AiChatReply reply) {
        List<Map<String, Object>> parsed = parsedArtifacts(reply);
        List<Map<String, Object>> accepted = new ArrayList<>(parsed.size());
        int index = 0;
        for (Map<String, Object> item : parsed) {
            Map<String, Object> content = asMap(item.get("content"));
            String name = trimToNull(asString(content.get("name")));
            if (name == null) {
                continue;
            }
            index++;
            content.put("name", name);
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("key", "module-" + index);
            artifact.put("kind", Constants.AiArtifactKind.MODULE);
            artifact.put("title", name);
            artifact.put("content", content);
            accepted.add(artifact);
        }
        if (accepted.isEmpty()) {
            throw modelFailed("模型未产出有效模块");
        }
        return accepted;
    }

    // ---------- 阶段 3：脑图文档 ----------

    private static List<Map<String, Object>> sanitizeDocuments(AiChatReply reply, Set<String> moduleKeys) {
        List<Map<String, Object>> parsed = parsedArtifacts(reply);
        List<Map<String, Object>> accepted = new ArrayList<>(parsed.size());
        int index = 0;
        for (Map<String, Object> item : parsed) {
            String parentKey = trimToNull(asString(item.get("parentKey")));
            if (parentKey == null || !moduleKeys.contains(parentKey)) {
                log.debug("丢弃父级模块不存在的文档产物 parentKey={}", parentKey);
                continue;
            }
            Map<String, Object> content = asMap(item.get("content"));
            String name = trimToNull(asString(content.get("name")));
            if (name == null) {
                continue;
            }
            index++;
            content.put("name", name);
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("key", "doc-" + index);
            artifact.put("kind", Constants.AiArtifactKind.MINDMAP_DOCUMENT);
            artifact.put("parentKey", parentKey);
            artifact.put("title", name);
            artifact.put("content", content);
            accepted.add(artifact);
        }
        if (accepted.isEmpty()) {
            throw modelFailed("模型未产出有效脑图文档");
        }
        return accepted;
    }

    // ---------- 阶段 4：用例节点 ----------

    private record NodeDraft(String ref, String documentKey, String rawParentRef, String finalKey,
            boolean testCase, String title, Map<String, Object> sourceRef) {
    }

    private static List<Map<String, Object>> sanitizeNodes(AiChatReply reply, Set<String> documentKeys,
            Set<String> scope) {
        List<Map<String, Object>> parsed = parsedArtifacts(reply);
        List<NodeDraft> drafts = new ArrayList<>(parsed.size());
        Set<String> seenRefs = new HashSet<>();
        int caseIndex = 0;
        int groupIndex = 0;
        for (Map<String, Object> item : parsed) {
            String documentKey = trimToNull(asString(item.get("parentKey")));
            if (documentKey == null || !documentKeys.contains(documentKey)) {
                log.debug("丢弃所属文档不存在的节点产物 parentKey={}", documentKey);
                continue;
            }
            Map<String, Object> content = asMap(item.get("content"));
            String title = trimToNull(asString(content.get("title")));
            if (title == null) {
                continue;
            }
            Map<String, Object> sourceRef = normalizeSourceRef(content.get("sourceRef"), scope);
            if (sourceRef == null) {
                // 来源缺失或越界的节点直接丢弃，不得进入待确认态（3.4）
                continue;
            }
            String ref = trimToNull(asString(item.get("ref")));
            if (ref == null || !seenRefs.add(documentKey + '#' + ref)) {
                continue;
            }
            boolean testCase = Boolean.TRUE.equals(content.get("isTestCase"));
            String finalKey = testCase ? "case-" + (++caseIndex) : "node-" + (++groupIndex);
            drafts.add(new NodeDraft(ref, documentKey, trimToNull(asString(item.get("parentRef"))),
                    finalKey, testCase, title, sourceRef));
        }
        if (drafts.isEmpty()) {
            throw modelFailed("模型未产出有效用例节点");
        }

        Map<String, Map<String, NodeDraft>> draftsByDocument = new HashMap<>();
        Map<String, Map<String, String>> keyByRef = new HashMap<>();
        for (NodeDraft draft : drafts) {
            draftsByDocument.computeIfAbsent(draft.documentKey(), key -> new HashMap<>())
                    .put(draft.ref(), draft);
            keyByRef.computeIfAbsent(draft.documentKey(), key -> new HashMap<>())
                    .put(draft.ref(), draft.finalKey());
        }

        List<Map<String, Object>> artifacts = new ArrayList<>(drafts.size());
        for (NodeDraft draft : drafts) {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("isTestCase", draft.testCase());
            content.put("parentRef", resolveParent(draft, draftsByDocument, keyByRef));
            content.put("sourceRefs", List.of(draft.sourceRef()));
            if (draft.testCase()) {
                content.put("attributes", new LinkedHashMap<String, Object>());
            }
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("key", draft.finalKey());
            artifact.put("kind", Constants.AiArtifactKind.TEST_CASE);
            artifact.put("parentKey", draft.documentKey());
            artifact.put("title", draft.title());
            artifact.put("content", content);
            artifacts.add(artifact);
        }
        return artifacts;
    }

    /** 解析文档内父引用：未知引用挂根；祖先成环或超过 4 层挂根（详设 3.3 层级约束） */
    private static String resolveParent(NodeDraft draft, Map<String, Map<String, NodeDraft>> draftsByDocument,
            Map<String, Map<String, String>> keyByRef) {
        if (draft.rawParentRef() == null) {
            return null;
        }
        Map<String, String> keys = keyByRef.getOrDefault(draft.documentKey(), Map.of());
        if (!keys.containsKey(draft.rawParentRef())) {
            return null;
        }
        Map<String, NodeDraft> siblings = draftsByDocument.getOrDefault(draft.documentKey(), Map.of());
        Set<String> seen = new HashSet<>();
        String cursor = draft.rawParentRef();
        int depth = 0;
        while (cursor != null) {
            if (!seen.add(cursor) || depth >= MAX_TREE_DEPTH) {
                return null;
            }
            NodeDraft parent = siblings.get(cursor);
            if (parent == null) {
                return null;
            }
            cursor = parent.rawParentRef();
            depth++;
        }
        return keys.get(draft.rawParentRef());
    }

    // ---------- 阶段 5：用例属性 ----------

    private static void fillCaseAttributes(List<Map<String, Object>> caseArtifacts, AiChatReply reply) {
        Map<String, Map<String, Object>> attributesByKey = new HashMap<>();
        for (Map<String, Object> item : parsedArtifacts(reply)) {
            String key = trimToNull(asString(item.get("ref")));
            if (key == null) {
                continue;
            }
            attributesByKey.put(key, sanitizeAttributes(asMap(item.get("content"))));
        }
        for (Map<String, Object> artifact : caseArtifacts) {
            Map<String, Object> content = contentOf(artifact);
            Map<String, Object> attributes = attributesByKey.get(asString(artifact.get("key")));
            content.put("attributes", attributes == null ? defaultAttributes() : attributes);
        }
    }

    private static Map<String, Object> sanitizeAttributes(Map<String, Object> raw) {
        String priority = trimToNull(asString(raw.get("priority")));
        List<String> steps = stringList(raw.get("steps"), MAX_STEP_ITEMS);
        List<String> expected = stringList(raw.get("expected"), MAX_STEP_ITEMS);
        // 步骤与预期必须一一对应：模型输出不成对时按短边截齐
        int paired = Math.min(steps.size(), expected.size());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("priority", priority != null && PRIORITIES.contains(priority) ? priority : PRIORITY_DEFAULT);
        attributes.put("precondition", nvl(asString(raw.get("precondition"))));
        attributes.put("steps", new ArrayList<>(steps.subList(0, paired)));
        attributes.put("expected", new ArrayList<>(expected.subList(0, paired)));
        attributes.put("tags", stringList(raw.get("tags"), MAX_TAGS));
        return attributes;
    }

    private static Map<String, Object> defaultAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("priority", PRIORITY_DEFAULT);
        attributes.put("precondition", "");
        attributes.put("steps", new ArrayList<String>());
        attributes.put("expected", new ArrayList<String>());
        attributes.put("tags", new ArrayList<String>());
        return attributes;
    }

    // ---------- 阶段 6：来源预校验与疑似重复 ----------

    /** 过滤并保留有效来源引用（scope 内需求 ID）；返回是否仍有来源（无来源的产物被移除） */
    private static boolean retainValidSourceRefs(Map<String, Object> artifact, Set<String> scope) {
        Map<String, Object> content = contentOf(artifact);
        Object raw = content.get("sourceRefs") != null ? content.get("sourceRefs") : content.get("sourceRef");
        List<Map<String, Object>> valid = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object element : list) {
                Map<String, Object> ref = normalizeSourceRef(element, scope);
                if (ref != null) {
                    valid.add(ref);
                }
            }
        } else if (raw instanceof Map<?, ?>) {
            Map<String, Object> ref = normalizeSourceRef(raw, scope);
            if (ref != null) {
                valid.add(ref);
            }
        }
        if (valid.isEmpty()) {
            log.debug("移除缺少有效来源的产物 key={}", artifact.get("key"));
            return false;
        }
        content.put("sourceRefs", valid);
        content.remove("sourceRef");
        return true;
    }

    private static Map<String, Object> normalizeSourceRef(Object raw, Set<String> scope) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        String requirementId = trimToNull(asString(map.get("requirementId")));
        if (requirementId == null || !scope.contains(requirementId.toLowerCase())) {
            return null;
        }
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("requirementId", requirementId);
        ref.put("quote", nvl(asString(map.get("quote"))));
        return ref;
    }

    private void markModuleDuplicates(List<Map<String, Object>> artifacts, UUID projectId) {
        Map<String, String> existing = new HashMap<>();
        for (ProjectModule module : projectModuleMapper.listByProjectId(projectId)) {
            String name = trimToNull(module.getName());
            if (name != null) {
                existing.putIfAbsent(name, String.valueOf(module.getId()));
            }
        }
        for (Map<String, Object> artifact : artifacts) {
            String duplicate = existing.get(trimToNull(asString(contentOf(artifact).get("name"))));
            if (duplicate != null) {
                contentOf(artifact).put("suspectedDuplicateOf", duplicate);
            }
        }
    }

    private void markCaseDuplicates(List<Map<String, Object>> artifacts, UUID projectId) {
        List<Map<String, Object>> cases = artifacts.stream()
                .filter(item -> Constants.AiArtifactKind.TEST_CASE.equals(item.get("kind"))
                        && Boolean.TRUE.equals(contentOf(item).get("isTestCase")))
                .toList();
        if (cases.isEmpty()) {
            return;
        }
        Set<UUID> documentIds = testCaseDocumentMapper.listByProjectId(projectId).stream()
                .map(TestCaseDocument::getId).collect(Collectors.toSet());
        Map<String, String> existing = new HashMap<>();
        for (TestCaseNode node : testCaseNodeMapper.listCasesByDocumentIds(documentIds)) {
            String title = trimToNull(node.getTitle());
            if (title != null) {
                existing.putIfAbsent(title, String.valueOf(node.getId()));
            }
        }
        for (Map<String, Object> artifact : cases) {
            String duplicate = existing.get(trimToNull(asString(artifact.get("title"))));
            if (duplicate != null) {
                contentOf(artifact).put("suspectedDuplicateOf", duplicate);
            }
        }
    }

    // ---------- 通用辅助 ----------

    private String renderStage(TaskExecutionContext context, String scene, Map<String, String> variables) {
        return context.getPromptService().render(scene, AiPromptScenes.builtin(scene), variables);
    }

    private static String optionsOf(List<Map<String, Object>> artifacts) {
        return artifacts.stream()
                .map(item -> item.get("key") + "|" + nvl(asString(contentOf(item).get("name"))))
                .collect(Collectors.joining("\n"));
    }

    private static String caseOptionsOf(List<Map<String, Object>> cases) {
        return cases.stream()
                .map(item -> item.get("key") + "|" + item.get("title"))
                .collect(Collectors.joining("\n"));
    }

    private static Set<String> keySetOf(List<Map<String, Object>> artifacts) {
        return artifacts.stream().map(item -> asString(item.get("key"))).collect(Collectors.toSet());
    }
}
