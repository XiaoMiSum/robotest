package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPlanOrderReasonReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPlanOrderComputeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPlanOrderQueryRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPlanOrderReasonRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPlanOrderRecommendRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanModuleSnapshot;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanNodeSnapshotMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 执行顺序推荐实现（详细设计 3.4 / 4.4）：
 * <ol>
 *   <li>确定性评分（不依赖 LLM）：relatedBugCount + priorityWeight + moduleBugDensity 加权，
 *       min-max 归一化，同分按 priorityWeight、relatedBugCount、快照 sort_order 决胜；</li>
 *   <li>结果落库 ai_analysis_task（type=plan_order_recommend，target=计划 ID），重复计算覆盖旧记录；</li>
 *   <li>理由按需生成（plan_order_reason，纯文本一句话）并回填 result 对应 item，LLM 不参与排序。</li>
 * </ol>
 */
@Slf4j
@Service
public class AiPlanOrderRecommendServiceImpl implements AiPlanOrderRecommendService {

    /** 权重默认值（settings 键 planOrder.weights 缺失/非法时回退，4.4） */
    static final double DEFAULT_W1 = 0.5;
    static final double DEFAULT_W2 = 0.3;
    static final double DEFAULT_W3 = 0.2;

    private static final String REASON_TASK_INSTRUCTION = """
            请基于业务数据中的评分因子（历史关联缺陷数、优先级权重、模块缺陷密度），
            用一句话说明推荐优先执行该用例的理由。""";

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private AiAnalysisTaskMapper aiTaskMapper;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private TestPlanNodeSnapshotMapper planNodeSnapshotMapper;
    @Resource
    private TestPlanModuleSnapshotMapper planModuleSnapshotMapper;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private BugMapper bugMapper;

    @Override
    public AiPlanOrderComputeRespDTO compute(UUID userId, UUID workspaceId, UUID projectId, UUID planId) {
        TestPlan plan = requireExecutor(planId, userId);
        List<TestPlanNodeSnapshot> caseNodes = planNodeSnapshotMapper
                .listAssociatedByPlanId(planId, Constants.NodeType.CASE);
        // 计划未关联快照（6012，3.4.1）
        if (caseNodes.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TARGET_STATE_INVALID);
        }

        Map<String, Double> weights = resolveWeights();
        List<ScoredNode> scored = scoreNodes(planId, projectId, caseNodes, weights);

        AiPlanOrderRecommendRespDTO result = new AiPlanOrderRecommendRespDTO();
        result.setPlanSyncedAt(plan.getSnapshotSyncedAt() == null ? null : plan.getSnapshotSyncedAt().toString());
        result.setWeights(weights);
        result.setItems(scored.stream()
                .map(this::toItem)
                .toList());

        // 落库 running 任务（同步计算，不进 executor 队列），success 后覆盖旧记录（3.4.1）
        AiAnalysisTask task = new AiAnalysisTask();
        task.setWorkspaceId(workspaceId);
        task.setProjectId(projectId);
        task.setType(Constants.AiTaskType.PLAN_ORDER_RECOMMEND);
        task.setTargetId(planId);
        task.setStatus(Constants.AiTaskStatus.RUNNING);
        task.setProgress(0);
        task.setCreatedBy(userId);
        aiTaskMapper.insert(task);
        UUID taskId = task.getId();
        aiTaskMapper.markSuccessIfRunning(taskId, JsonUtils.toJsonString(result));
        aiTaskMapper.deleteSuccessExcept(Constants.AiTaskType.PLAN_ORDER_RECOMMEND, planId, taskId);

