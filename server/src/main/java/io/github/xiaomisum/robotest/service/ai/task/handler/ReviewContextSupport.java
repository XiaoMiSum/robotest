package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * AI 评审上下文支撑（详设 4.2）：提交期冻结需求快照、执行期装配用例与需求上下文、
 * 提案清洗。均为无副作用静态方法，提交与执行读同一份口径。
 */
public final class ReviewContextSupport {

    private static final int MAX_DESCRIPTION_CHARS = 3000;
    private static final int MAX_ISSUES = 10;
    private static final int MAX_SOURCE_REFS = 5;
    private static final int MAX_QUOTE = 300;
    private static final int MAX_ANCESTOR_DEPTH = 32;
    private static final Set<String> MARKS = Set.of(Constants.ReviewMark.PASS, Constants.ReviewMark.FAIL);
    private static final Set<String> SEVERITIES = Set.of("high", "medium", "low");
    private static final List<String> SOURCE_ID_FIELDS = List.of(
            "id", "requirementId", "code", "requirementCode", "requirementNo", "title", "requirementTitle");

    private static final Comparator<TestReviewNodeSnapshot> BY_ORDER = Comparator.comparing(
            node -> node.getSortOrder() == null ? Integer.MAX_VALUE : node.getSortOrder());

    private ReviewContextSupport() {
    }

