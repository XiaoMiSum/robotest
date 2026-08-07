package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiRegressionRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiRegressionRecommendRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.AiVectorSearchService.CaseDedupHit;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 回归测试用例子集推荐实现（详细设计 3.5 / 4.5）：
 * <ol>
 *   <li>模块名匹配：modules 输入对模块树名称精确 + ILIKE 模糊匹配，命中模块（含子孙目录）下全部 case 节点；
 *       score = 精确 1.0 / 模糊 0.9（matchType = module）；</li>
 *   <li>语义匹配（可用时）：text / 需求条目合并向量化 → case_embedding TopK
 *       （K = regression.topK 默认 50，阈值 = regression.similarityThreshold 默认 0.7），
 *       matchType = semantic，score = 相似度；降级态改为 LLM 抽取关键词 + 标题 ILIKE（score = 0.6，仅展示排序用）；</li>
 *   <li>合并去重（both 取高分 + matchType 合并），截断 50 条按 score 降序；</li>
 *   <li>理由生成：一次 LLM 调用为全部结果批量生成一句话 reason（读超时功能级覆盖 60s），
 *       长度不匹配或生成失败时整体置空——理由缺失不影响清单可用。</li>
 * </ol>
 */
@Slf4j
@Service
public class AiRegressionRecommendServiceImpl implements AiRegressionRecommendService {

    /**
     * 结果上限（3.5：截断 50 条按 score 降序）
     */
    static final int RESULT_LIMIT = 50;

    /**
     * 模块名精确匹配得分（4.5 步骤 1）
     */
    static final double EXACT_MODULE_SCORE = 1.0;

    /**
     * 模块名 ILIKE 模糊匹配得分（4.5 步骤 1）
     */
    static final double FUZZY_MODULE_SCORE = 0.9;

    /**
     * 语义降级关键词匹配得分（4.5 步骤 2，代码内置常量，仅作展示排序用）
     */
    static final double DEGRADED_KEYWORD_SCORE = 0.6;

    private static final String REASON_TASK_INSTRUCTION = """
            请根据变更描述，为下列每个用例标题生成一句话推荐理由（reason），说明该用例为何应纳入本次回归测试子集。
            输出 reasons 数组，顺序与用例标题清单一一对应、长度完全一致；无法给出理由的用例可用空字符串占位。""";

    private static final String KEYWORD_TASK_INSTRUCTION = """
            请从给定变更描述中抽取用于在测试用例库中检索的 ≤10 个关键词。
            关键词应为变更描述中出现过的核心业务词或短语，按重要程度取前 10，避免空泛词汇。""";

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
    public AiRegressionRecommendRespDTO recommend(UUID userId, UUID workspaceId, UUID projectId,
                                                  AiRegressionRecommendReqDTO reqDTO) {
        // 三种输入（modules / text / requirementIds）至少一项非空（3.5）
        boolean hasModules = reqDTO.getModules() != null && !reqDTO.getModules().isEmpty();
        boolean hasText = StringUtils.hasText(reqDTO.getText());
        boolean hasItems = reqDTO.getRequirementIds() != null && !reqDTO.getRequirementIds().isEmpty();
        if (!hasModules && !hasText && !hasItems) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        // saveAsRequirement 非空时 text 必填；推荐开始前独立保存，保存失败不阻断推荐（3.5，同 3.3）
        if (reqDTO.getSaveAsRequirement() != null) {
            if (!hasText) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            requirementContextAssembler.trySaveRequirement(projectId, userId,
                    reqDTO.getSaveAsRequirement().getTitle(), reqDTO.getText());
        }

        // 变更描述块（模块清单 + 需求条目 + 需求文本），供语义向量化与理由生成共用
        String changeData = buildChangeData(projectId, reqDTO);
        // 1. 模块名匹配（4.5 步骤 1）
        List<Candidate> moduleCandidates = hasModules ? matchModules(projectId, reqDTO.getModules()) : List.of();
        // 2. 语义匹配（4.5 步骤 2）：可用时向量 TopK，否则降级关键词 + 标题 ILIKE
        SemanticResult semantic = matchSemantic(userId, workspaceId, projectId, changeData, hasText || hasItems);
        // 3. 合并去重 + 排序截断（4.5 步骤 3）
        List<Candidate> ranked = mergeAndRank(moduleCandidates, semantic.candidates());
        // 4. 理由生成（4.5 步骤 4）：失败/长度不匹配整体置空，不影响清单可用
        List<String> reasons = buildReasons(userId, workspaceId, projectId, changeData, ranked);
        return response(ranked, reasons, semantic.degraded());
    }

