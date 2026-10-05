package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import jakarta.annotation.Resource;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parsedArtifacts;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 圈选建议处理器基类（生成链详设 3.1 类型 B / 3.2 输入）：评审与计划共用
 * 范围校验（需求 / 模块口径与按范围的资源权限）、候选用例装配与单次模型调用产出
 * sel-1 单产物；轮次规则由子类给出（评审禁传，计划必传 ∈ [1,10]）。
 */
abstract class AbstractSelectionHandler implements TaskHandler {

    protected static final String PHASE_PARSE = "解析输入";
    protected static final String PHASE_SELECT = "生成圈选建议";
    private static final String SYSTEM_PROMPT = "你是资深测试评审与计划专家。严格按用户给出的 JSON 输出结构作答。";

    /** 上下文容量护栏：超大范围只装配前 N 条候选用例进提示词 */
    private static final int MAX_SCOPE_CASES = 500;
    private static final String PERMISSION_REQUIREMENT = "requirement:view";
    private static final String PERMISSION_CASE = "case:view";

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TraceEdgeMapper traceEdgeMapper;

    /** 轮次入参规则：评审禁止传入 roundCount（1000018206），计划必传且 ∈ [1,10] */
    protected abstract Integer parseRoundCount(Map<String, Object> input);

    /** 产物 kind（与任务 type 同值） */
    protected abstract String selectionKind();

    /** 产物标题前缀（评审圈选建议 / 计划圈选建议） */
    protected abstract String titlePrefix();

