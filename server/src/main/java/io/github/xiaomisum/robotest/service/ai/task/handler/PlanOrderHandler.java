package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanNodeSnapshotMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
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

/**
 * 执行顺序建议处理器（辅助功能详设 3.4）：按计划快照现序产出 order_suggestion 单产物，
 * items 全覆盖计划内关联用例；采纳只改排序，不改关联集合与快照内容（详设 4.4）。
 */
@Component
public class PlanOrderHandler implements TaskHandler {

    public static final String TYPE = "plan_order";

    private static final String SYSTEM_PROMPT = "你是资深测试计划执行专家。严格按用户给出的 JSON 输出结构作答。";
    private static final String PERMISSION = "plan:execute";

    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private TestPlanNodeSnapshotMapper testPlanNodeSnapshotMapper;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深测试计划执行专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                为计划内关联用例给出执行顺序建议（suggestedRank 从 1 开始，1 为最先执行）：
                1. 只能引用计划用例清单中的 nodeId，每个用例恰好出现一次，不得引入清单外用例；
                2. 依据限于清单给出的信息（优先级、最近执行结果、用例路径），不得臆造；
                3. reason 简述该用例排在该位次的依据，可留空。
                输出结构（JSON 对象，artifacts 即产物数组）：
                {"artifacts":[{"content":{"items":[{"nodeId":"…","suggestedRank":1,"reason":"…"}]}}]}

                计划关联用例与最近执行结果：
                {{planContext}}

                推荐轮次：
                {{roundCount}}
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
        requirePlanId(input);
        parseRound(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        validateInput(input);
        if (context == null || context.projectId() == null) {
            // 辅助功能发起必经 X-Active-Project（C4）；缺失按输入非法处理
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        UUID planId = requirePlanId(input);
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null || !context.projectId().equals(plan.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_PLAN_NOT_FOUND);
        }
        requireAssociatedCases(planId);
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        Map<String, Object> input = context.getInput();

        context.report(5, "解析计划范围");
        UUID planId = requirePlanId(input);
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null || !context.getProjectId().equals(plan.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_PLAN_NOT_FOUND);
        }
        List<TestPlanNodeSnapshot> associated = requireAssociatedCases(planId);
        List<TestPlanNodeSnapshot> currentOrder = currentOrder(planId, associated);

        context.report(25, "装配提示词上下文");
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("planContext", planContext(currentOrder));
        // round 仅透传提示词，缺省空串（渲染只替换已提供变量）
        variables.put("roundCount", nvl(parseRound(input)));

        context.report(45, "生成顺序建议");
        AiChatReply reply = context.chat(SYSTEM_PROMPT, context.prompt(defaultPrompt(), variables));

