package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewCheckBatchDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewCheckItemDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewModuleSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewNodeSnapshotMapper;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * review_check 任务处理器（US-AI-005）：加载快照 case 节点 → 按 token 预算/数量切批 →
 * 逐批结构化调用 LLM → 幻觉过滤 → 累计写入 result/进度（每批心跳 + 协作式取消，基础设施 4.6）。
 */
@Component
public class AiReviewCheckTaskHandler implements AiTaskHandler {

    public static final String TYPE = Constants.AiTaskType.REVIEW_CHECK;

    /** 单批用例数上限（详细设计 4.1） */
    static final int BATCH_CASE_LIMIT = 30;
    /** 单批输入预算（token）：低于全局预算为 system 段与输出留余量 */
    static final int BATCH_TOKEN_BUDGET = 12_000;
    /** 单批 LLM 失败重试次数（重试后仍失败跳过该批并计数，4.1） */
    static final int BATCH_RETRY_TIMES = 1;
    /** 检查维度白名单（2.2.1 枚举，extraAssertion 校验用） */
    static final Set<String> DIMENSIONS = Set.of(
            "missing_precondition", "vague_step", "missing_expected", "priority_conflict");
    /** 相似标题判定最小公共子串长度（4.1 同批相似分组，供优先级冲突判断） */
    static final int SIMILAR_MIN_COMMON = 4;

    private static final String TASK_INSTRUCTION = """
            请检查给定批次测试用例的完整性，为每处发现的问题输出一条建议。检查维度：\
            missing_precondition（缺少前置条件）、vague_step（步骤描述笼统无法执行）、\
            missing_expected（缺少预期结果）、priority_conflict（【同批相似用例】分组内优先级冲突）。\
            snapshotNodeId 必须引用本批输入中列出的用例，不得虚构；没有可判定问题的用例不要输出建议。""";

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private AiAnalysisTaskMapper aiTaskMapper;
    @Resource
    private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    @Resource
    private TestReviewModuleSnapshotMapper reviewModuleSnapshotMapper;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, Object> execute(AiAnalysisTask task) {
        UUID reviewId = task.getTargetId();
        List<TestReviewNodeSnapshot> allNodes = reviewNodeSnapshotMapper.listByReviewId(reviewId);
        Map<UUID, String> docNameById = reviewModuleSnapshotMapper.listByReviewId(reviewId).stream()
                .filter(m -> Constants.ModuleType.DOCUMENT.equals(m.getType()))
                .collect(Collectors.toMap(TestReviewModuleSnapshot::getId, TestReviewModuleSnapshot::getName,
                        (a, b) -> a));

        List<CaseContext> cases = collectCases(allNodes, docNameById);
        int total = cases.size();
        List<List<CaseContext>> batches = splitBatches(cases);

        List<AiReviewCheckItemDTO> items = new ArrayList<>();
        int skippedBatches = 0;
        int processed = 0;
        for (List<CaseContext> batch : batches) {
            // 批次边界心跳：写入进度与累计结果；影响行数为 0 表示任务已被取消/置失败，立即中止返回部分结果
            Map<String, Object> partial = buildResult(processed, total, skippedBatches, items);
            if (AiTaskProgressSupport.heartbeat(aiTaskMapper, task.getId(),
                    AiTaskProgressSupport.percent(processed, total),
                    JsonUtils.toJsonString(partial)) == 0) {
                return partial;
            }
            try {
                items.addAll(invokeBatch(task, batch));
                processed += batch.size();
            } catch (Exception e) {
                skippedBatches++;
            }
        }
        return buildResult(processed, total, skippedBatches, items);
    }

