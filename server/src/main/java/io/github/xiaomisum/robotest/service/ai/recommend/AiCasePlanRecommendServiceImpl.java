package io.github.xiaomisum.robotest.service.ai.recommend;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCasePlanRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiCasePlanRecommendRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.support.AiConstants;
import io.github.xiaomisum.robotest.service.ai.support.AiKeywordExtractor;
import io.github.xiaomisum.robotest.service.ai.support.AiModuleTreeSupport;
import io.github.xiaomisum.robotest.service.ai.support.AiRequirementContextAssembler;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService.CaseDedupHit;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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

/**
 * AI 用例规划智能推荐实现（详细设计 3.5 / 4.5）：
 * <ol>
 *   <li>语义匹配（可用时）：text / 需求条目合并向量化 → ai_case_embedding TopK
 *       （K = planRecommend.topK 默认 50，阈值 = planRecommend.similarityThreshold 默认 0.7），
 *       matchType = semantic，score = 相似度；降级态改为 LLM 抽取关键词 + 标题 ILIKE（score = 0.6，仅展示排序用）；</li>
 *   <li>排除已纳入用例：过滤 excludeCaseNodeIds，截断 50 条按 score 降序；</li>
 *   <li>理由生成：一次 LLM 调用为全部结果批量生成一句话 reason（读超时功能级覆盖 60s），
 *       长度不匹配或生成失败时整体置空——理由缺失不影响清单可用。</li>
 * </ol>
 */
@Slf4j
@Service
public class AiCasePlanRecommendServiceImpl implements AiCasePlanRecommendService {

    /**
     * 结果上限（3.5：截断 50 条按 score 降序）
     */
    static final int RESULT_LIMIT = 50;

    /**
     * 语义降级关键词匹配得分（4.5 步骤 1，代码内置常量，仅作展示排序用）
     */
    static final double DEGRADED_KEYWORD_SCORE = 0.6;

    private static final String REASON_TASK_INSTRUCTION = """
            请根据需求描述，为下列每个用例标题生成一句话推荐理由（reason），说明该用例为何应纳入当前评审或测试计划的用例清单。
            输出 reasons 数组，顺序与用例标题清单一一对应、长度完全一致；无法给出理由的用例可用空字符串占位。""";

    private static final String KEYWORD_TASK_INSTRUCTION = """
            请从给定需求描述中抽取用于在测试用例库中检索的 ≤10 个关键词。
            关键词应为需求描述中出现过的核心业务词或短语，按重要程度取前 10，避免空泛词汇。""";

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private AiVectorSearchService vectorSearchService;
    @Resource
    private AiKeywordExtractor aiKeywordExtractor;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private AiRequirementContextAssembler requirementContextAssembler;

    @Override
    public AiCasePlanRecommendRespDTO recommend(UUID userId, UUID workspaceId, UUID projectId,
                                                AiCasePlanRecommendReqDTO reqDTO) {
        // text / requirementIds 至少一项非空（3.5）
        boolean hasText = StringUtils.hasText(reqDTO.getText());
        boolean hasItems = reqDTO.getRequirementIds() != null && !reqDTO.getRequirementIds().isEmpty();
        if (!hasText && !hasItems) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        // 需求描述块（需求条目 + 需求文本），供语义向量化与理由生成共用
        String needData = buildNeedData(projectId, reqDTO);
        // 1. 语义匹配（4.5 步骤 1）：可用时向量 TopK，否则降级关键词 + 标题 ILIKE
        SemanticResult semantic = matchSemantic(userId, workspaceId, projectId, needData);
        // 2. 排除已纳入用例 + 排序截断（4.5 步骤 2）
        List<Candidate> ranked = excludeAndRank(semantic.candidates(), reqDTO.getExcludeCaseNodeIds());
        // 3. 理由生成（4.5 步骤 3）：失败/长度不匹配整体置空，不影响清单可用
        List<String> reasons = buildReasons(userId, workspaceId, projectId, needData, ranked);
        return response(ranked, reasons, semantic.degraded());
    }

