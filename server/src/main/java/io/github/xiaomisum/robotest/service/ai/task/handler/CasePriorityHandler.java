package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
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
 * 用例级别推荐处理器（辅助功能详设 3.3）：逐选中用例产出 high / medium / low 建议，
 * current 与 suggested 均入产物，级别一致项由前端折叠、批量采纳默认跳过（详设 4.2）。
 */
@Component
public class CasePriorityHandler implements TaskHandler {

    public static final String TYPE = "case_priority";

    private static final String SYSTEM_PROMPT = "你是资深测试用例设计专家。严格按用户给出的 JSON 输出结构作答。";
    private static final String PERMISSION = "case:edit";
    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");
    /** 缺陷热点回看窗口（详设 3.3 reason 口径：该模块近 30 天缺陷） */
    private static final int HOTSPOT_DAYS = 30;
    private static final int MAX_HOTSPOT_BUGS = 5;

    @Resource
    private CaseAssistSupport assistSupport;
    @Resource
    private BugMapper bugMapper;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深测试用例设计专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                为下列选中用例推荐执行优先级（只取 high / medium / low）：
                1. 每条产物只针对一个 nodeId（必须取自选中用例清单，不得编造），并给出简短 reason；
                2. 推荐依据只用给出的上游需求与缺陷热点，不得臆造需求之外的信息；
                3. 上游需求引用只用需求清单中的需求 ID（sourceRefs），可为空。
                输出结构（JSON 对象，artifacts 即产物数组，每个选中节点一条 content）：
                {"artifacts":[{"content":{"nodeId":"…","suggested":"high","reason":"…",
                "sourceRefs":[{"requirementId":"…"}]}}]}

                选中用例与当前级别：
                {{caseContext}}

                上游需求：
                {{requirementContext}}

                模块缺陷热点（近 30 天）：
                {{bugContext}}
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
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        validateInput(input);
        if (context == null || context.projectId() == null) {
            // 辅助功能发起必经 X-Active-Project（C4）；缺失按输入非法处理
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        assistSupport.resolve(context.projectId(), CaseAssistSupport.requireDocumentId(input),
                CaseAssistSupport.requireNodeIds(input));
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        Map<String, Object> input = context.getInput();

        context.report(5, "解析选中范围");
        List<UUID> nodeIds = CaseAssistSupport.requireNodeIds(input);
        CaseAssistSupport.ResolvedCases resolved = assistSupport.resolve(context.getProjectId(),
                CaseAssistSupport.requireDocumentId(input), nodeIds);
        List<Requirement> requirements = assistSupport.upstreamRequirements(context.getProjectId(), nodeIds, List.of());

        context.report(25, "装配提示词上下文");
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("caseContext", CaseAssistSupport.priorityCaseContext(resolved));
        variables.put("requirementContext", CaseAssistSupport.requirementContext(requirements));
        variables.put("bugContext", bugContext(context.getProjectId(), resolved));

        context.report(45, "生成级别推荐");
        AiChatReply reply = context.chat(SYSTEM_PROMPT, context.prompt(defaultPrompt(), variables));

        context.report(85, "解析级别推荐");
        List<Map<String, Object>> artifacts = sanitize(reply, resolved, requirements);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", artifacts);
        return new TaskResult(result, reply.tokensIn(), reply.tokensOut());
    }

    /** 逐选中节点产出一条产物：current 取用例现状，suggested 非法或缺失时回退 current（无变化即无写入） */
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
            String current = CaseAssistSupport.displayPriority(node.getPriority());
            String suggested = trimToNull(asString(suggestion == null ? null : suggestion.get("suggested")));
            if (suggested == null || !PRIORITIES.contains(suggested)) {
                suggested = current;
            }

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("nodeId", String.valueOf(node.getId()));
            content.put("current", current);
            content.put("suggested", suggested);
            content.put("reason", nvl(asString(suggestion == null ? null : suggestion.get("reason"))));
            content.put("sourceRefs", CaseAssistSupport.sourceRefsOf(suggestion, requirementById, false));

            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("key", "node-" + index);
            artifact.put("kind", Constants.AiArtifactKind.PRIORITY_SUGGESTION);
            artifact.put("parentKey", null);
            artifact.put("title", "级别推荐：" + nvl(node.getTitle()));
            artifact.put("content", content);
            artifact.put("confirmStatus", "pending");
            artifacts.add(artifact);
        }
        return artifacts;
    }

    /** 文档无归属模块或窗口内无缺陷时给空串（渲染只替换已提供变量，故始终提供） */
    private String bugContext(UUID projectId, CaseAssistSupport.ResolvedCases resolved) {
        if (resolved.document().getModuleId() == null) {
            return "";
        }
        LocalDateTime now = LocalDateTime.now();
        List<Bug> bugs = bugMapper.findForAnalysis(projectId, now.minusDays(HOTSPOT_DAYS), now.plusDays(1));
        List<Bug> hotspot = bugs.stream()
                .filter(bug -> resolved.document().getModuleId().equals(bug.getModuleId()))
                .filter(bug -> bug.getCreatedAt() != null
                        && bug.getCreatedAt().isAfter(now.minusDays(HOTSPOT_DAYS)))
                .toList();
        if (hotspot.isEmpty()) {
            return "";
        }
        String titles = hotspot.stream().limit(MAX_HOTSPOT_BUGS)
                .map(bug -> nvl(bug.getTitle()))
                .collect(Collectors.joining("；"));
        return "所属模块近 " + HOTSPOT_DAYS + " 天共 " + hotspot.size() + " 个缺陷：" + titles;
    }
}
