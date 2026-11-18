package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.modelFailed;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parsedArtifacts;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parseUuid;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.stringList;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 用例优化处理器（辅助功能详设 3.2）：逐选中用例产出字段补全与级别建议，
 * 逐节点一条 case_optimize_suggestion 产物待确认；模型未命中任何选中节点按模型失败落态。
 */
@Component
public class CaseOptimizeTypeAdapter extends AbstractOrdinaryTaskTypeAdapter {

    public static final String TYPE = "case_optimize";

    private static final String SYSTEM_PROMPT = "你是资深测试用例设计专家。严格按用户给出的 JSON 输出结构作答。";

    private static final int MAX_STEPS = 10;
    private static final int MAX_TAGS = 5;
    private static final Set<String> PRIORITIES = Set.of("P0", "P1", "P2", "P3");
    /** 模型未给出有效建议时的兜底理由：保证每条产物都有结果（详设 3.2） */
    private static final String FALLBACK_REASON = "模型未给出调整建议，保留当前级别";

    @Resource
    private CaseAssistSupport assistSupport;

    @Override
    public String type() {
        return Constants.AiTaskType.CASE_OPTIMIZE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深测试用例设计专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                为下列选中用例一次性给出字段补全与优先级建议：
                1. 每条产物只针对一个 nodeId（必须取自选中用例清单，不得编造），必须同时给出 fields 与 priority；
                2. fields 只给 suggested 值：precondition 为单段文本，steps 与 expected 一一对应（各至多 10 条），tags 至多 5 个；未命中字段留空即可，已有人工填写内容不得覆盖；
                3. priority 只取 P0 / P1 / P2 / P3，并给出简短 reason；推荐依据只用给出的上游需求，不得臆造需求之外的信息；即使建议级别与当前级别相同也要输出，不得省略；
                4. sourceRefs 只能引用上游需求清单中的需求 ID，quote 为原文摘录（至多 300 字），无来源则留空。
                输出结构（JSON 对象，artifacts 即产物数组，每个选中节点一条 content）：
                {"artifacts":[{"content":{"nodeId":"…","fields":{"precondition":"…","steps":["…"],"expected":["…"],"tags":["…"]},
                "priority":{"suggested":"P0","reason":"…"},"sourceRefs":[{"requirementId":"…","quote":"…"}]}}]}

                选中用例与现有字段：
                {{caseContext}}

                文档层级与相邻用例：
                {{documentContext}}

                上游需求：
                {{requirementContext}}
                """;
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        CaseAssistSupport.requireDocumentId(input);
        CaseAssistSupport.requireNodeIds(input);
        CaseAssistSupport.scopeRequirementIds(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        validateInput(input);
        if (context == null || context.projectId() == null) {
            // 辅助功能发起必经 X-Active-Project（C4）：头缺失属上下文问题，不冒充输入非法
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_MISSING);
        }
        List<UUID> nodeIds = CaseAssistSupport.requireNodeIds(input);
        assistSupport.resolve(context.projectId(), CaseAssistSupport.requireDocumentId(input), nodeIds);
        assistSupport.upstreamRequirements(context.projectId(), CaseAssistSupport.scopeRequirementIds(input));
    }

    @Override
    public AgentExecutionPreparation prepare(TaskTypeAdapter.PreparationContext context) {
        CaseAssistSupport.ResolvedCases resolved = assistSupport.resolve(context.projectId(),
                CaseAssistSupport.requireDocumentId(context.input()), CaseAssistSupport.requireNodeIds(context.input()));
        List<Requirement> requirements = assistSupport.upstreamRequirements(context.projectId(),
                CaseAssistSupport.scopeRequirementIds(context.input()));
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("caseContext", CaseAssistSupport.optimizeCaseContext(resolved));
        variables.put("documentContext", CaseAssistSupport.documentContext(resolved));
        variables.put("requirementContext", CaseAssistSupport.requirementContext(requirements));
        return new AgentExecutionPreparation(variables, String.join("\n", variables.values()), List.of(),
                output -> ProposalDrafts.fromArtifacts(sanitize(new AiChatReply(output, 0, 0), resolved, requirements)),
                output -> null);
    }

    @Override public List<String> toolCodes() { return List.of("query_case_context", "query_requirements"); }

    @Override protected String defaultProposalKind() { return Constants.AiProposalKind.CASE_OPTIMIZE_SUGGESTION; }

    /** 逐选中节点产出一条产物：字段与级别合并；模型未命中字段回退现有值（no-change） */
    private List<Map<String, Object>> sanitize(AiChatReply reply, CaseAssistSupport.ResolvedCases resolved,
            List<Requirement> requirements) {
        Map<UUID, Map<String, Object>> suggestions = new LinkedHashMap<>();
        for (Map<String, Object> item : parsedArtifacts(reply)) {
            Map<String, Object> content = asMap(item.get("content"));
            UUID nodeId = parseUuid(content.get("nodeId"));
            if (nodeId != null && !suggestions.containsKey(nodeId)) {
                suggestions.put(nodeId, content);
            }
        }
        if (suggestions.keySet().stream().noneMatch(id -> resolved.nodesById().containsKey(id))) {
            throw modelFailed("模型未命中任何选中用例");
        }

        Map<UUID, Requirement> requirementById = requirements.stream()
                .collect(Collectors.toMap(Requirement::getId, item -> item, (left, right) -> left));
        List<Map<String, Object>> artifacts = new ArrayList<>(resolved.nodes().size());
        int index = 0;
        for (TestCaseNode node : resolved.nodes()) {
            index++;
            Map<String, Object> suggestion = suggestions.get(node.getId());

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("nodeId", String.valueOf(node.getId()));
            content.put("fields", fieldsOf(node, suggestion, resolved));
            content.put("priority", priorityOf(node, suggestion));
            content.put("sourceRefs", CaseAssistSupport.sourceRefsOf(suggestion, requirementById, true));

            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("key", "node-" + index);
            artifact.put("kind", Constants.AiProposalKind.CASE_OPTIMIZE_SUGGESTION);
            artifact.put("parentKey", null);
            artifact.put("title", nvl(node.getTitle()));
            artifact.put("content", content);
            artifact.put("confirmStatus", "pending");
            artifacts.add(artifact);
        }
        return artifacts;
    }

    private static Map<String, Object> fieldsOf(TestCaseNode node, Map<String, Object> suggestion,
            CaseAssistSupport.ResolvedCases resolved) {
        CaseAssistSupport.ExistingFields existing = CaseAssistSupport.existingFields(node, resolved);
        Map<String, Object> raw = asMap(suggestion == null ? null : suggestion.get("fields"));

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("precondition", fieldPair(existing.precondition(),
                trimToNull(asString(raw.get("precondition")))));
        fields.put("steps", fieldPair(existing.steps(), stringList(raw.get("steps"), MAX_STEPS)));
        fields.put("expected", fieldPair(existing.expected(), stringList(raw.get("expected"), MAX_STEPS)));
        fields.put("tags", fieldPair(existing.tags(), stringList(raw.get("tags"), MAX_TAGS)));
        return fields;
    }

    /** 级别建议：current 取用例现状；模型未给有效建议时回退 current 并补兜底理由 */
    private static Map<String, Object> priorityOf(TestCaseNode node, Map<String, Object> suggestion) {
        Map<String, Object> raw = asMap(suggestion == null ? null : suggestion.get("priority"));
        String current = nvl(node.getPriority());
        String suggested = trimToNull(asString(raw.get("suggested")));
        if (suggested == null || !PRIORITIES.contains(suggested)) {
            suggested = current;
        }
        String reason = trimToNull(asString(raw.get("reason")));
        if (reason == null) {
            reason = FALLBACK_REASON;
        }
        Map<String, Object> priority = new LinkedHashMap<>();
        priority.put("current", current);
        priority.put("suggested", suggested);
        priority.put("reason", reason);
        return priority;
    }

    /** existing / suggested 成对：模型省略或空值字段 suggested 回退 existing（详设 4.2 覆盖口径的展示基础） */
    private static Map<String, Object> fieldPair(Object existing, Object suggested) {
        boolean empty = suggested == null
                || (suggested instanceof String text && text.isBlank())
                || (suggested instanceof Collection<?> values && values.isEmpty());
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("existing", existing);
        pair.put("suggested", empty ? existing : suggested);
        return pair;
    }
}