        context.report(85, "解析顺序建议");
        Map<String, Object> artifact = sanitize(reply, plan, currentOrder);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", List.of(artifact));
        return new TaskResult(result, reply.tokensIn(), reply.tokensOut());
    }

    /** 计划用例按快照树现序（DFS 平铺，兄弟与根均按 sortOrder nullsLast 稳定排序） */
    private List<TestPlanNodeSnapshot> currentOrder(UUID planId, List<TestPlanNodeSnapshot> associated) {
        List<TestPlanNodeSnapshot> all = testPlanNodeSnapshotMapper.listByPlanId(planId);
        Map<UUID, List<TestPlanNodeSnapshot>> children = new HashMap<>();
        List<TestPlanNodeSnapshot> roots = new ArrayList<>();
        Set<UUID> ids = all.stream().map(TestPlanNodeSnapshot::getId).collect(Collectors.toSet());
        for (TestPlanNodeSnapshot node : all) {
            if (node.getParentId() == null || !ids.contains(node.getParentId())) {
                // 父级缺失的孤儿快照按根处理，避免顺序整体丢失
                roots.add(node);
            } else {
                children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node);
            }
        }
        Comparator<TestPlanNodeSnapshot> bySort = Comparator.comparing(
                node -> node.getSortOrder() == null ? Integer.MAX_VALUE : node.getSortOrder());
        roots.sort(bySort);
        children.values().forEach(list -> list.sort(bySort));

        Set<UUID> associatedIds = associated.stream()
                .map(TestPlanNodeSnapshot::getId).collect(Collectors.toSet());
        List<TestPlanNodeSnapshot> ordered = new ArrayList<>(associatedIds.size());
        Deque<TestPlanNodeSnapshot> stack = new ArrayDeque<>();
        for (int i = roots.size() - 1; i >= 0; i--) {
            stack.push(roots.get(i));
        }
        while (!stack.isEmpty()) {
            TestPlanNodeSnapshot node = stack.pop();
            if (associatedIds.contains(node.getId())) {
                ordered.add(node);
            }
            List<TestPlanNodeSnapshot> next = children.getOrDefault(node.getId(), List.of());
            for (int i = next.size() - 1; i >= 0; i--) {
                stack.push(next.get(i));
            }
        }
        return ordered;
    }

    private List<TestPlanNodeSnapshot> requireAssociatedCases(UUID planId) {
        List<TestPlanNodeSnapshot> associated =
                testPlanNodeSnapshotMapper.listAssociatedByPlanId(planId, Constants.NodeType.CASE);
        if (associated.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_PLAN_CASES_EMPTY);
        }
        return associated;
    }

    private static String planContext(List<TestPlanNodeSnapshot> currentOrder) {
        StringBuilder builder = new StringBuilder("计划关联用例（共 ").append(currentOrder.size())
                .append(" 条，按当前执行顺序，id | 优先级 | 最近结果 | 标题）：\n");
        for (TestPlanNodeSnapshot node : currentOrder) {
            builder.append("- ").append(node.getId())
                    .append(" | ").append(nvl(node.getPriority()))
                    .append(" | ").append(nvl(node.getLastResult()))
                    .append(" | ").append(nvl(node.getTitle()))
                    .append('\n');
        }
        return builder.toString();
    }

    /** 清洗为单产物：模型响应项按 suggestedRank 升序、未响应按现序补尾，suggestedRank 连续化覆盖全部关联用例 */
    private Map<String, Object> sanitize(AiChatReply reply, TestPlan plan,
            List<TestPlanNodeSnapshot> currentOrder) {
        Map<UUID, TestPlanNodeSnapshot> byId = currentOrder.stream()
                .collect(Collectors.toMap(TestPlanNodeSnapshot::getId, node -> node, (left, right) -> left));

        // 模型响应项：仅保留计划内用例，去重取首个，非法序号视为未指定（排尾，保持响应序）
        record Ranked(TestPlanNodeSnapshot node, int rank, String reason) {
        }
        List<Ranked> responded = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Map<String, Object> item : parsedArtifacts(reply)) {
            if (!(asMap(item.get("content")).get("items") instanceof List<?> raw)) {
                continue;
            }
            for (Object element : raw) {
                Map<String, Object> entry = asMap(element);
                UUID nodeId = parseUuid(entry.get("nodeId"));
                TestPlanNodeSnapshot node = nodeId == null ? null : byId.get(nodeId);
                if (node == null || !seen.add(node.getId())) {
                    // 清单外或重复出现的用例直接丢弃（详设 3.4 不得引入清单外用例）
                    continue;
                }
                responded.add(new Ranked(node, parseRank(entry.get("suggestedRank")),
                        nvl(asString(entry.get("reason")))));
            }
        }
        if (responded.isEmpty()) {
            throw modelFailed("模型未产出计划内用例的顺序建议");
        }
        responded.sort(Comparator.comparingInt(Ranked::rank));

        List<TestPlanNodeSnapshot> ordered = responded.stream().map(Ranked::node).collect(Collectors.toList());
        Set<UUID> covered = seen;
        currentOrder.forEach(node -> {
            if (!covered.contains(node.getId())) {
                ordered.add(node);
            }
        });

        Map<UUID, String> reasons = responded.stream().collect(Collectors.toMap(
                ranked -> ranked.node().getId(), Ranked::reason, (left, right) -> left));
        List<Map<String, Object>> items = new ArrayList<>(ordered.size());
        List<String> afterOrder = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            TestPlanNodeSnapshot node = ordered.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", String.valueOf(node.getId()));
            item.put("caseTitle", nvl(node.getTitle()));
            item.put("suggestedRank", i + 1);
            item.put("reason", nvl(reasons.get(node.getId())));
            items.add(item);
            afterOrder.add(nvl(node.getTitle()));
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("planId", String.valueOf(plan.getId()));
        content.put("items", items);
        content.put("beforeOrder", currentOrder.stream()
                .map(node -> nvl(node.getTitle())).toList());
        content.put("afterOrder", afterOrder);

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("key", "order-1");
        artifact.put("kind", Constants.AiArtifactKind.ORDER_SUGGESTION);
        artifact.put("parentKey", null);
        artifact.put("title", "执行顺序建议（" + items.size() + " 项）");
        artifact.put("content", content);
        artifact.put("confirmStatus", "pending");
        return artifact;
    }

    private static int parseRank(Object raw) {
        if (raw == null) {
            return Integer.MAX_VALUE;
        }
        try {
            int rank = Integer.parseInt(String.valueOf(raw).trim());
            return rank > 0 ? rank : Integer.MAX_VALUE;
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static UUID requirePlanId(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("planId");
        if (raw == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /** round 仅透传提示词（详设 3.4 可选入参）：畸形或非正整数按输入非法处理 */
    private static String parseRound(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("round");
        if (raw == null) {
            return null;
        }
        try {
            int round = Integer.parseInt(String.valueOf(raw).trim());
            if (round <= 0) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
            }
            return String.valueOf(round);
        } catch (NumberFormatException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }
}
