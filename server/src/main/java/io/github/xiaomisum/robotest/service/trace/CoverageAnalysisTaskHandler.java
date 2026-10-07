package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.domain.requirement.RequirementSuggestionParser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.abbreviate;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.modelFailed;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.stringList;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 覆盖分析任务处理器（追溯矩阵详设 4.2 / AI 基座详设 3.6.1）：按需求逐条将派生边关联
 * 用例与需求正文语义比对，产出 covered / partial / uncovered + evidence 并直接写入
 * trace_coverage_result（无产物）；无关联用例的需求不调模型直接判 uncovered，
 * 写入由矩阵服务按「人工判定优先」跳过（4.2 步骤 3）。
 */
@Component
public class CoverageAnalysisTaskHandler implements TaskHandler {

    private static final String TYPE = "coverage_analysis";
    private static final String PERMISSION_TRACE_VIEW = "trace:view";
    private static final String SYSTEM_PROMPT = "你是资深测试覆盖分析专家。严格按用户给出的 JSON 输出结构作答。";

    /** 分析范围上限（详设 3.7）：显式圈选与缺省全量同口径 */
    private static final int MAX_SCOPE = 100;
    /** 上下文护栏：超大关联用例集合只装配前 N 条进提示词 */
    private static final int MAX_CONTEXT_CASES = 200;
    private static final int MAX_DESCRIPTION_CHARS = 4000;
    private static final int MAX_GAPS = 20;
    private static final int MAX_GAP_CHARS = 500;
    private static final int MAX_REASON_CHARS = 1000;

    private static final Set<String> COVERABLE = Set.of(
            Constants.TraceCoverageStatus.COVERED,
            Constants.TraceCoverageStatus.PARTIAL,
            Constants.TraceCoverageStatus.UNCOVERED);