    /**
     * 需求描述块：需求条目（标题定界，按选取顺序）+ 需求文本；text 与条目超预算截断（同 4.3）
     */
    private String buildNeedData(UUID projectId, AiCasePlanRecommendReqDTO reqDTO) {
        return requirementContextAssembler.assemble(projectId, reqDTO.getRequirementIds(),
                reqDTO.getText(), null).data();
    }

    /**
     * 语义匹配（4.5 步骤 1）：semanticSearch = available 时向量 TopK；
     * 未配置/降级/调用异常自动降级为 LLM 抽取关键词 + 标题 ILIKE（score 0.6，semanticDegraded=true）。
     */
    private SemanticResult matchSemantic(UUID userId, UUID workspaceId, UUID projectId, String needData) {
        if (!StringUtils.hasText(needData)) {
            return new SemanticResult(List.of(), false);
        }
        String semantic = aiConfigService.getStatus().getSemanticSearch();
        if (Constants.AiSemanticSearch.AVAILABLE.equals(semantic)) {
            try {
                int topK = aiConfigService.getIntSetting("planRecommend.topK");
                double threshold = aiConfigService.getNumberSetting("planRecommend.similarityThreshold");
                List<CaseDedupHit> hits = vectorSearchService.searchSimilarCases(projectId, needData, topK, threshold);
                return new SemanticResult(toSemanticCandidates(projectId, hits), false);
            } catch (Exception e) {
                log.warn("[AI] 用例规划推荐语义检索失败，降级关键词匹配: {}", e.getMessage());
            }
        }
        return new SemanticResult(degradedKeywordCandidates(userId, workspaceId, projectId, needData), true);
    }