    @Override
    public void validateInput(Map<String, Object> input) {
        List<UUID> requirementIds = scopeRequirementIds(input);
        List<UUID> moduleIds = scopeModuleIds(input);
        if (requirementIds.isEmpty() && moduleIds.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        parseRoundCount(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        validateInput(input);
        if (context == null || context.projectId() == null) {
            // 圈选发起必经 X-Active-Project（C4）；缺失按输入非法处理
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        UUID projectId = context.projectId();
        // 按范围取资源权限（checkPermission 拿不到输入；系统触发无登录态时同口径跳过）
        LoginUser loginUser = context.loginUser();
        if (loginUser != null) {
            List<UUID> requirementIds = scopeRequirementIds(input);
            if (!requirementIds.isEmpty() && !loginUser.getPermissions().contains(PERMISSION_REQUIREMENT)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NO_PERMISSION);
            }
            if (!scopeModuleIds(input).isEmpty() && !loginUser.getPermissions().contains(PERMISSION_CASE)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
            }
        }
        Map<UUID, Requirement> requirements = indexRequirements(scopeRequirementIds(input));
        for (UUID id : scopeRequirementIds(input)) {
            Requirement item = requirements.get(id);
            if (item == null || !projectId.equals(item.getProjectId())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
            }
        }
        validateModules(projectId, scopeModuleIds(input));
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        Map<String, Object> input = context.getInput();
        List<UUID> requirementIds = scopeRequirementIds(input);
        List<UUID> moduleIds = scopeModuleIds(input);
        Integer roundCount = parseRoundCount(input);

        context.report(10, PHASE_PARSE);
        Candidates candidates = loadCandidates(context.getProjectId(), requirementIds, moduleIds);
        if (candidates.cases().isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        String scopeContext = buildScopeContext(candidates);

        context.report(30, PHASE_SELECT);
        Map<String, String> variables = new HashMap<>();
        variables.put("scopeContext", scopeContext);
        if (roundCount != null) {
            variables.put("roundCount", String.valueOf(roundCount));
        }
        AiChatReply reply = context.chat(SYSTEM_PROMPT, context.prompt(defaultPrompt(), variables));

        context.report(95, PHASE_SELECT);
        Map<String, Object> artifact = sanitizeSelection(reply, candidates, roundCount);
        List<Map<String, Object>> artifacts = new ArrayList<>();
        artifacts.add(artifact);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", artifacts);
        return new TaskResult(result, reply.tokensIn(), reply.tokensOut());
    }

    // ---------- 输入解析 ----------

    protected static List<UUID> scopeRequirementIds(Map<String, Object> input) {
        return parseIds(input, "requirementIds");
    }

    protected static List<UUID> scopeModuleIds(Map<String, Object> input) {
        return parseIds(input, "moduleIds");
    }

    private static List<UUID> parseIds(Map<String, Object> input, String field) {
        Object raw = input == null ? null : input.get(field);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        List<UUID> ids = new ArrayList<>(list.size());
        for (Object element : list) {
            try {
                ids.add(UUID.fromString(String.valueOf(element).trim()));
            } catch (IllegalArgumentException e) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
            }
        }
        return ids;
    }

    private void validateModules(UUID projectId, List<UUID> moduleIds) {
        if (moduleIds.isEmpty()) {
            return;
        }
        Set<UUID> found = projectModuleMapper.listByIds(moduleIds).stream()
                .filter(module -> projectId.equals(module.getProjectId()))
                .map(ProjectModule::getId)
                .collect(Collectors.toSet());
        for (UUID id : moduleIds) {
            if (!found.contains(id)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
            }
        }
    }

    private Map<UUID, Requirement> indexRequirements(List<UUID> requirementIds) {
        if (requirementIds.isEmpty()) {
            return Map.of();
        }
        return requirementMapper.listByIds(requirementIds).stream()
                .collect(Collectors.toMap(Requirement::getId, item -> item, (left, right) -> left));
    }

    // ---------- 候选用例装配 ----------

    protected record Candidates(Map<UUID, TestCaseNode> cases, Map<UUID, String> documentNames,
            List<Requirement> requirements, List<String> moduleNames) {
    }

    private Candidates loadCandidates(UUID projectId, List<UUID> requirementIds, List<UUID> moduleIds) {
        Map<UUID, TestCaseNode> nodes = new LinkedHashMap<>();
        Set<UUID> documentIds = new LinkedHashSet<>();
        List<Requirement> requirements = indexRequirements(requirementIds).values().stream()
                .filter(item -> projectId.equals(item.getProjectId()))
                .toList();
        List<String> moduleNames = new ArrayList<>();

        // 模块范围：所选模块及其子模块下的文档用例
        List<ProjectModule> projectModules = projectModuleMapper.listByProjectId(projectId);
        if (!moduleIds.isEmpty()) {
            Map<UUID, ProjectModule> moduleById = projectModules.stream()
                    .collect(Collectors.toMap(ProjectModule::getId, item -> item, (left, right) -> left));
            Set<UUID> moduleScope = expandWithDescendants(moduleIds, moduleById);
            moduleById.values().stream()
                    .filter(module -> moduleScope.contains(module.getId()) && module.getName() != null)
                    .forEach(module -> moduleNames.add(module.getName()));
            Set<UUID> scopedDocumentIds = testCaseDocumentMapper.listByProjectId(projectId).stream()
                    .filter(doc -> doc.getModuleId() != null && moduleScope.contains(doc.getModuleId()))
                    .map(TestCaseDocument::getId)
                    .collect(Collectors.toSet());
            for (TestCaseNode node : testCaseNodeMapper.listCasesByDocumentIds(scopedDocumentIds)) {
                nodes.put(node.getId(), node);
                documentIds.add(node.getDocumentId());
            }
        }

        // 需求范围：需求 → 有效 derivation 边 → 既有用例（detached 不参与）
        if (!requirementIds.isEmpty()) {
            List<TraceEdge> edges = traceEdgeMapper.listActiveBySource(
                    Constants.TraceNodeType.REQUIREMENT, requirementIds);
            Set<UUID> linkedCaseIds = edges.stream()
                    .filter(edge -> Constants.TraceNodeType.TEST_CASE.equals(edge.getTargetType()))
                    .map(TraceEdge::getTargetId)
                    .collect(Collectors.toSet());
            for (TestCaseNode node : testCaseNodeMapper.listByIds(linkedCaseIds)) {
                if (Constants.NodeType.CASE.equals(node.getType())) {
                    nodes.put(node.getId(), node);
                    documentIds.add(node.getDocumentId());
                }
            }
        }

        // 归属校验：文档必须在当前项目内（逻辑删除由 @TableLogic 过滤）
        Map<UUID, String> documentNames = new HashMap<>();
        testCaseDocumentMapper.listByIds(documentIds).stream()
                .filter(doc -> projectId.equals(doc.getProjectId()))
                .forEach(doc -> documentNames.put(doc.getId(), nvl(doc.getName())));
        nodes.values().removeIf(node -> !documentNames.containsKey(node.getDocumentId()));
        return new Candidates(nodes, documentNames, requirements, moduleNames);
    }

    private static Set<UUID> expandWithDescendants(List<UUID> moduleIds, Map<UUID, ProjectModule> moduleById) {
        Set<UUID> scope = new LinkedHashSet<>(moduleIds);
        Deque<UUID> queue = new ArrayDeque<>(moduleIds);
        while (!queue.isEmpty()) {
            UUID parentId = queue.poll();
            for (ProjectModule module : moduleById.values()) {
                if (parentId.equals(module.getParentId()) && scope.add(module.getId())) {
                    queue.add(module.getId());
                }
            }
        }
        return scope;
    }

    // ---------- 范围上下文与产物清洗 ----------

    private static String buildScopeContext(Candidates candidates) {
        StringBuilder builder = new StringBuilder("圈选范围：\n");
        if (!candidates.requirements().isEmpty()) {
            builder.append("- 需求：")
                    .append(candidates.requirements().stream()
                            .map(item -> nvl(item.getCode()) + " " + nvl(item.getTitle()))
                            .collect(Collectors.joining("；")))
                    .append('\n');
        }
        if (!candidates.moduleNames().isEmpty()) {
            builder.append("- 模块：").append(String.join("；", candidates.moduleNames())).append('\n');
        }
        builder.append("候选用例（共 ").append(candidates.cases().size())
                .append(" 条；id | 文档 | 标题 | 优先级）：\n");
        int shown = 0;
        for (TestCaseNode node : candidates.cases().values()) {
            if (shown >= MAX_SCOPE_CASES) {
                builder.append("（超出展示上限，仅列出前 ").append(MAX_SCOPE_CASES).append(" 条）\n");
                break;
            }
            builder.append("- ").append(node.getId())
                    .append(" | ").append(candidates.documentNames().get(node.getDocumentId()))
                    .append(" | ").append(nvl(node.getTitle()))
                    .append(" | ").append(nvl(node.getPriority()))
                    .append('\n');
            shown++;
        }
        return builder.toString();
    }

    /** 清洗模型输出为单个 sel-1 产物：范围外用例剔除、标题回填候选、轮次按域收敛 */
    private Map<String, Object> sanitizeSelection(AiChatReply reply, Candidates candidates, Integer roundCount) {
        Map<String, Map<String, Object>> itemsByCase = new LinkedHashMap<>();
        for (Map<String, Object> item : parsedArtifacts(reply)) {
            Map<String, Object> content = asMap(item.get("content"));
            if (!(content.get("items") instanceof List<?> rawItems)) {
                continue;
            }
            for (Object element : rawItems) {
                if (!(element instanceof Map<?, ?> entry)) {
                    continue;
                }
                String caseId = trimToNull(asString(entry.get("caseId")));
                TestCaseNode node = findCase(candidates, caseId);
                if (node == null) {
                    // 不得推荐范围外用例（详设 3.3）
                    continue;
                }
                Map<String, Object> normalized = new LinkedHashMap<>();
                normalized.put("caseId", String.valueOf(node.getId()));
                String title = trimToNull(asString(entry.get("title")));
                normalized.put("title", title == null ? nvl(node.getTitle()) : title);
                normalized.put("reason", nvl(asString(entry.get("reason"))));
                if (roundCount != null) {
                    normalized.put("round", normalizeRound(entry.get("round"), roundCount));
                }
                itemsByCase.putIfAbsent(String.valueOf(node.getId()), normalized);
            }
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("items", new ArrayList<>(itemsByCase.values()));
        content.put("round", null);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("key", "sel-1");
        artifact.put("kind", selectionKind());
        artifact.put("title", titlePrefix() + "（" + itemsByCase.size() + " 条）");
        artifact.put("content", content);
        return artifact;
    }

    private static TestCaseNode findCase(Candidates candidates, String caseId) {
        if (caseId == null) {
            return null;
        }
        try {
            return candidates.cases().get(UUID.fromString(caseId));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int normalizeRound(Object raw, int roundCount) {
        try {
            int round = Integer.parseInt(String.valueOf(raw).trim());
            return Math.min(Math.max(round, 1), roundCount);
        } catch (RuntimeException e) {
            return 1;
        }
    }
}
