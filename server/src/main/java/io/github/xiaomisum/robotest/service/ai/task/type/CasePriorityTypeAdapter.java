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
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 用例级别推荐处理器（辅助功能详设 3.3）：逐选中用例产出 P0 / P1 / P2 / P3 建议，
 * current 与 suggested 均入产物；级别一致项同样产出结果并正常展示，不折叠隐藏。
 */
@Component
public class CasePriorityTypeAdapter extends AbstractOrdinaryTaskTypeAdapter {

    public static final String TYPE = "case_priority";

    private static final Set<String> PRIORITIES = Set.of("P0", "P1", "P2", "P3");
    /** 模型未给出有效建议时的兜底理由：保证每条产物都有结果（详设 3.3） */
    private static final String FALLBACK_REASON = "模型未给出调整建议，保留当前级别";

    @Resource
    private CaseAssistSupport assistSupport;

    @Override
    public String type() {
        return Constants.AiTaskType.CASE_PRIORITY;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深测试用例设计专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                为下列选中用例推荐执行优先级（只取 P0 / P1 / P2 / P3）：
                1. 每条产物只针对一个 nodeId（必须取自选中用例清单，不得编造），并给出简短 reason；
                2. 推荐依据只用给出的上游需求，不得臆造需求之外的信息；
                3. 每个选中用例都必须输出一条结果（含 suggested 与 reason），即使建议级别与当前级别相同也要输出，不得省略；
                4. 上游需求引用只用需求清单中的需求 ID（sourceRefs），可为空。
                输出结构（JSON 对象，artifacts 即产物数组，每个选中节点一条 content）：
                {"artifacts":[{"content":{"nodeId":"…","suggested":"P0","reason":"…",
                "sourceRefs":[{"requirementId":"…"}]}}]}

                选中用例与当前级别：
                {{caseContext}}

                上游需求：
                {{requirementContext}}
                """;
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        CaseAssistSupport.requireDocumentId(input);
        CaseAssistSupport.requireNodeIds(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        validateInput(input);
        if (context == null || context.projectId() == null) {
            // 辅助功能发起必经 X-Active-Project（C4）：头缺失属上下文问题，不冒充输入非法
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_MISSING);
        }
        assistSupport.resolve(context.projectId(), CaseAssistSupport.requireDocumentId(input),
                CaseAssistSupport.requireNodeIds(input));
    }

    @Override
    public AgentExecutionPreparation prepare(TaskTypeAdapter.PreparationContext context) {
        CaseAssistSupport.ResolvedCases resolved = assistSupport.resolve(context.projectId(),
                CaseAssistSupport.requireDocumentId(context.input()), CaseAssistSupport.requireNodeIds(context.input()));
        List<Requirement> requirements = assistSupport.upstreamRequirements(context.projectId(), List.of());
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("caseContext", CaseAssistSupport.priorityCaseContext(resolved));
        variables.put("requirementContext", CaseAssistSupport.requirementContext(requirements));
        return new AgentExecutionPreparation(variables, String.join("\n", variables.values()), List.of(),
                output -> ProposalDrafts.fromArtifacts(sanitize(new AiChatReply(output, 0, 0), resolved, requirements)),
                output -> null);
    }

    @Override public List<String> toolCodes() { return List.of("query_case_context"); }

    @Override protected String defaultProposalKind() { return Constants.AiProposalKind.PRIORITY_SUGGESTION; }

    /** 逐选中节点产出一条产物：current 取用例现状；模型未给有效建议时回退 current 并补兜底理由，保证每条都有结果 */
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
            String current = nvl(node.getPriority());
            String suggested = trimToNull(asString(suggestion == null ? null : suggestion.get("suggested")));
            if (suggested == null || !PRIORITIES.contains(suggested)) {
                suggested = current;
            }
            String reason = trimToNull(asString(suggestion == null ? null : suggestion.get("reason")));
            if (reason == null) {
                reason = FALLBACK_REASON;
            }

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("nodeId", String.valueOf(node.getId()));
            content.put("current", current);
            content.put("suggested", suggested);
            content.put("reason", reason);
            content.put("sourceRefs", CaseAssistSupport.sourceRefsOf(suggestion, requirementById, false));

            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("key", "node-" + index);
            artifact.put("kind", Constants.AiProposalKind.PRIORITY_SUGGESTION);
            artifact.put("parentKey", null);
            artifact.put("title", "级别推荐：" + nvl(node.getTitle()));
            artifact.put("content", content);
            artifact.put("confirmStatus", "pending");
            artifacts.add(artifact);
        }
        return artifacts;
    }
}