    /**
     * 语义命中 → 候选：按 nodeId 批量取节点，附模块路径（相似度为 score）
     */
    private List<Candidate> toSemanticCandidates(UUID projectId, List<CaseDedupHit> hits) {
        if (hits.isEmpty()) {
            return List.of();
        }
        List<UUID> nodeIds = hits.stream().map(CaseDedupHit::nodeId).toList();
        Map<UUID, TestCaseNode> nodeById = testCaseNodeMapper.selectByIds(nodeIds).stream()
                .collect(Collectors.toMap(TestCaseNode::getId, node -> node));
        Map<UUID, String> modulePathById = AiModuleTreeSupport.buildModulePaths(testCaseModuleMapper.listByProjectId(projectId));
        return hits.stream()
                .map(hit -> {
                    TestCaseNode node = nodeById.get(hit.nodeId());
                    if (node == null) {
                        return null;
                    }
                    return new Candidate(node.getId(), node.getTitle(),
                            modulePathById.getOrDefault(node.getDocumentId(), ""),
                            "semantic", hit.similarity());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 降级关键词匹配（4.5 步骤 1）：LLM 抽取 ≤10 关键词 → 标题 ILIKE 每词取前 30，score = 0.6 仅排序用
     */
    private List<Candidate> degradedKeywordCandidates(UUID userId, UUID workspaceId, UUID projectId, String needData) {
        List<String> keywords = aiKeywordExtractor.extract(userId, workspaceId, projectId,
                KEYWORD_TASK_INSTRUCTION, "【需求描述】", needData);
        List<TestCaseModule> documents = testCaseModuleMapper.findDocumentModulesByProjectId(projectId);
        List<UUID> documentIds = documents.stream().map(TestCaseModule::getId).toList();
        if (documentIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> modulePathById = AiModuleTreeSupport.buildModulePaths(testCaseModuleMapper.listByProjectId(projectId));
        Map<UUID, Candidate> byNode = new LinkedHashMap<>();
        for (String keyword : keywords) {
            List<TestCaseNode> nodes = testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(
                    documentIds, keyword, AiConstants.CANDIDATE_LIMIT_PER_KEYWORD);
            for (TestCaseNode node : nodes) {
                byNode.putIfAbsent(node.getId(), new Candidate(node.getId(), node.getTitle(),
                        modulePathById.getOrDefault(node.getDocumentId(), ""),
                        "semantic", DEGRADED_KEYWORD_SCORE));
            }
        }
        return new ArrayList<>(byNode.values());
    }

    /**
     * 排除已纳入用例 + 排序截断（4.5 步骤 2）：过滤 excludeCaseNodeIds 后按 score 降序截断 50 条
     */
    private List<Candidate> excludeAndRank(List<Candidate> candidates, List<UUID> excludeCaseNodeIds) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<UUID> excluded = excludeCaseNodeIds == null || excludeCaseNodeIds.isEmpty()
                ? Set.of()
                : new HashSet<>(excludeCaseNodeIds);
        return candidates.stream()
                .filter(candidate -> !excluded.contains(candidate.nodeId()))
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .limit(RESULT_LIMIT)
                .toList();
    }

    /**
     * 理由生成（4.5 步骤 3）：一次 LLM 调用批量生成，长度不匹配/生成失败整体置空（不阻断清单返回）
     */
    private List<String> buildReasons(UUID userId, UUID workspaceId, UUID projectId,
                                      String needData, List<Candidate> ranked) {
        if (ranked.isEmpty()) {
            return List.of();
        }
        StringBuilder data = new StringBuilder(needData);
        data.append("【用例标题清单】\n");
        for (int i = 0; i < ranked.size(); i++) {
            data.append(i + 1).append(". ").append(ranked.get(i).title()).append('\n');
        }
        try {
            ReasonOut out = aiGatewayService.completeStructured(
                    new AiCallContext(userId, workspaceId, projectId),
                    AiFunctionType.CASE_PLAN_RECOMMENDATION,
                    REASON_TASK_INSTRUCTION,
                    data.toString(),
                    new ChatCallOptions(null, null, true, AiConstants.LLM_TIMEOUT_MILLIS),
                    ReasonOut.class,
                    null);
            List<String> reasons = out.getReasons();
            if (reasons == null || reasons.size() != ranked.size()) {
                log.warn("[AI] 用例规划推荐理由长度不匹配（{} vs {}），整体置空",
                        reasons == null ? 0 : reasons.size(), ranked.size());
                return List.of();
            }
            return reasons;
        } catch (Exception e) {
            log.warn("[AI] 用例规划推荐理由生成失败，整体置空: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 响应组装：items 按 score 降序；理由整体缺失时 reason 置空
     */
    private AiCasePlanRecommendRespDTO response(List<Candidate> ranked, List<String> reasons,
                                                boolean semanticDegraded) {
        AiCasePlanRecommendRespDTO resp = new AiCasePlanRecommendRespDTO();
        resp.setSemanticDegraded(semanticDegraded);
        List<AiCasePlanRecommendRespDTO.Item> items = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Candidate candidate = ranked.get(i);
            AiCasePlanRecommendRespDTO.Item item = new AiCasePlanRecommendRespDTO.Item();
            item.setCaseNodeId(candidate.nodeId());
            item.setTitle(candidate.title());
            item.setModulePath(candidate.modulePath());
            item.setMatchType(candidate.matchType());
            item.setScore(candidate.score());
            item.setReason(reasons.isEmpty() ? null : reasons.get(i));
            items.add(item);
        }
        resp.setItems(items);
        return resp;
    }

    /**
     * 推荐候选（节点 + 标题 + 模块路径 + 命中方式 + 得分）
     */
    private record Candidate(UUID nodeId, String title, String modulePath, String matchType, double score) {
    }

    /**
     * 语义匹配结果 + 是否降级
     */
    private record SemanticResult(List<Candidate> candidates, boolean degraded) {
    }

    /**
     * LLM 结构化输出：批量推荐理由数组（长度须与输入用例标题清单一致，不一致整体置空）
     */
    @Setter
    @Getter
    public static class ReasonOut {

        private List<String> reasons;

    }
}