        AiPlanOrderComputeRespDTO resp = new AiPlanOrderComputeRespDTO();
        resp.setTaskId(taskId);
        resp.setResult(result);
        return resp;
    }

    @Override
    public AiPlanOrderQueryRespDTO query(UUID userId, UUID workspaceId, UUID projectId, UUID planId) {
        TestPlan plan = requireExecutor(planId, userId);
        AiPlanOrderQueryRespDTO resp = new AiPlanOrderQueryRespDTO();
        AiAnalysisTask task = aiTaskMapper.findLatestSuccessByTypeAndTarget(
                Constants.AiTaskType.PLAN_ORDER_RECOMMEND, planId);
        if (task == null || task.getResult() == null) {
            resp.setStale(false);
            resp.setResult(null);
            return resp;
        }
        AiPlanOrderRecommendRespDTO result = toResult(task);
        // 失效判定：计算时的 snapshot_synced_at 与当前不相等（含 NULL）即 stale（4.4）
        resp.setStale(AiPlanOrderScoring.isStale(result.getPlanSyncedAt(), plan.getSnapshotSyncedAt()));
        resp.setResult(result);
        return resp;
    }

    @Override
    public AiPlanOrderReasonRespDTO reason(UUID userId, UUID workspaceId, UUID projectId, UUID planId,
                                           AiPlanOrderReasonReqDTO reqDTO) {
        requireExecutor(planId, userId);
        AiPlanOrderReasonRespDTO resp = new AiPlanOrderReasonRespDTO();
        AiAnalysisTask task = aiTaskMapper.findLatestSuccessByTypeAndTarget(
                Constants.AiTaskType.PLAN_ORDER_RECOMMEND, planId);
        if (task == null || task.getResult() == null) {
            return resp;
        }
        AiPlanOrderRecommendRespDTO result = toResult(task);
        AiPlanOrderRecommendRespDTO.Item item = result.getItems().stream()
                .filter(i -> Objects.equals(i.getSnapshotNodeId(), reqDTO.getSnapshotNodeId()))
                .findFirst()
                .orElse(null);
        if (item == null || StringUtils.hasText(item.getReason())) {
            // 重复请求直接返回已生成理由（缓存复用，3.4.3）；item 不存在时返回空
            if (item != null) {
                resp.setReason(item.getReason());
            }
            return resp;
        }

        String reason = generateReason(userId, workspaceId, projectId, reqDTO.getSnapshotNodeId(), item);
        if (!StringUtils.hasText(reason)) {
            return resp;
        }
        // 回填并更新落库结果（任务已 success，仅更新 result 不触碰状态机）
        item.setReason(reason);
        aiTaskMapper.updateResultById(task.getId(), JsonUtils.toJsonString(result));
        resp.setReason(reason);
        return resp;
    }

    /**
     * 现势一次性取数评分（4.4）：快照节点 → factors，min-max 归一化后加权排序
     */
    private List<ScoredNode> scoreNodes(UUID planId, UUID projectId, List<TestPlanNodeSnapshot> caseNodes,
                                        Map<String, Double> weights) {
        Map<UUID, UUID> documentModuleIdBySnapshotId = planModuleSnapshotMapper.listByPlanId(planId).stream()
                .filter(m -> Constants.ModuleType.DOCUMENT.equals(m.getType()))
                .filter(m -> m.getOriginalModuleId() != null)
                .collect(Collectors.toMap(TestPlanModuleSnapshot::getId, TestPlanModuleSnapshot::getOriginalModuleId,
                        (a, b) -> a));

        List<TestCaseModule> allModules = testCaseModuleMapper.listByProjectId(projectId);
        AiModuleTreeSupport.ModuleIndex index = AiModuleTreeSupport.indexByParent(allModules);
        Set<UUID> documentModuleIds = allModules.stream()
                .filter(m -> Constants.ModuleType.DOCUMENT.equals(m.getType()))
                .map(TestCaseModule::getId)
                .collect(Collectors.toSet());

        // 现势 case 节点按文档计数（密度分母），一次性查询保证同次计算可复现
        List<TestCaseNode> currentCaseNodes = documentModuleIds.isEmpty() ? List.of()
                : testCaseNodeMapper.listCaseNodesByDocumentIds(documentModuleIds);
        Map<UUID, Long> caseCountByDocumentId = currentCaseNodes.stream()
                .collect(Collectors.groupingBy(TestCaseNode::getDocumentId, Collectors.counting()));

        // 项目内未删除缺陷一次取数：relatedCaseId 关联缺陷数 + moduleId 模块缺陷数
        List<Bug> bugs = bugMapper.listForOrderRecommend(projectId);
        Map<UUID, Long> bugCountByModuleId = bugs.stream()
                .filter(b -> b.getModuleId() != null)
                .collect(Collectors.groupingBy(Bug::getModuleId, Collectors.counting()));
        Map<UUID, Long> bugCountByRelatedCaseId = bugs.stream()
                .filter(b -> b.getRelatedCaseId() != null)
                .collect(Collectors.groupingBy(Bug::getRelatedCaseId, Collectors.counting()));

        List<ScoredNode> nodes = caseNodes.stream()
                .map(node -> {
                    int relatedBugCount = bugCountByRelatedCaseId.getOrDefault(node.getOriginalNodeId(), 0L).intValue();
                    double priorityWeight = AiPlanOrderScoring.priorityWeight(node.getPriority());
                    double moduleBugDensity = moduleBugDensity(node.getDocumentSnapshotId(),
                            documentModuleIdBySnapshotId, index.moduleById(), index.childrenByParent(),
                            bugCountByModuleId, caseCountByDocumentId);
                    return new ScoredNode(node, relatedBugCount, priorityWeight, moduleBugDensity);
                })
                .toList();

        double bugMin = min(nodes.stream().mapToDouble(ScoredNode::relatedBugCount).toArray());
        double bugMax = max(nodes.stream().mapToDouble(ScoredNode::relatedBugCount).toArray());
        double densityMin = min(nodes.stream().mapToDouble(ScoredNode::moduleBugDensity).toArray());
        double densityMax = max(nodes.stream().mapToDouble(ScoredNode::moduleBugDensity).toArray());

        return nodes.stream()
                .map(n -> {
                    double score = AiPlanOrderScoring.score(
                            weights.getOrDefault("w1", DEFAULT_W1),
                            weights.getOrDefault("w2", DEFAULT_W2),
                            weights.getOrDefault("w3", DEFAULT_W3),
                            AiPlanOrderScoring.normalize(n.relatedBugCount(), bugMin, bugMax),
                            n.priorityWeight(),
                            AiPlanOrderScoring.normalize(n.moduleBugDensity(), densityMin, densityMax));
                    return n.withScore(score);
                })
                .sorted(Comparator.comparing(ScoredNode::rankKey, AiPlanOrderScoring.RankKey::compare))
                .toList();
    }

    /**
     * 模块缺陷密度：快照节点所属文档模块（含子孙模块）缺陷数 ÷ 该模块下现势 case 节点数；
     * 分子分母均取现势口径，模块快照/模块树缺失或分母为 0 时取 0（4.4）
     */
    private double moduleBugDensity(UUID documentSnapshotId, Map<UUID, UUID> documentModuleIdBySnapshotId,
                                    Map<UUID, TestCaseModule> moduleById,
                                    Map<UUID, List<TestCaseModule>> childrenByParent,
                                    Map<UUID, Long> bugCountByModuleId,
                                    Map<UUID, Long> caseCountByDocumentId) {
        UUID documentModuleId = documentModuleIdBySnapshotId.get(documentSnapshotId);
        if (documentModuleId == null || !moduleById.containsKey(documentModuleId)) {
            return 0.0;
        }
        Set<UUID> subtree = new LinkedHashSet<>();
        AiModuleTreeSupport.collectSubtreeModuleIds(documentModuleId, moduleById, childrenByParent, subtree);
        long bugCount = 0;
        long caseCount = 0;
        for (UUID moduleId : subtree) {
            bugCount += bugCountByModuleId.getOrDefault(moduleId, 0L);
            TestCaseModule module = moduleById.get(moduleId);
            if (module != null && Constants.ModuleType.DOCUMENT.equals(module.getType())) {
                caseCount += caseCountByDocumentId.getOrDefault(moduleId, 0L);
            }
        }
        return caseCount == 0 ? 0.0 : (double) bugCount / caseCount;
    }

    /**
     * 权重解析：settings 键 planOrder.weights（Map），键缺失/非数值回退默认值（4.4）
     */
    private Map<String, Double> resolveWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        Object raw = aiConfigService.getMergedSettings().get("planOrder.weights");
        weights.put("w1", weightValue(raw, "w1", DEFAULT_W1));
        weights.put("w2", weightValue(raw, "w2", DEFAULT_W2));
        weights.put("w3", weightValue(raw, "w3", DEFAULT_W3));
        return weights;
    }

    private double weightValue(Object raw, String key, double fallback) {
        if (!(raw instanceof Map<?, ?> map)) {
            return fallback;
        }
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double min(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double min = values[0];
        for (double value : values) {
            min = Math.min(min, value);
        }
        return min;
    }

    private double max(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double max = values[0];
        for (double value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private AiPlanOrderRecommendRespDTO.Item toItem(ScoredNode node) {
        AiPlanOrderRecommendRespDTO.Item item = new AiPlanOrderRecommendRespDTO.Item();
        item.setSnapshotNodeId(node.node().getId());
        item.setScore(node.score());
        AiPlanOrderRecommendRespDTO.Factors factors = new AiPlanOrderRecommendRespDTO.Factors();
        factors.setRelatedBugCount(node.relatedBugCount());
        factors.setPriorityWeight(node.priorityWeight());
        factors.setModuleBugDensity(node.moduleBugDensity());
        item.setFactors(factors);
        return item;
    }

    private AiPlanOrderRecommendRespDTO toResult(AiAnalysisTask task) {
        AiPlanOrderRecommendRespDTO result = JsonUtils.convert(task.getResult(),
                AiPlanOrderRecommendRespDTO.class);
        if (result.getItems() != null) {
            for (int i = 0; i < result.getItems().size(); i++) {
                result.getItems().get(i).setOrder(i + 1);
            }
        }
        return result;
    }

    /**
     * LLM 生成一句话理由（plan_order_reason，纯文本输出，3.4.3）：输入该条用例的标题与因子数据
     */
    private String generateReason(UUID userId, UUID workspaceId, UUID projectId, UUID snapshotNodeId,
                                  AiPlanOrderRecommendRespDTO.Item item) {
        TestPlanNodeSnapshot node = planNodeSnapshotMapper.selectById(snapshotNodeId);
        String title = node != null && StringUtils.hasText(node.getTitle()) ? node.getTitle() : "该用例";
        AiPlanOrderRecommendRespDTO.Factors factors = item.getFactors();
        StringBuilder data = new StringBuilder();
        data.append("【用例】\n标题：").append(title).append('\n');
        data.append("【评分因子】\n")
                .append("历史关联缺陷数：").append(factors == null || factors.getRelatedBugCount() == null
                        ? 0 : factors.getRelatedBugCount())
                .append("，优先级权重：").append(factors == null || factors.getPriorityWeight() == null
                        ? 0.0 : factors.getPriorityWeight())
                .append("，模块缺陷密度：").append(factors == null || factors.getModuleBugDensity() == null
                        ? 0.0 : factors.getModuleBugDensity())
                .append('\n');
        try {
            ChatResult chat = aiGatewayService.complete(
                    new AiCallContext(userId, workspaceId, projectId),
                    AiFunctionType.PLAN_ORDER_REASON,
                    REASON_TASK_INSTRUCTION,
                    data.toString(),
                    new ChatCallOptions(null, null, false, AiConstants.LLM_TIMEOUT_MILLIS));
            return chat == null ? null : chat.content();
        } catch (Exception e) {
            log.warn("[AI] 执行顺序推荐理由生成失败: {}", e.getMessage());
            return null;
        }
    }

    private TestPlan requireExecutor(UUID planId, UUID userId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        // 仅计划执行人（3.4.1 权限口径，附录 B）
        if (!userId.equals(plan.getExecutorId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        return plan;
    }

    /** 评分中间态：快照节点 + 三因子 + 综合得分 */
    private record ScoredNode(TestPlanNodeSnapshot node, int relatedBugCount,
                              double priorityWeight, double moduleBugDensity, double score) {

        ScoredNode(TestPlanNodeSnapshot node, int relatedBugCount, double priorityWeight, double moduleBugDensity) {
            this(node, relatedBugCount, priorityWeight, moduleBugDensity, 0.0);
        }

        ScoredNode withScore(double score) {
            return new ScoredNode(node, relatedBugCount, priorityWeight, moduleBugDensity, score);
        }

        AiPlanOrderScoring.RankKey rankKey() {
            return new AiPlanOrderScoring.RankKey(score, priorityWeight, relatedBugCount,
                    node.getSortOrder() == null ? 0 : node.getSortOrder());
        }
    }
}