    /** 提交期冻结关联需求正文；执行期只读快照，需求后续变更不改变本次评审 */
    public static Map<String, Object> snapshotOf(List<Requirement> requirements) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Requirement requirement : requirements == null ? List.<Requirement>of() : requirements) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(requirement.getId()));
            item.put("code", nvl(requirement.getCode()));
            item.put("title", nvl(requirement.getTitle()));
            item.put("content", nvl(requirement.getDescription()));
            items.add(item);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requirements", items);
        return snapshot;
    }

    /** 需求上下文（基于快照：唯一可信 id + 编号 + 标题 + 冻结正文） */
    public static String requirementContext(Map<String, Object> snapshot) {
        List<Map<String, Object>> items = requirementItems(snapshot);
        if (items.isEmpty()) {
            return "（无关联需求）";
        }
        StringBuilder builder = new StringBuilder("【来源需求白名单】sourceRefs.id 只能逐字复制下方 id= 后的 UUID；")
                .append("编号与标题仅供人工核对，禁止填入 id。sourceRefs.quote 取「内容」原文摘录：\n");
        for (Map<String, Object> item : items) {
            builder.append("- [id=").append(nvl(asString(item.get("id")))).append("] code=")
                    .append(nvl(asString(item.get("code")))).append(" | title=")
                    .append(nvl(asString(item.get("title")))).append('\n');
            String content = trimToNull(asString(item.get("content")));
            if (content != null) {
                builder.append("  内容：").append(abbreviate(content, MAX_DESCRIPTION_CHARS)).append('\n');
            }
        }
        return builder.toString();
    }

    /** 关联用例上下文：标题、优先级、前置/步骤/预期与祖先路径，缺失维度显式留空 */
    public static String reviewCaseContext(List<TestReviewNodeSnapshot> associated,
            List<TestReviewNodeSnapshot> allNodes) {
        List<TestReviewNodeSnapshot> cases = associated == null ? List.of() : associated;
        List<TestReviewNodeSnapshot> all = allNodes == null ? List.of() : allNodes;
        Map<UUID, TestReviewNodeSnapshot> byId = all.stream()
                .collect(Collectors.toMap(TestReviewNodeSnapshot::getId, node -> node, (left, right) -> left));
        Map<UUID, List<TestReviewNodeSnapshot>> childrenByParent = all.stream()
                .filter(node -> node.getParentId() != null)
                .collect(Collectors.groupingBy(TestReviewNodeSnapshot::getParentId));

        StringBuilder builder = new StringBuilder("关联规划用例（共 ")
                .append(cases.size()).append(" 条，snapshotNodeId 必须取下方 id）：\n");
        for (TestReviewNodeSnapshot node : cases) {
            builder.append("- [").append(node.getId()).append("] ").append(nvl(node.getTitle()))
                    .append(" | 优先级 ").append(nvl(node.getPriority()))
                    .append(" | 路径 ").append(ancestorPath(node, byId)).append('\n');
            List<TestReviewNodeSnapshot> children = childrenByParent.getOrDefault(node.getId(), List.of());
            builder.append("    前置条件：").append(joinOrEmpty(childrenOfType(children, Constants.NodeType.PRECONDITION)))
                    .append('\n');
            builder.append("    步骤：").append(joinOrEmpty(childrenOfType(children, Constants.NodeType.STEP)))
                    .append('\n');
            builder.append("    预期：").append(joinOrEmpty(childrenOfType(children, Constants.NodeType.EXPECTED)))
                    .append('\n');
        }
        return builder.toString();
    }

    /** RAG 片段装配：由调用方检索，未配置 / 重建中由调用方降级为空串，不阻断评审 */
    public static String ragContext(List<AiVectorSearchHitRespDTO> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        List<String> lines = hits.stream()
                .map(hit -> abbreviate(nvl(asString(hit.getContent())).replace('\n', ' '), MAX_QUOTE))
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();
        if (lines.isEmpty()) {
            return "";
        }
        return "\n相关需求片段（向量检索命中）：\n" + String.join("\n", lines);
    }

    /** 清洗结果：失败时携带原因与首个非法取值，供执行轨迹与日志定位模型输出问题。 */
    public record SanitizeOutcome(Map<String, Object> content, String dropReason, String sample) {
        public boolean accepted() {
            return content != null;
        }

        public static SanitizeOutcome accepted(Map<String, Object> content) {
            return new SanitizeOutcome(content, null, null);
        }

        public static SanitizeOutcome rejected(String dropReason) {
            return new SanitizeOutcome(null, dropReason, null);
        }

        public static SanitizeOutcome rejected(String dropReason, String sample) {
            return new SanitizeOutcome(null, dropReason, sample);
        }
    }

    /**
     * 提案内容清洗：非法节点 / 非法标记 / 不满足约束的 issues / 无有效来源的提案返回拒绝原因，
     * 由调用方丢弃（详设 4.2：非法提案不进入待采纳态）。
     */
    public static SanitizeOutcome sanitizeContentWithReason(Map<String, Object> raw, UUID reviewId,
            Map<UUID, TestReviewNodeSnapshot> associatedById,
            Map<UUID, Map<String, Object>> requirementsById) {
        Map<String, Object> source = asMap(raw);
        UUID nodeId = parseUuid(source.get("snapshotNodeId"));
        TestReviewNodeSnapshot node = nodeId == null ? null : associatedById.get(nodeId);
        if (node == null) {
            return SanitizeOutcome.rejected("invalid_node");
        }
        String mark = trimToNull(asString(source.get("suggestedMark")));
        if (mark == null || !MARKS.contains(mark)) {
            return SanitizeOutcome.rejected("invalid_mark");
        }
        List<Map<String, Object>> issues = sanitizeIssues(source.get("issues"), mark);
        if (issues == null) {
            return SanitizeOutcome.rejected("invalid_issues");
        }
        List<Map<String, Object>> sourceRefs = sanitizeSourceRefs(source.get("sourceRefs"), requirementsById);
        if (sourceRefs.isEmpty()) {
            return SanitizeOutcome.rejected("missing_source_refs",
                    firstSourceCandidate(source.get("sourceRefs")));
        }

        String caseTitle = trimToNull(asString(source.get("caseTitle")));
        String reason = asString(source.get("reason"));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("reviewId", String.valueOf(reviewId));
        content.put("snapshotNodeId", nodeId.toString());
        content.put("caseTitle", caseTitle == null ? nvl(node.getTitle()) : caseTitle);
        content.put("suggestedMark", mark);
        content.put("reason", reason == null ? "" : reason);
        content.put("issues", issues);
        content.put("sourceRefs", sourceRefs);
        return SanitizeOutcome.accepted(content);
    }

    /**
     * 提案内容清洗：非法节点 / 非法标记 / 不满足约束的 issues / 无有效来源的提案返回 null，
     * 由调用方丢弃（详设 4.2：非法提案不进入待采纳态）。
     */
    public static Map<String, Object> sanitizeContent(Map<String, Object> raw, UUID reviewId,
            Map<UUID, TestReviewNodeSnapshot> associatedById,
            Map<UUID, Map<String, Object>> requirementsById) {
        return sanitizeContentWithReason(raw, reviewId, associatedById, requirementsById).content();
    }

    /** 快照 requirements 列表转 id 索引，供提案来源引用按冻结需求校验 */
    @SuppressWarnings("unchecked")
    public static Map<UUID, Map<String, Object>> snapshotRequirementsById(Map<String, Object> snapshot) {
        Map<UUID, Map<String, Object>> byId = new LinkedHashMap<>();
        if (snapshot == null || !(snapshot.get("requirements") instanceof List<?> items)) {
            return byId;
        }
        for (Object element : items) {
            Map<String, Object> item = element instanceof Map<?, ?>
                    ? new LinkedHashMap<>((Map<String, Object>) element) : Map.of();
            UUID id = parseUuid(item.get("id"));
            if (id != null) {
                byId.put(id, item);
            }
        }
        return byId;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> requirementItems(Map<String, Object> snapshot) {
        if (snapshot == null || !(snapshot.get("requirements") instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream()
                .filter(element -> element instanceof Map)
                .map(element -> (Map<String, Object>) element)
                .toList();
    }

    /** fail 必须至少一项有效问题；畸形形态按非法处理，单项字段缺失留空 */
    private static List<Map<String, Object>> sanitizeIssues(Object raw, String mark) {
        if (!(raw instanceof List<?> items)) {
            return Constants.ReviewMark.FAIL.equals(mark) ? null : new ArrayList<>();
        }
        List<Map<String, Object>> issues = new ArrayList<>();
        for (Object element : items) {
            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            Map<String, Object> item = asMap(element);
            String severity = trimToNull(asString(item.get("severity")));
            if (severity == null || !SEVERITIES.contains(severity)) {
                continue;
            }
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("severity", severity);
            issue.put("category", nvl(asString(item.get("category"))));
            issue.put("description", nvl(asString(item.get("description"))));
            issue.put("path", nvl(asString(item.get("path"))));
            issues.add(issue);
        }
        if (Constants.ReviewMark.FAIL.equals(mark) && issues.isEmpty()) {
            return null;
        }
        return issues;
    }

    /** 来源引用按冻结快照的 id / code / title 归一化，越界引用一并丢弃；无来源的提案由调用方丢弃 */
    private static List<Map<String, Object>> sanitizeSourceRefs(Object raw,
            Map<UUID, Map<String, Object>> requirementsById) {
        if (!(raw instanceof List<?> items)) {
            return List.of();
        }
        Map<String, Map<String, Object>> byAlias = sourceAliasIndex(requirementsById);
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Object element : items) {
            if (refs.size() >= MAX_SOURCE_REFS) {
                break;
            }
            Map<String, Object> item = asMap(element);
            Map<String, Object> requirement = resolveRequirement(item, requirementsById, byAlias);
            if (requirement == null) {
                continue;
            }
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", "requirement");
            ref.put("id", nvl(asString(requirement.get("id"))));
            ref.put("title", nvl(asString(requirement.get("title"))));
            ref.put("code", nvl(asString(requirement.get("code"))));
            ref.put("quote", abbreviate(nvl(asString(item.get("quote"))), MAX_QUOTE));
            refs.add(ref);
        }
        return refs;
    }

    /** 冻结快照的 UUID / 编号 / 标题统一建小写索引，兼容模型把 code / title 填进 id 的常见偏移 */
    private static Map<String, Map<String, Object>> sourceAliasIndex(
            Map<UUID, Map<String, Object>> requirementsById) {
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Object>> entry : requirementsById.entrySet()) {
            putAlias(index, entry.getKey().toString(), entry.getValue());
            putAlias(index, entry.getKey().toString().replace("-", ""), entry.getValue());
            putAlias(index, asString(entry.getValue().get("code")), entry.getValue());
            putAlias(index, asString(entry.getValue().get("title")), entry.getValue());
        }
        return index;
    }

    private static void putAlias(Map<String, Map<String, Object>> index, String alias,
            Map<String, Object> requirement) {
        String key = trimToNull(alias);
        if (key != null) {
            index.putIfAbsent(key.toLowerCase(Locale.ROOT), requirement);
        }
    }

    /** 优先按 UUID 精确命中，其次按编号 / 标题回查；任一命中都必须落在本次冻结快照内 */
    private static Map<String, Object> resolveRequirement(Map<String, Object> item,
            Map<UUID, Map<String, Object>> requirementsById,
            Map<String, Map<String, Object>> byAlias) {
        for (String field : SOURCE_ID_FIELDS) {
            String candidate = trimToNull(asString(item.get(field)));
            if (candidate == null) {
                continue;
            }
            UUID id = parseUuid(candidate);
            if (id != null && requirementsById.containsKey(id)) {
                return requirementsById.get(id);
            }
            Map<String, Object> matched = byAlias.get(candidate.toLowerCase(Locale.ROOT));
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    /** 首条来源引用的首个非空候选值，供全量丢弃时的错误提示定位模型实际写了什么 */
    private static String firstSourceCandidate(Object raw) {
        if (!(raw instanceof List<?> items)) {
            return null;
        }
        for (Object element : items) {
            Map<String, Object> item = asMap(element);
            for (String field : SOURCE_ID_FIELDS) {
                String candidate = trimToNull(asString(item.get(field)));
                if (candidate != null) {
                    return abbreviate(candidate, 80);
                }
            }
        }
        return null;
    }

    private static List<String> childrenOfType(Collection<TestReviewNodeSnapshot> children, String type) {
        return children.stream()
                .filter(child -> type.equals(child.getType()))
                .sorted(BY_ORDER)
                .map(TestReviewNodeSnapshot::getTitle)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static String ancestorPath(TestReviewNodeSnapshot node, Map<UUID, TestReviewNodeSnapshot> byId) {
        List<String> titles = new ArrayList<>();
        UUID parentId = node.getParentId();
        int depth = 0;
        while (parentId != null && depth++ < MAX_ANCESTOR_DEPTH) {
            TestReviewNodeSnapshot parent = byId.get(parentId);
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
}
