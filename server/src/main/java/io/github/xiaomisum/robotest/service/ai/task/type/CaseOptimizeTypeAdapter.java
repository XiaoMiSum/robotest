package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseOptimizeOps;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.HashSet;
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
 * 用例优化处理器（辅助功能详设 3.2）：逐选中用例产出节点操作指令清单（update 原地更新 / add 新增子节点），
 * 逐节点一条 case_optimize_suggestion 产物待确认；模型未命中任何选中节点按模型失败落态。
 */
@Component
public class CaseOptimizeTypeAdapter extends AbstractOrdinaryTaskTypeAdapter {

    public static final String TYPE = "case_optimize";

    private static final String FALLBACK_REASON = "模型未给出调整建议，保留当前内容";

    /** 单条提案的操作上限，避免模型输出失控 */
    private static final int MAX_OPERATIONS = 30;

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
                为下列选中用例给出针对具体节点的操作指令，人工采纳后才会真正落库：
                1. 每条产物只针对一个 nodeId（必须取自选中用例清单，不得编造）；
                2. operations 为操作数组（至多 30 条），每条只做一件事：
                   - update（原地改写）：给出 nodeId（必须取自「可操作节点清单」）、field（title / priority / tags）与 suggested 新值；
                     field=title 可作用于用例节点或任意属性子节点，field=priority / tags 仅作用于对应用例节点本身；
                     priority 只取 P0 / P1 / P2 / P3，tags 至多 5 个；已有内容需要改写时才给 update，空建议不要输出；
                   - add（新增子节点）：给出 parentId（用例节点或其任一后代）、nodeType（precondition / step / expected / normal）与 title 文本；
                     不得新增 case 节点，可在任意层级节点下追加；
                3. 每条操作必须给出简短 reason，并给出 path（取自「可操作节点清单」的 path 原文，便于人工定位）；
                4. 推荐依据只用给出的上游需求，不得臆造需求之外的信息；建议值与现状相同时也要输出并正常展示；
                5. sourceRefs 只能引用上游需求清单中的需求 ID，quote 为原文摘录（至多 300 字），无来源则留空。
                输出结构（JSON 对象，artifacts 即产物数组，每个选中节点一条 content）：
                {"artifacts":[{"content":{"nodeId":"…",
                "operations":[
                  {"op":"update","nodeId":"…","field":"title","suggested":"输入邮箱和密码","path":"登录用例 › 步骤 1","reason":"补充账号类型说明"},
                  {"op":"update","nodeId":"…","field":"priority","suggested":"P0","path":"登录用例","reason":"上游需求优先级为高"},
                  {"op":"add","parentId":"…","nodeType":"step","title":"输入图形验证码","path":"登录用例","reason":"上游要求校验验证码"}
                ],"sourceRefs":[{"requirementId":"…","quote":"…"}]}}]}

                选中用例与现有字段：
                {{caseContext}}

