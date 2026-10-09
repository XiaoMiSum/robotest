package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.abbreviate;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.modelFailed;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parsedArtifacts;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parseUuid;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.stringList;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 用例补全处理器（辅助功能详设 3.2）：逐选中用例产出字段补全与补充场景建议，
 * 逐节点一条 case_suggestion 产物待确认；模型未命中任何选中节点按模型失败落态。
 */
@Component
public class CaseCompleteHandler implements TaskHandler {

    public static final String TYPE = "case_complete";

    private static final String SYSTEM_PROMPT = "你是资深测试用例设计专家。严格按用户给出的 JSON 输出结构作答。";
    private static final String PERMISSION = "case:edit";

    private static final int MAX_STEPS = 10;
    private static final int MAX_TAGS = 5;
    private static final int MAX_EXTRA_NODES = 5;
    private static final int MAX_TITLE = 200;

    @Resource
    private CaseAssistSupport assistSupport;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深测试用例设计专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                为下列选中用例给出补全建议：
                1. 每条产物只针对一个 nodeId（必须取自选中用例清单，不得编造）；未命中字段留空即可，已有人工填写内容不得覆盖；
                2. fields 只给 suggested 值：precondition 为单段文本，steps 与 expected 一一对应（各至多 10 条），tags 至多 5 个；
                3. extraNodes 为确实缺失的补充场景（至多 5 条），isTestCase=true 表示补一条完整用例、false 表示补结构分组节点；
                4. sourceRefs 只能引用上游需求清单中的需求 ID，quote 为原文摘录（至多 300 字），无来源则留空。
                输出结构（JSON 对象，artifacts 即产物数组，每个选中节点一条 content）：
                {"artifacts":[{"content":{"nodeId":"…","fields":{"precondition":"…","steps":["…"],"expected":["…"],"tags":["…"]},
                "extraNodes":[{"title":"…","isTestCase":true}],"sourceRefs":[{"requirementId":"…","quote":"…"}]}}]}

                选中用例与现有字段：
                {{caseContext}}

                文档层级与相邻用例：
                {{documentContext}}

                上游需求：
                {{requirementContext}}
                """;
    }

    @Override
    public void checkPermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains(PERMISSION)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_NO_PERMISSION);
        }
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
            // 辅助功能发起必经 X-Active-Project（C4）；缺失按输入非法处理
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        List<UUID> nodeIds = CaseAssistSupport.requireNodeIds(input);
        assistSupport.resolve(context.projectId(), CaseAssistSupport.requireDocumentId(input), nodeIds);
        assistSupport.upstreamRequirements(context.projectId(), nodeIds,
                CaseAssistSupport.scopeRequirementIds(input));
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        Map<String, Object> input = context.getInput();

        context.report(5, "解析选中范围");
        List<UUID> nodeIds = CaseAssistSupport.requireNodeIds(input);
        CaseAssistSupport.ResolvedCases resolved = assistSupport.resolve(context.getProjectId(),
                CaseAssistSupport.requireDocumentId(input), nodeIds);
        List<Requirement> requirements = assistSupport.upstreamRequirements(context.getProjectId(), nodeIds,
                CaseAssistSupport.scopeRequirementIds(input));

        context.report(25, "装配提示词上下文");
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("caseContext", CaseAssistSupport.completeCaseContext(resolved));
        variables.put("documentContext", CaseAssistSupport.documentContext(resolved));
        variables.put("requirementContext", CaseAssistSupport.requirementContext(requirements));

        context.report(45, "生成补全建议");
        AiChatReply reply = context.chat(SYSTEM_PROMPT, context.prompt(defaultPrompt(), variables));

        context.report(85, "解析补全建议");
        List<Map<String, Object>> artifacts = sanitize(reply, resolved, requirements);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", artifacts);
        return new TaskResult(result, reply.tokensIn(), reply.tokensOut());
    }

    /** 逐选中节点产出一条产物：模型命中的字段用其建议值，未命中字段回退现有值（no-change） */
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
            artifacts.add(artifactOf(index, node, suggestions.get(node.getId()), resolved, requirementById));
        }
        return artifacts;
    }

    private static Map<String, Object> artifactOf(int index, TestCaseNode node, Map<String, Object> suggestion,
            CaseAssistSupport.ResolvedCases resolved, Map<UUID, Requirement> requirementById) {
        CaseAssistSupport.ExistingFields existing = CaseAssistSupport.existingFields(node, resolved);
        Map<String, Object> raw = asMap(suggestion == null ? null : suggestion.get("fields"));

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("precondition", fieldPair(existing.precondition(),
                trimToNull(asString(raw.get("precondition")))));
        fields.put("steps", fieldPair(existing.steps(), stringList(raw.get("steps"), MAX_STEPS)));
        fields.put("expected", fieldPair(existing.expected(), stringList(raw.get("expected"), MAX_STEPS)));
        fields.put("tags", fieldPair(existing.tags(), stringList(raw.get("tags"), MAX_TAGS)));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("nodeId", String.valueOf(node.getId()));
        content.put("fields", fields);
        content.put("extraNodes", extraNodesOf(suggestion, node));
        content.put("sourceRefs", CaseAssistSupport.sourceRefsOf(suggestion, requirementById, true));

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("key", "node-" + index);
        artifact.put("kind", Constants.AiArtifactKind.CASE_SUGGESTION);
        artifact.put("parentKey", null);
        artifact.put("title", nvl(node.getTitle()));
        artifact.put("content", content);
        artifact.put("confirmStatus", "pending");
        return artifact;
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

    private static List<Map<String, Object>> extraNodesOf(Map<String, Object> suggestion, TestCaseNode node) {
        if (suggestion == null) {
            return List.of();
        }
        List<Map<String, Object>> extraNodes = new ArrayList<>();
        if (suggestion.get("extraNodes") instanceof List<?> raw) {
            for (Object element : raw) {
                if (extraNodes.size() >= MAX_EXTRA_NODES) {
                    break;
                }
                Map<String, Object> item = asMap(element);
                String title = trimToNull(asString(item.get("title")));
                if (title == null) {
                    continue;
                }
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("title", abbreviate(title, MAX_TITLE));
                extra.put("isTestCase", Boolean.parseBoolean(String.valueOf(item.get("isTestCase"))));
                // 补充节点挂源用例之下，采纳期按目标位置（缺省 sibling）解析父级
                extra.put("parentNodeId", String.valueOf(node.getId()));
                extraNodes.add(extra);
            }
        }
        return extraNodes;
    }
}
