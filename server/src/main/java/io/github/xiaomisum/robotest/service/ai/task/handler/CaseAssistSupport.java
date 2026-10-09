package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.abbreviate;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parseUuid;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 辅助功能（用例补全 / 级别推荐）共用支撑（辅助功能详设 3.2、3.3、4.1）：
 * 输入解析与归属校验、上游需求回溯、提示词上下文装配。
 * 纯计算方法为静态，仅需落库查询的 resolve / upstreamRequirements 走实例。
 */
@Component
public class CaseAssistSupport {

    /** 单次选中节点上限（详设 4.1，超限提示分批执行） */
    private static final int MAX_NODE_IDS = 50;
    /** 需求描述进入提示词的截断长度 */
    private static final int MAX_REQUIREMENT_DESC = 300;
    /** 来源引用条数与摘录截断（详设 3.2 sourceRefs 上限） */
    private static final int MAX_SOURCE_REFS = 5;
    private static final int MAX_QUOTE = 300;
    /** 祖先路径回溯护栏：父链异常时不至于死循环 */
    private static final int MAX_ANCESTOR_DEPTH = 32;

    private static final Comparator<TestCaseNode> BY_ORDER = Comparator.comparing(
            (TestCaseNode node) -> node.getSortOrder() == null ? Integer.MAX_VALUE : node.getSortOrder());

    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private TraceEdgeMapper traceEdgeMapper;

    /** 解析结果：选中文档 + 选中用例节点（保序去重） + 文档全量节点索引 */
    public record ResolvedCases(TestCaseDocument document, List<TestCaseNode> nodes,
                                Map<UUID, TestCaseNode> nodesById,
                                Map<UUID, List<TestCaseNode>> childrenByParent) {
    }

    /** 用例现有属性：precondition 取首个，steps / expected 按 sortOrder 归类，tags 取节点列 */
    public record ExistingFields(String precondition, List<String> steps, List<String> expected,
                                 List<String> tags) {
    }

    // ---------- 输入解析（静态：提交校验与执行期共用） ----------