    /**
     * 变更描述块：模块清单 + 需求条目（标题定界，按选取顺序）+ 需求文本；text 与条目超预算截断（同 4.3）
     */
    private String buildChangeData(UUID projectId, AiRegressionRecommendReqDTO reqDTO) {
        String prefixBlock = reqDTO.getModules() != null && !reqDTO.getModules().isEmpty()
                ? "【变更模块】" + String.join("、", reqDTO.getModules()) + "\n"
                : null;
        return requirementContextAssembler.assemble(projectId, reqDTO.getRequirementIds(),
                reqDTO.getText(), prefixBlock).data();
    }

    /**
     * 模块名匹配（4.5 步骤 1）：精确 1.0 / ILIKE 模糊 0.9，命中模块（含子孙目录）下全部 case 节点
     */
    private List<Candidate> matchModules(UUID projectId, List<String> modules) {
        List<TestCaseModule> allModules = testCaseModuleMapper.listByProjectId(projectId);
        if (allModules.isEmpty()) {
            return List.of();
        }
        AiModuleTreeSupport.ModuleIndex index = AiModuleTreeSupport.indexByParent(allModules);
        // 每个输入名称对全量模块取最优命中分（精确优先于模糊）
        Map<UUID, Double> matchedModuleScore = new LinkedHashMap<>();
        for (String name : modules) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            for (TestCaseModule module : allModules) {
                double score = 0;
                if (name.equals(module.getName())) {
                    score = EXACT_MODULE_SCORE;
                } else if (module.getName() != null
                        && module.getName().toLowerCase().contains(name.toLowerCase())) {
                    score = FUZZY_MODULE_SCORE;
                }
                if (score > 0) {
                    matchedModuleScore.merge(module.getId(), score, Math::max);
                }
            }
        }
        if (matchedModuleScore.isEmpty()) {
            return List.of();
        }
        // 命中模块（含子孙目录）收集文档 id；同一文档多模块命中取最高分
        Map<UUID, Double> documentScore = new LinkedHashMap<>();
        for (Map.Entry<UUID, Double> entry : matchedModuleScore.entrySet()) {
            Set<UUID> documents = new LinkedHashSet<>();
            AiModuleTreeSupport.collectDocumentIds(entry.getKey(), index.moduleById(), index.childrenByParent(), documents);
            for (UUID documentId : documents) {
                documentScore.merge(documentId, entry.getValue(), Math::max);
            }
        }
        if (documentScore.isEmpty()) {
            return List.of();
        }
        List<TestCaseNode> nodes = testCaseNodeMapper.listCaseNodesByDocumentIds(documentScore.keySet());
        Map<UUID, String> modulePathById = AiModuleTreeSupport.buildModulePaths(allModules);
        return nodes.stream()
                .map(node -> new Candidate(node.getId(), node.getTitle(),
                        modulePathById.getOrDefault(node.getDocumentId(), ""),
                        "module", documentScore.get(node.getDocumentId())))
                .toList();
    }

    /**
     * 语义匹配（4.5 步骤 2）：semanticSearch = available 时向量 TopK；
     * 未配置/降级/调用异常自动降级为 LLM 抽取关键词 + 标题 ILIKE（score 0.6，semanticDegraded=true）。
     */
    private SemanticResult matchSemantic(UUID userId, UUID workspaceId, UUID projectId,
                                         String changeData, boolean hasSemanticInput) {
        if (!hasSemanticInput || !StringUtils.hasText(changeData)) {
            return new SemanticResult(List.of(), false);
        }
        String semantic = aiConfigService.getStatus().getSemanticSearch();
        if (Constants.AiSemanticSearch.AVAILABLE.equals(semantic)) {
            try {
                int topK = aiConfigService.getIntSetting("regression.topK");
                double threshold = aiConfigService.getNumberSetting("regression.similarityThreshold");
                List<CaseDedupHit> hits = vectorSearchService.searchSimilarCases(projectId, changeData, topK, threshold);
                return new SemanticResult(toSemanticCandidates(projectId, hits), false);
            } catch (Exception e) {
                log.warn("[AI] 回归推荐语义检索失败，降级关键词匹配: {}", e.getMessage());
            }
        }
        return new SemanticResult(degradedKeywordCandidates(userId, workspaceId, projectId, changeData), true);
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
     * 降级关键词匹配（4.5 步骤 2）：LLM 抽取 ≤10 关键词 → 标题 ILIKE 每词取前 30，score = 0.6 仅排序用
     */
    private List<Candidate> degradedKeywordCandidates(UUID userId, UUID workspaceId, UUID projectId, String changeData) {
        List<String> keywords = aiKeywordExtractor.extract(userId, workspaceId, projectId,
                KEYWORD_TASK_INSTRUCTION, "【变更描述】", changeData);
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
     * 合并去重 + 排序截断（4.5 步骤 3）：同节点双命中 → matchType = both 取高分
     */
    private List<Candidate> mergeAndRank(List<Candidate> moduleCandidates, List<Candidate> semanticCandidates) {
        Map<UUID, Candidate> merged = new LinkedHashMap<>();
        for (Candidate candidate : moduleCandidates) {
            merged.put(candidate.nodeId(), candidate);
        }
        for (Candidate candidate : semanticCandidates) {
            merged.merge(candidate.nodeId(), candidate, (a, b) -> new Candidate(a.nodeId(), a.title(),
                    a.modulePath().isBlank() ? b.modulePath() : a.modulePath(),
                    "both", Math.max(a.score(), b.score())));
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .limit(RESULT_LIMIT)
                .toList();
    }

    /**
     * 理由生成（4.5 步骤 4）：一次 LLM 调用批量生成，长度不匹配/生成失败整体置空（不阻断清单返回）
     */
    private List<String> buildReasons(UUID userId, UUID workspaceId, UUID projectId,
                                      String changeData, List<Candidate> ranked) {
        if (ranked.isEmpty()) {
            return List.of();
        }
        StringBuilder data = new StringBuilder(changeData);
        data.append("【用例标题清单】\n");
        for (int i = 0; i < ranked.size(); i++) {
            data.append(i + 1).append(". ").append(ranked.get(i).title()).append('\n');
        }
        try {
            ReasonOut out = aiGatewayService.completeStructured(
                    new AiCallContext(userId, workspaceId, projectId),
                    AiFunctionType.REGRESSION_RECOMMENDATION,
                    REASON_TASK_INSTRUCTION,
                    data.toString(),
                    new ChatCallOptions(null, null, true, AiConstants.LLM_TIMEOUT_MILLIS),
                    ReasonOut.class,
                    null);
            List<String> reasons = out.getReasons();
            if (reasons == null || reasons.size() != ranked.size()) {
                log.warn("[AI] 回归推荐理由长度不匹配（{} vs {}），整体置空",
                        reasons == null ? 0 : reasons.size(), ranked.size());
                return List.of();
            }
            return reasons;
        } catch (Exception e) {
            log.warn("[AI] 回归推荐理由生成失败，整体置空: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 响应组装：items 按 score 降序；理由整体缺失时 reason 置空
     */
    private AiRegressionRecommendRespDTO response(List<Candidate> ranked, List<String> reasons,
                                                  boolean semanticDegraded) {
        AiRegressionRecommendRespDTO resp = new AiRegressionRecommendRespDTO();
        resp.setSemanticDegraded(semanticDegraded);
        List<AiRegressionRecommendRespDTO.Item> items = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Candidate candidate = ranked.get(i);
            AiRegressionRecommendRespDTO.Item item = new AiRegressionRecommendRespDTO.Item();
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