    /** 单批 LLM 调用（含幻觉过滤），失败按重试次数重试后向上抛（由调用方跳过该批计数） */
    private List<AiReviewCheckItemDTO> invokeBatch(AiAnalysisTask task, List<CaseContext> batch) {
        AiCallContext context = new AiCallContext(task.getCreatedBy(), task.getWorkspaceId(), task.getProjectId());
        String businessData = buildBatchData(batch);
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= BATCH_RETRY_TIMES; attempt++) {
            try {
                AiReviewCheckBatchDTO out = aiGatewayService.completeStructured(context, AiFunctionType.REVIEW_CHECK,
                        TASK_INSTRUCTION, businessData, ChatCallOptions.json(),
                        AiReviewCheckBatchDTO.class, this::assertBatch);
                return filterHallucinations(out, batch);
            } catch (RuntimeException e) {
                lastError = e;
            }
        }
        throw lastError;
    }

    /** 幻觉过滤：snapshotNodeId 必须命中本批输入用例，非法/未命中剔除（4.1） */
    private List<AiReviewCheckItemDTO> filterHallucinations(AiReviewCheckBatchDTO out, List<CaseContext> batch) {
        Set<UUID> validIds = batch.stream().map(c -> c.node().getId()).collect(Collectors.toSet());
        if (out.getItems() == null) {
            return List.of();
        }
        return out.getItems().stream()
                .filter(item -> item.getSnapshotNodeId() != null && contains(validIds, item.getSnapshotNodeId()))
                .toList();
    }

    private boolean contains(Set<UUID> validIds, String rawId) {
        try {
            return validIds.contains(UUID.fromString(rawId));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 输出结构断言：维度枚举白名单 + 关键字段非空（schema 约束与提示词一致，2.2.1） */
    private void assertBatch(AiReviewCheckBatchDTO out) {
        if (out.getItems() == null) {
            throw new AiOutputValidator.OutputValidationException("items 不能为空");
        }
        for (AiReviewCheckItemDTO item : out.getItems()) {
            if (!DIMENSIONS.contains(item.getDimension())) {
                throw new AiOutputValidator.OutputValidationException("dimension 取值非法：" + item.getDimension());
            }
        }
    }

    /** 关联 case 节点按排序收集，并组装文档名/祖先路径/子节点（precondition/step/expected） */
    private List<CaseContext> collectCases(List<TestReviewNodeSnapshot> allNodes,
            Map<UUID, String> docNameById) {
        Map<UUID, TestReviewNodeSnapshot> nodeById = allNodes.stream()
                .collect(Collectors.toMap(TestReviewNodeSnapshot::getId, n -> n, (a, b) -> a));
        return allNodes.stream()
                .filter(n -> Constants.NodeType.CASE.equals(n.getType()) && Boolean.TRUE.equals(n.getIsAssociated()))
                .sorted(Comparator.comparing(n -> n.getSortOrder() != null ? n.getSortOrder() : 0))
                .map(n -> new CaseContext(n,
                        docNameById.getOrDefault(n.getDocumentSnapshotId(), "未命名文档"),
                        ancestorTitles(n, nodeById),
                        childNodes(n, allNodes)))
                .toList();
    }

    private List<TestReviewNodeSnapshot> childNodes(TestReviewNodeSnapshot node, List<TestReviewNodeSnapshot> allNodes) {
        return allNodes.stream()
                .filter(c -> node.getId().equals(c.getParentId()))
                .sorted(Comparator.comparing(c -> c.getSortOrder() != null ? c.getSortOrder() : 0))
                .toList();
    }

    private List<String> ancestorTitles(TestReviewNodeSnapshot node, Map<UUID, TestReviewNodeSnapshot> nodeById) {
        List<String> titles = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        TestReviewNodeSnapshot cursor = node;
        while (cursor != null && visited.add(cursor.getId())) {
            titles.addFirst(cursor.getTitle());
            cursor = cursor.getParentId() != null ? nodeById.get(cursor.getParentId()) : null;
        }
        return titles;
    }

    /** 切批：先按用例数 30 封顶，再按单批 token 预算截断（超预算用例单独成批，防御性放行） */
    private List<List<CaseContext>> splitBatches(List<CaseContext> cases) {
        List<List<CaseContext>> batches = new ArrayList<>();
        List<CaseContext> current = new ArrayList<>();
        int currentTokens = 0;
        for (CaseContext c : cases) {
            int tokens = PromptAssembler.estimateTokens(buildCaseData(c));
            if (!current.isEmpty() && (current.size() >= BATCH_CASE_LIMIT
                    || currentTokens + tokens > BATCH_TOKEN_BUDGET)) {
                batches.add(current);
                current = new ArrayList<>();
                currentTokens = 0;
            }
            current.add(c);
            currentTokens += tokens;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    private String buildBatchData(List<CaseContext> batch) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            CaseContext c = batch.get(i);
            sb.append("【用例 ").append(i + 1).append("】snapshotNodeId=").append(c.node().getId()).append('\n');
            sb.append(buildCaseData(c)).append('\n');
        }
        List<List<CaseContext>> groups = similarTitleGroups(batch);
        if (!groups.isEmpty()) {
            sb.append("【同批相似用例分组】（仅用于 priority_conflict 判断）\n");
            for (List<CaseContext> group : groups) {
                sb.append("- ").append(group.stream()
                        .map(c -> c.node().getTitle())
                        .collect(Collectors.joining(" / "))).append('\n');
            }
        }
        return sb.toString();
    }

    private String buildCaseData(CaseContext c) {
        StringBuilder sb = new StringBuilder();
        sb.append("用例标题：").append(c.node().getTitle()).append('\n');
        sb.append("所属文档：").append(c.documentName()).append('\n');
        if (!c.ancestorTitles().isEmpty()) {
            sb.append("祖先路径：").append(String.join(" > ", c.ancestorTitles())).append('\n');
        }
        Map<String, List<TestReviewNodeSnapshot>> byType = c.childNodes().stream()
                .collect(Collectors.groupingBy(TestReviewNodeSnapshot::getType));
        appendChildren(sb, "前置条件", byType.getOrDefault(Constants.NodeType.PRECONDITION, List.of()));
        appendChildren(sb, "步骤", byType.getOrDefault(Constants.NodeType.STEP, List.of()));
        appendChildren(sb, "预期结果", byType.getOrDefault(Constants.NodeType.EXPECTED, List.of()));
        return sb.toString();
    }

    private void appendChildren(StringBuilder sb, String label, List<TestReviewNodeSnapshot> nodes) {
        sb.append(label).append('：');
        if (nodes.isEmpty()) {
            sb.append("无\n");
        } else {
            sb.append(nodes.stream().map(TestReviewNodeSnapshot::getTitle).collect(Collectors.joining("；")))
                    .append('\n');
        }
    }

    /** 同批相似用例分组：归一化标题公共子串长度达标者归并为一组（优先级冲突仅在组内比较，4.1） */
    private List<List<CaseContext>> similarTitleGroups(List<CaseContext> batch) {
        int n = batch.size();
        if (n < 2) {
            return List.of();
        }
        List<String> norms = batch.stream()
                .map(c -> normalizeTitle(c.node().getTitle()))
                .toList();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (sharesCommonSubstring(norms.get(i), norms.get(j))) {
                    union(parent, i, j);
                }
            }
        }
        Map<Integer, List<Integer>> components = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            components.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }
        return components.values().stream()
                .filter(list -> list.size() >= 2)
                .map(list -> list.stream().sorted().map(batch::get).toList())
                .toList();
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** 滚动数组求最长公共子串长度是否达到阈值（标题短，O(n·m) 可接受） */
    private boolean sharesCommonSubstring(String a, String b) {
        if (a.length() < SIMILAR_MIN_COMMON || b.length() < SIMILAR_MIN_COMMON) {
            return false;
        }
        int[] dp = new int[b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            int prev = 0;
            for (int j = 1; j <= b.length(); j++) {
                int temp = dp[j];
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[j] = prev + 1;
                    if (dp[j] >= SIMILAR_MIN_COMMON) {
                        return true;
                    }
                } else {
                    dp[j] = 0;
                }
                prev = temp;
            }
        }
        return false;
    }

    private int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }

    private Map<String, Object> buildResult(int checkedCaseCount, int totalCaseCount, int skippedBatches,
            List<AiReviewCheckItemDTO> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedCaseCount", checkedCaseCount);
        result.put("totalCaseCount", totalCaseCount);
        result.put("skippedBatches", skippedBatches);
        result.put("items", items);
        return result;
    }

    /** 用例检查上下文：节点 + 文档名 + 祖先路径 + 直接子节点（precondition/step/expected） */
    private record CaseContext(TestReviewNodeSnapshot node, String documentName,
                               List<String> ancestorTitles, List<TestReviewNodeSnapshot> childNodes) {
    }
}