                可操作节点清单（update 的 nodeId 与 add 的 parentId 只能引用此处的 id）：
                {{nodeContext}}

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
        variables.put("nodeContext", CaseAssistSupport.nodeContext(resolved));
        variables.put("documentContext", CaseAssistSupport.documentContext(resolved));
        variables.put("requirementContext", CaseAssistSupport.requirementContext(requirements));
        return new AgentExecutionPreparation(variables, String.join("\n", variables.values()), List.of(),
                output -> ProposalDrafts.fromArtifacts(sanitize(new AiChatReply(output, 0, 0), resolved, requirements)),
                output -> null);
    }

    @Override public List<String> toolCodes() { return List.of("query_case_context", "query_requirements"); }

    @Override protected String defaultProposalKind() { return Constants.AiProposalKind.CASE_OPTIMIZE_SUGGESTION; }

    /** 逐选中节点产出一条产物：操作指令只保留定位合法且建议非空的项，非法引用在清洗阶段丢弃 */
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
            content.put("operations", operationsOf(node, suggestion, resolved));
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

    /** 清洗操作：仅保留定位属于该用例子树、字段 / 类型合法且建议非空的操作，回填 existing / path / reason */
    private static List<Map<String, Object>> operationsOf(TestCaseNode node, Map<String, Object> suggestion,
            CaseAssistSupport.ResolvedCases resolved) {
        Set<UUID> subtree = new HashSet<>();
        collectSubtree(node.getId(), resolved.childrenByParent(), subtree);
        List<Map<String, Object>> operations = new ArrayList<>();
        if (suggestion == null) {
            return operations;
        }
        Object raw = suggestion.get("operations");
        if (!(raw instanceof List<?> items)) {
            return operations;
        }
        for (Object element : items) {
            if (operations.size() >= MAX_OPERATIONS) {
                break;
            }
            Map<String, Object> op = cleanOperation(asMap(element), node, resolved, subtree);
            if (op != null) {
                operations.add(op);
            }
        }
        return operations;
    }

    private static Map<String, Object> cleanOperation(Map<String, Object> raw, TestCaseNode caseNode,
            CaseAssistSupport.ResolvedCases resolved, Set<UUID> subtree) {
        String reason = trimToNull(asString(raw.get("reason")));
        if (reason == null) {
            reason = FALLBACK_REASON;
        }
        String op = trimToNull(asString(raw.get("op")));
        if (CaseOptimizeOps.OP_UPDATE.equals(op)) {
            return cleanUpdate(raw, caseNode, resolved, subtree, reason);
        }
        if (CaseOptimizeOps.OP_ADD.equals(op)) {
            return cleanAdd(raw, resolved, subtree, reason);
        }
        return null;
    }

    private static Map<String, Object> cleanUpdate(Map<String, Object> raw, TestCaseNode caseNode,
            CaseAssistSupport.ResolvedCases resolved, Set<UUID> subtree, String reason) {
        UUID nodeId = parseUuid(raw.get("nodeId"));
        TestCaseNode target = nodeId == null ? null : resolved.nodesById().get(nodeId);
        String field = trimToNull(asString(raw.get("field")));
        if (target == null || !subtree.contains(nodeId) || field == null
                || !CaseOptimizeOps.UPDATE_FIELDS.contains(field)
                || (!caseNode.getId().equals(nodeId) && !CaseOptimizeOps.FIELD_TITLE.equals(field))) {
            return null;
        }
        Object suggested = CaseOptimizeOps.normalizeSuggested(field, raw.get("suggested"));
        if (suggested == null) {
            return null;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        cleaned.put("op", CaseOptimizeOps.OP_UPDATE);
        cleaned.put("nodeId", String.valueOf(nodeId));
        cleaned.put("field", field);
        cleaned.put("existing", existingField(target, field));
        cleaned.put("suggested", suggested);
        cleaned.put("path", CaseAssistSupport.nodePath(target, resolved.nodesById()));
        cleaned.put("reason", reason);
        return cleaned;
    }

    private static Map<String, Object> cleanAdd(Map<String, Object> raw,
            CaseAssistSupport.ResolvedCases resolved, Set<UUID> subtree, String reason) {
        UUID parentId = parseUuid(raw.get("parentId"));
        TestCaseNode parent = parentId == null ? null : resolved.nodesById().get(parentId);
        String nodeType = trimToNull(asString(raw.get("nodeType")));
        String title = (String) CaseOptimizeOps.normalizeSuggested(
                CaseOptimizeOps.FIELD_TITLE, raw.get("title"));
        if (parent == null || !subtree.contains(parentId) || nodeType == null
                || !CaseOptimizeOps.ADD_NODE_TYPES.contains(nodeType) || title == null) {
            return null;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        cleaned.put("op", CaseOptimizeOps.OP_ADD);
        cleaned.put("parentId", String.valueOf(parentId));
        cleaned.put("nodeType", nodeType);
        cleaned.put("title", title);
        cleaned.put("path", CaseAssistSupport.nodePath(parent, resolved.nodesById()));
        cleaned.put("reason", reason);
        return cleaned;
    }

    private static Object existingField(TestCaseNode target, String field) {
        if (CaseOptimizeOps.FIELD_TITLE.equals(field)) {
            return nvl(target.getTitle());
        }
        if (CaseOptimizeOps.FIELD_PRIORITY.equals(field)) {
            return nvl(target.getPriority());
        }
        return target.getTags() == null ? List.of() : List.copyOf(target.getTags());
    }

    private static void collectSubtree(UUID rootId, Map<UUID, List<TestCaseNode>> childrenByParent, Set<UUID> out) {
        if (!out.add(rootId)) {
            return;
        }
        for (TestCaseNode child : childrenByParent.getOrDefault(rootId, List.of())) {
            collectSubtree(child.getId(), childrenByParent, out);
        }
    }
}