    /** documentId 必填且须为合法 uuid，否则按输入非法处理（1000018115） */
    public static UUID requireDocumentId(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("documentId");
        if (raw == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /** nodeIds 未选中（缺 / 空 / 超上限）报 1000018302，形态畸形（非数组 / 元素非 uuid）报 1000018115 */
    public static List<UUID> requireNodeIds(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("nodeIds");
        if (raw == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_INPUT_INVALID);
        }
        if (!(raw instanceof List<?> list)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        // 空选与超限都属「未选中 / 超量」，提示分批执行（详设 4.1）
        if (list.isEmpty() || list.size() > MAX_NODE_IDS) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_INPUT_INVALID);
        }
        Set<UUID> deduped = new LinkedHashSet<>();
        for (Object element : list) {
            try {
                deduped.add(UUID.fromString(String.valueOf(element).trim()));
            } catch (IllegalArgumentException e) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
            }
        }
        return List.copyOf(deduped);
    }

    /** scope.requirementIds 可选；显式给出时形态必须合法，否则按输入非法处理 */
    public static List<UUID> scopeRequirementIds(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("scope");
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof Map<?, ?> scope)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        Object ids = scope.get("requirementIds");
        if (ids == null) {
            return List.of();
        }
        if (!(ids instanceof List<?> list)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        List<UUID> parsed = new ArrayList<>(list.size());
        for (Object element : list) {
            try {
                parsed.add(UUID.fromString(String.valueOf(element).trim()));
            } catch (IllegalArgumentException e) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
            }
        }
        return List.copyOf(parsed);
    }

    /** 用例优先级落库为 P0–P3，提示词与产物统一展示为 high / medium / low */
    public static String displayPriority(String priority) {
        if (priority == null) {
            return null;
        }
        return switch (priority) {
            case "P0" -> "high";
            case "P1" -> "medium";
            case "P2", "P3" -> "low";
            default -> null;
        };
    }

    // ---------- 归属校验与需求回溯（需查库） ----------

    /**
     * 解析选中范围：文档须属当前项目，节点须属该文档（1000018301）且为用例节点（1000018307）。
     * 文档全量节点一次载入，供属性子节点与祖先路径复用。
     */
    public ResolvedCases resolve(UUID projectId, UUID documentId, List<UUID> nodeIds) {
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        if (document == null || !projectId.equals(document.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_NODE_NOT_FOUND);
        }
        List<TestCaseNode> documentNodes = testCaseNodeMapper.listByDocumentId(documentId);
        Map<UUID, TestCaseNode> nodesById = documentNodes.stream()
                .collect(Collectors.toMap(TestCaseNode::getId, node -> node, (left, right) -> left));
        Map<UUID, List<TestCaseNode>> childrenByParent = documentNodes.stream()
                .filter(node -> node.getParentId() != null)
                .collect(Collectors.groupingBy(TestCaseNode::getParentId));

        List<TestCaseNode> nodes = new ArrayList<>(nodeIds.size());
        for (UUID nodeId : nodeIds) {
            TestCaseNode node = nodesById.get(nodeId);
            if (node == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_NODE_NOT_FOUND);
            }
            if (!Constants.NodeType.CASE.equals(node.getType())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_NODE_TYPE_UNSUPPORTED);
            }
            nodes.add(node);
        }
        return new ResolvedCases(document, List.copyOf(nodes), nodesById, childrenByParent);
    }

    /**
     * 上游需求：显式 scope 逐条归属校验（异项目 / 不存在按输入非法 1000018115）；
     * 缺省按选中节点的 derivation 入边回溯，detached 边不参与（listActiveByTarget 口径）。
     */
    public List<Requirement> upstreamRequirements(UUID projectId, List<UUID> nodeIds,
                                                  List<UUID> scopeRequirementIds) {
        if (!scopeRequirementIds.isEmpty()) {
            Map<UUID, Requirement> found = requirementMapper.listByIds(scopeRequirementIds).stream()
                    .collect(Collectors.toMap(Requirement::getId, item -> item, (left, right) -> left));
            List<Requirement> scoped = new ArrayList<>(scopeRequirementIds.size());
            for (UUID id : scopeRequirementIds) {
                Requirement item = found.get(id);
                if (item == null || !projectId.equals(item.getProjectId())) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
                }
                scoped.add(item);
            }
            return List.copyOf(scoped);
        }
        Set<UUID> requirementIds = traceEdgeMapper.listActiveByTarget(Constants.TraceNodeType.TEST_CASE, nodeIds)
                .stream()
                .filter(edge -> Constants.TraceEdgeType.DERIVATION.equals(edge.getEdgeType())
                        && Constants.TraceNodeType.REQUIREMENT.equals(edge.getSourceType()))
                .map(TraceEdge::getSourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requirementIds.isEmpty()) {
            return List.of();
        }
        return requirementMapper.listByIds(requirementIds).stream()
                .filter(item -> projectId.equals(item.getProjectId()))
                .toList();
    }

    // ---------- 提示词上下文（纯计算，静态） ----------

    /** 文档层级：每条选中用例给出祖先路径，供模型判断所属业务流 */
    public static String documentContext(ResolvedCases resolved) {
        StringBuilder builder = new StringBuilder("选中用例路径：\n");
        for (TestCaseNode node : resolved.nodes()) {
            builder.append("- ").append(nvl(node.getTitle()))
                    .append("（").append(ancestorPath(node, resolved.nodesById())).append("）\n");
        }
        return builder.toString();
    }

    /** 上游需求：[id] 编号 | 标题 | 优先级，描述截断 300 字 */
    public static String requirementContext(List<Requirement> requirements) {
        if (requirements.isEmpty()) {
            return "（无上游需求）";
        }
        StringBuilder builder = new StringBuilder("上游需求（共 ").append(requirements.size()).append(" 条）：\n");
        for (Requirement item : requirements) {
            builder.append("- [").append(item.getId()).append("] ")
                    .append(nvl(item.getCode())).append(" | ").append(nvl(item.getTitle()))
                    .append(" | ").append(nvl(item.getPriority())).append('\n');
            String description = trimToNull(item.getDescription());
            if (description != null) {
                builder.append("  摘要：").append(abbreviate(description, MAX_REQUIREMENT_DESC)).append('\n');
            }
        }
        return builder.toString();
    }

    /** 补全上下文：选中用例的现有属性清单，空缺显式写"（空）"提示模型可补 */
    public static String completeCaseContext(ResolvedCases resolved) {
        StringBuilder builder = new StringBuilder("选中用例（共 ")
                .append(resolved.nodes().size()).append(" 条）：\n");
        for (TestCaseNode node : resolved.nodes()) {
            ExistingFields fields = existingFields(node, resolved);
            builder.append("- [").append(node.getId()).append("] ").append(nvl(node.getTitle()))
                    .append(" | 优先级 ").append(nvl(displayPriority(node.getPriority()))).append('\n');
            builder.append("    前置条件：").append(nvl(fields.precondition())).append('\n');
            builder.append("    步骤：").append(joinOrEmpty(fields.steps())).append('\n');
            builder.append("    预期：").append(joinOrEmpty(fields.expected())).append('\n');
            builder.append("    标签：").append(joinOrEmpty(fields.tags())).append('\n');
        }
        return builder.toString();
    }

    /** 级别推荐上下文：仅需用例标识与当前级别 */
    public static String priorityCaseContext(ResolvedCases resolved) {
        StringBuilder builder = new StringBuilder("选中用例（共 ")
                .append(resolved.nodes().size()).append(" 条）：\n");
        for (TestCaseNode node : resolved.nodes()) {
            builder.append("- [").append(node.getId()).append("] ").append(nvl(node.getTitle()))
                    .append(" | 当前级别 ").append(nvl(displayPriority(node.getPriority())))
                    .append(" | 路径 ").append(ancestorPath(node, resolved.nodesById())).append('\n');
        }
        return builder.toString();
    }

    /** 现有属性：属性子节点按 type 归类、组内按 sortOrder 稳定排序；tags 取节点列 */
    public static ExistingFields existingFields(TestCaseNode node, ResolvedCases resolved) {
        List<TestCaseNode> children = resolved.childrenByParent().getOrDefault(node.getId(), List.of());
        String precondition = children.stream()
                .filter(child -> Constants.NodeType.PRECONDITION.equals(child.getType()))
                .sorted(BY_ORDER)
                .map(TestCaseNode::getTitle)
                .findFirst()
                .orElse(null);
        return new ExistingFields(precondition,
                childrenOfType(children, Constants.NodeType.STEP),
                childrenOfType(children, Constants.NodeType.EXPECTED),
                node.getTags() == null ? List.of() : List.copyOf(node.getTags()));
    }

    private static List<String> childrenOfType(List<TestCaseNode> children, String type) {
        return children.stream()
                .filter(child -> type.equals(child.getType()))
                .sorted(BY_ORDER)
                .map(TestCaseNode::getTitle)
                .toList();
    }

    private static String ancestorPath(TestCaseNode node, Map<UUID, TestCaseNode> nodesById) {
        List<String> titles = new ArrayList<>();
        UUID parentId = node.getParentId();
        int depth = 0;
        while (parentId != null && depth++ < MAX_ANCESTOR_DEPTH) {
            TestCaseNode parent = nodesById.get(parentId);
            if (parent == null) {
                break;
            }
            titles.add(0, nvl(parent.getTitle()));
            parentId = parent.getParentId();
        }
        return titles.isEmpty() ? "（文档根）" : String.join(" › ", titles);
    }

    private static String joinOrEmpty(Collection<String> values) {
        return values.isEmpty() ? "（空）" : String.join("；", values);
    }

    /**
     * 来源引用清洗：只保留上游需求范围内的引用，需求编号作 title；补全带 quote，级别推荐不带（详设 3.2 / 3.3）。
     */
    public static List<Map<String, Object>> sourceRefsOf(Map<String, Object> suggestion,
            Map<UUID, Requirement> requirementById, boolean withQuote) {
        if (suggestion == null) {
            return List.of();
        }
        List<Map<String, Object>> sourceRefs = new ArrayList<>();
        if (suggestion.get("sourceRefs") instanceof List<?> raw) {
            for (Object element : raw) {
                if (sourceRefs.size() >= MAX_SOURCE_REFS) {
                    break;
                }
                Map<String, Object> item = asMap(element);
                UUID requirementId = parseUuid(item.get("requirementId"));
                Requirement requirement = requirementId == null ? null : requirementById.get(requirementId);
                if (requirement == null) {
                    // 范围外需求不入产物
                    continue;
                }
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("type", "requirement");
                ref.put("id", String.valueOf(requirementId));
                ref.put("title", nvl(requirement.getCode()));
                if (withQuote) {
                    ref.put("quote", abbreviate(nvl(asString(item.get("quote"))), MAX_QUOTE));
                }
                sourceRefs.add(ref);
            }
        }
        return sourceRefs;
    }
}