    private static final String DEFAULT_PROMPT = """
            你是资深测试覆盖分析专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
            对比需求正文与该需求关联的测试用例清单，判断该需求的测试覆盖程度：
            - covered：需求关键点均有对应用例覆盖
            - partial：部分关键点缺少对应用例，gaps 列出缺口
            - uncovered：需求关键点基本没有对应用例覆盖
            matchedCaseIds 只能填写关联用例清单中出现的用例 ID（原样照抄，不得虚构）；
            gaps 为缺口描述数组（无缺口给空数组）；reason 为一句话判定理由（100 字内）。
            输出结构：
            {"coverageStatus": "covered|partial|uncovered", "matchedCaseIds": ["..."], "gaps": ["..."], "reason": "..."}

            需求：
            {{requirementContext}}

            关联用例：
            {{caseContext}}
            """;

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private TraceEdgeMapper traceEdgeMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TraceMatrixService traceMatrixService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return DEFAULT_PROMPT;
    }

    /** 发起权限 = 追溯查看（3.8 人工修正仍为 trace:edit），登录态缺失由提交侧跳过校验 */
    @Override
    public void checkPermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains(PERMISSION_TRACE_VIEW)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_NO_PERMISSION);
        }
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        parseRequirementIds(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        if (context == null || context.projectId() == null) {
            // 发起必经 X-Active-Project（C4）；缺失视为参数非法
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_COVERAGE_INPUT_INVALID);
        }
        resolveScope(context.projectId(), input);
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        context.report(10, "读取分析范围");
        List<Requirement> scope = resolveScope(context.getProjectId(), context.getInput());

        context.report(30, "读取派生边与关联用例");
        Map<UUID, List<TestCaseNode>> linkedCases = loadLinkedCases(context.getProjectId(), scope);

        int analyzed = 0;
        int skipped = 0;
        int uncovered = 0;
        int tokensIn = 0;
        int tokensOut = 0;
        int total = scope.size();
        for (int i = 0; i < total; i++) {
            Requirement item = scope.get(i);
            List<TestCaseNode> cases = linkedCases.getOrDefault(item.getId(), List.of());
            String coverageStatus;
            Map<String, Object> evidence;
            if (cases.isEmpty()) {
                // 无关联用例不消耗模型调用，直接判 uncovered 并留痕（4.2 步骤 2）
                coverageStatus = Constants.TraceCoverageStatus.UNCOVERED;
                evidence = directUncoveredEvidence();
            } else {
                AiChatReply reply = context.chat(SYSTEM_PROMPT,
                        context.prompt(defaultPrompt(), variables(item, cases)));
                tokensIn += reply.tokensIn();
                tokensOut += reply.tokensOut();
                Map<String, Object> parsed = RequirementSuggestionParser.parseJsonObject(reply.content());
                coverageStatus = sanitizeStatus(parsed);
                evidence = sanitizeEvidence(parsed, cases);
            }
            analyzed++;
            boolean written = traceMatrixService.applyAiCoverage(context.getProjectId(), context.getTaskId(),
                    item.getId(), coverageStatus, evidence);
            if (!written) {
                skipped++;
            } else if (Constants.TraceCoverageStatus.UNCOVERED.equals(coverageStatus)) {
                uncovered++;
            }
            context.report(30 + 60 * (i + 1) / total, "语义比对 " + (i + 1) + "/" + total);
        }
        context.report(100, "覆盖分析完成");

        // 该类型无产物（AI 基座详设 3.6.1）：结论已落追溯表，result 只回统计口径——
        // uncoveredCount 按实际写入计数，与矩阵展示一致（人工行跳过者不计）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analyzedCount", analyzed);
        result.put("skippedCount", skipped);
        result.put("uncoveredCount", uncovered);
        return new TaskResult(result, tokensIn, tokensOut);
    }

    /**
     * 分析范围解析（提交校验与执行共用）：显式圈选逐条校验存在 + 同项目 + 非归档，
     * 缺省取项目内全量非归档需求并复核上限，任一不满足按参数非法（1000018156）。
     */
    private List<Requirement> resolveScope(UUID projectId, Map<String, Object> input) {
        List<UUID> requested = parseRequirementIds(input);
        if (!requested.isEmpty()) {
            Map<UUID, Requirement> byId = requirementMapper.listByIds(requested).stream()
                    .collect(Collectors.toMap(Requirement::getId, row -> row, (left, right) -> left));
            List<Requirement> scope = new ArrayList<>(requested.size());
            for (UUID id : requested) {
                Requirement row = byId.get(id);
                if (row == null || !projectId.equals(row.getProjectId())
                        || Constants.RequirementStatus.ARCHIVED.equals(row.getStatus())) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_COVERAGE_INPUT_INVALID);
                }
                scope.add(row);
            }
            return scope;
        }
        long count = requirementMapper.countAnalyzableByProject(projectId);
        if (count == 0 || count > MAX_SCOPE) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_COVERAGE_INPUT_INVALID);
        }
        return requirementMapper.listAnalyzableByProject(projectId);
    }

    private static List<UUID> parseRequirementIds(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("requirementIds");
        if (raw == null) {
            // 缺省或空数组 = 项目内全量非归档需求
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_COVERAGE_INPUT_INVALID);
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (Object element : list) {
            try {
                ids.add(UUID.fromString(String.valueOf(element).trim()));
            } catch (IllegalArgumentException invalid) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_COVERAGE_INPUT_INVALID);
            }
        }
        if (ids.size() > MAX_SCOPE) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_COVERAGE_INPUT_INVALID);
        }
        return List.copyOf(ids);
    }

    /** 派生边 → 关联用例装配：仅 derivation + 目标用例 + 非 conflict + 同项目边，节点须为用例类型 */
    private Map<UUID, List<TestCaseNode>> loadLinkedCases(UUID projectId, List<Requirement> scope) {
        List<UUID> requirementIds = scope.stream().map(Requirement::getId).toList();
        List<TraceEdge> edges = traceEdgeMapper.listActiveBySource(Constants.TraceNodeType.REQUIREMENT,
                requirementIds);
        Map<UUID, Set<UUID>> caseIdsByRequirement = new LinkedHashMap<>();
        Set<UUID> allCaseIds = new LinkedHashSet<>();
        for (TraceEdge edge : edges) {
            if (!Constants.TraceEdgeType.DERIVATION.equals(edge.getEdgeType())
                    || !Constants.TraceNodeType.TEST_CASE.equals(edge.getTargetType())
                    || Constants.TraceEdgeStatus.CONFLICT.equals(edge.getStatus())
                    || !projectId.equals(edge.getProjectId())) {
                continue;
            }
            caseIdsByRequirement.computeIfAbsent(edge.getSourceId(), key -> new LinkedHashSet<>())
                    .add(edge.getTargetId());
            allCaseIds.add(edge.getTargetId());
        }
        if (allCaseIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TestCaseNode> caseById = testCaseNodeMapper.listByIds(allCaseIds).stream()
                .filter(node -> Constants.NodeType.CASE.equals(node.getType()))
                .collect(Collectors.toMap(TestCaseNode::getId, node -> node, (left, right) -> left));
        Map<UUID, List<TestCaseNode>> linked = new LinkedHashMap<>();
        caseIdsByRequirement.forEach((requirementId, caseIds) -> {
            List<TestCaseNode> cases = caseIds.stream().map(caseById::get)
                    .filter(Objects::nonNull).toList();
            if (!cases.isEmpty()) {
                linked.put(requirementId, cases);
            }
        });
        return linked;
    }

    private static Map<String, String> variables(Requirement item, List<TestCaseNode> cases) {
        StringBuilder caseContext = new StringBuilder();
        int limit = Math.min(cases.size(), MAX_CONTEXT_CASES);
        for (int i = 0; i < limit; i++) {
            TestCaseNode node = cases.get(i);
            caseContext.append(node.getId()).append(" | ").append(nvl(node.getTitle())).append('\n');
        }
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("requirementContext", nvl(item.getCode()) + " " + nvl(item.getTitle()) + "\n"
                + abbreviate(nvl(item.getDescription()), MAX_DESCRIPTION_CHARS));
        variables.put("caseContext", caseContext.toString());
        return variables;
    }

    private static Map<String, Object> directUncoveredEvidence() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("coveredBy", List.of());
        evidence.put("gaps", List.of("无关联用例"));
        return evidence;
    }

    private static String sanitizeStatus(Map<String, Object> parsed) {
        String status = trimToNull(asString(parsed.get("coverageStatus")));
        if (status == null || !COVERABLE.contains(status)) {
            throw modelFailed("覆盖状态取值非法：" + status);
        }
        return status;
    }

    /** 清洗判定依据：matchedCaseIds 只保留已知用例并映射为标题，缺口与理由按长度截断 */
    private static Map<String, Object> sanitizeEvidence(Map<String, Object> parsed, List<TestCaseNode> cases) {
        Map<UUID, String> titleById = cases.stream()
                .collect(Collectors.toMap(TestCaseNode::getId, node -> nvl(node.getTitle()),
                        (left, right) -> left));
        List<String> coveredBy = stringList(parsed.get("matchedCaseIds"), MAX_CONTEXT_CASES).stream()
                .map(CoverageAnalysisTaskHandler::parseUuid)
                .filter(Objects::nonNull)
                .map(titleById::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("coveredBy", coveredBy);
        evidence.put("gaps", stringList(parsed.get("gaps"), MAX_GAPS).stream()
                .map(value -> abbreviate(value, MAX_GAP_CHARS)).toList());
        String reason = trimToNull(asString(parsed.get("reason")));
        if (reason != null) {
            evidence.put("reason", abbreviate(reason, MAX_REASON_CHARS));
        }
        return evidence;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            // 模型幻觉 ID 不入 evidence，只保留可核验的已知用例
            return null;
        }
    }
}
