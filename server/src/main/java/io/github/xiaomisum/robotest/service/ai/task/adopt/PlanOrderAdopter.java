package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanNodeSnapshotMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ArtifactAdopter;
import io.github.xiaomisum.robotest.service.ai.task.handler.PlanOrderHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.contentOf;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parseRank;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parseUuid;

/**
 * 执行顺序建议承接（详设 3.4 落库 / 4.4 顺序推荐的边界）：
 * 采纳只重排快照 sortOrder，不改快照内容、不改关联集合、不持久复用建议结果。
 */
@Service
public class PlanOrderAdopter implements ArtifactAdopter {

    /** 执行顺序建议要求计划执行权（详设 3.6.1「落库承接」列） */
    private static final String PERMISSION = "plan:execute";

    /** 根节点分组键，与计划树读取口径（Constants.Tree.ROOT_KEY）一致 */
    private static final String ROOT_KEY = Constants.Tree.ROOT_KEY;

    @Resource
    private AssistAdoptSupport support;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private TestPlanNodeSnapshotMapper testPlanNodeSnapshotMapper;

    @Override
    public String type() {
        return PlanOrderHandler.TYPE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        if (Constants.AiArtifactAction.REJECTED.equals(context.action())) {
            return null;
        }
        support.requirePermission(context.loginUser(), PERMISSION);
        UUID planId = requirePlanId(context);
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null || !context.projectId().equals(plan.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_PLAN_NOT_FOUND);
        }
        if (context.round() != null && context.round() <= 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        Map<UUID, TestPlanNodeSnapshot> associated = associatedById(planId);
        if (associated.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_PLAN_CASES_EMPTY);
        }

        Map<UUID, Integer> ranks = suggestedRanks(context, associated);
        boolean touched = reorder(planId, associated, ranks);
        if (touched) {
            support.record(context, "TEST_PLAN", planId, nvl(plan.getName()), "PLAN_UPDATED",
                    "应用 AI 执行顺序建议（" + ranks.size() + " 项）");
        }

        Map<String, Object> adoptedRef = new LinkedHashMap<>();
        adoptedRef.put("planId", String.valueOf(planId));
        adoptedRef.put("appliedOrder", ranks.keySet().stream().map(UUID::toString).toList());
        return new AdoptOutcome(null, adoptedRef);
    }

    private Map<UUID, TestPlanNodeSnapshot> associatedById(UUID planId) {
        return testPlanNodeSnapshotMapper.listAssociatedByPlanId(planId, Constants.NodeType.CASE).stream()
                .collect(Collectors.toMap(TestPlanNodeSnapshot::getId, node -> node, (left, right) -> left,
                        LinkedHashMap::new));
    }

    /**
     * 建议名次：adopted_edited 以拖拽后的编辑载荷为准，否则取产物；解析失败或清单外用例视为畸形输入
     * （1000018115）——采纳是人工确认动作，静默丢弃会让用户选中的顺序被部分忽略。
     */
    private static Map<UUID, Integer> suggestedRanks(AdoptContext context,
            Map<UUID, TestPlanNodeSnapshot> associated) {
        List<Map<String, Object>> items;
        if (Constants.AiArtifactAction.ADOPTED_EDITED.equals(context.action())
                && context.content() != null && context.content().get("items") instanceof List<?>) {
            items = maps(context.content().get("items"));
        } else {
            items = maps(contentOf(context.artifact()).get("items"));
        }
        if (items.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        Map<UUID, Integer> ranks = new LinkedHashMap<>();
        items.stream()
                .sorted(Comparator.comparingInt(item -> parseRank(item.get("suggestedRank"))))
                .forEach(item -> {
                    UUID nodeId = parseUuid(item.get("nodeId"));
                    if (nodeId == null || !associated.containsKey(nodeId)) {
                        throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
                    }
                    ranks.putIfAbsent(nodeId, ranks.size());
                });
        return ranks;
    }

    /**
     * 逐父节点重排同级子节点：非关联子节点（结构容器 / 用例属性）保持原位置不动，
     * 关联用例在它们腾出的槽位上按建议名次重排；未被清单覆盖的用例排在本父级关联槽位之尾
     * （与产物侧「未响应补尾」同口径）。树形结构下跨父级顺序由祖先决定，采纳无法也不必改写。
     */
    private boolean reorder(UUID planId, Map<UUID, TestPlanNodeSnapshot> associated, Map<UUID, Integer> ranks) {
        List<TestPlanNodeSnapshot> all = testPlanNodeSnapshotMapper.listByPlanId(planId);
        Map<UUID, TestPlanNodeSnapshot> byId = all.stream()
                .collect(Collectors.toMap(TestPlanNodeSnapshot::getId, node -> node, (left, right) -> left));
        Set<UUID> kept = keptIds(associated.keySet(), byId);
        Map<String, List<TestPlanNodeSnapshot>> byParent = all.stream()
                .filter(node -> kept.contains(node.getId()))
                .collect(Collectors.groupingBy(
                        node -> node.getParentId() != null ? node.getParentId().toString() : ROOT_KEY));

        Comparator<TestPlanNodeSnapshot> byCurrentOrder = Comparator.comparingInt(node -> {
            Integer order = node.getSortOrder();
            return order == null ? Integer.MAX_VALUE : order;
        });
        boolean touched = false;
        for (List<TestPlanNodeSnapshot> siblings : byParent.values()) {
            List<TestPlanNodeSnapshot> current = new ArrayList<>(siblings);
            current.sort(byCurrentOrder);
            List<TestPlanNodeSnapshot> merged = new ArrayList<>(current.size());
            List<TestPlanNodeSnapshot> cases = current.stream()
                    .filter(node -> associated.containsKey(node.getId()))
                    .sorted(Comparator.comparingInt(node -> ranks.getOrDefault(node.getId(), Integer.MAX_VALUE)))
                    .toList();
            int next = 0;
            for (TestPlanNodeSnapshot sibling : current) {
                // 槽位 = 该父级下关联用例原先占用的位置，非关联子节点原位不动
                merged.add(associated.containsKey(sibling.getId()) ? cases.get(next++) : sibling);
            }
            for (int i = 0; i < merged.size(); i++) {
                TestPlanNodeSnapshot node = merged.get(i);
                if (!Objects.equals(node.getSortOrder(), i)) {
                    testPlanNodeSnapshotMapper.updateSortOrder(node.getId(), i);
                    touched = true;
                }
            }
        }
        return touched;
    }

    /** 与计划树读取口径一致（pruneSnapshotTree）：关联用例 + 其祖先 + 其后代 */
    private static Set<UUID> keptIds(Set<UUID> associatedIds, Map<UUID, TestPlanNodeSnapshot> byId) {
        Set<UUID> kept = new HashSet<>(associatedIds);
        for (UUID id : associatedIds) {
            UUID parentId = byId.get(id) == null ? null : byId.get(id).getParentId();
            while (parentId != null && kept.add(parentId)) {
                parentId = byId.get(parentId) == null ? null : byId.get(parentId).getParentId();
            }
            collectDescendants(id, byId, kept);
        }
        return kept;
    }

    private static void collectDescendants(UUID nodeId, Map<UUID, TestPlanNodeSnapshot> byId, Set<UUID> kept) {
        for (TestPlanNodeSnapshot node : byId.values()) {
            if (nodeId.equals(node.getParentId()) && kept.add(node.getId())) {
                collectDescendants(node.getId(), byId, kept);
            }
        }
    }

    /** 承接计划：target.planId 显式优先，缺省回退任务入参（任务详情页发起不带 target） */
    private static UUID requirePlanId(AdoptContext context) {
        if (context.targetPlanId() != null) {
            return context.targetPlanId();
        }
        UUID planId = parseUuid(AssistAdoptSupport.taskInput(context).get("planId"));
        if (planId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        return planId;
    }

    private static List<Map<String, Object>> maps(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(element -> asMap(element)).toList();
    }
}
