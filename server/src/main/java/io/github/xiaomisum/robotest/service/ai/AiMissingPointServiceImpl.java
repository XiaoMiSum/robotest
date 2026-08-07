package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiMissingPointReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMissingPointRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AI 遗漏测试点分析实现（详细设计 3.3 / 4.3，关键词版）：
 * <ol>
 *   <li>需求输入归一：keywords / text / 需求条目合并为需求描述块（text 与条目内容超预算截断，同生成类裁剪规则）；</li>
 *   <li>关键词模式：text 场景由 LLM 先抽取 ≤10 个关键词（一次同步调用），否则直接用入参 keywords；</li>
 *   <li>候选检索：每词对项目内 case 节点标题 ILIKE 取前 30 条，去重并组装模块路径；</li>
 *   <li>LLM 比对（读超时功能级覆盖 60s）：输出遗漏点，结构断言 suggestedModulePath 必须来自候选模块路径；</li>
 *   <li>relatedCaseTitles 与候选清单比对过滤（防幻觉）。</li>
 * </ol>
 * 本梯队仅关键词模式，semanticDegraded 恒 true；语义升级（梯队三）按 semanticSearch 能力翻转。
 */
@Service
public class AiMissingPointServiceImpl implements AiMissingPointService {

    /** 候选清单整体 token 预算（防御性：避免超出 PromptAssembler 输入预算，超出静默截断） */
    static final int CANDIDATE_TOKEN_BUDGET = 8_000;

    private static final String TASK_INSTRUCTION = """
            请对比需求描述与候选用例清单，找出需求已提及但现有候选用例未覆盖的测试点。
            每个遗漏点包含：title（建议新增用例标题）、description（遗漏原因说明）、\
            suggestedModulePath（建议归属模块路径，必须来自候选用例清单中出现过的模块路径或留空）、\
            relatedCaseTitles（关联的候选用例标题，仅允许引用候选用例清单中真实存在的标题，无关联时为空数组）。\
            遗漏点应与候选用例互补：需求已覆盖的测试点不要重复输出。""";

    private static final String KEYWORD_TASK_INSTRUCTION = """
            请从给定需求文本中抽取用于在测试用例库中检索的 ≤10 个关键词。
            关键词应为需求中出现过的核心业务词或短语，按重要程度取前 10，避免空泛词汇。""";

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private AiKeywordExtractor aiKeywordExtractor;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private AiRequirementContextAssembler requirementContextAssembler;

    @Override
    public AiMissingPointRespDTO analyze(UUID userId, UUID workspaceId, UUID projectId, AiMissingPointReqDTO reqDTO) {
        // 三种输入（keywords / text / requirementIds）至少一项非空（3.3）
        boolean hasKeywords = reqDTO.getKeywords() != null && !reqDTO.getKeywords().isEmpty();
        boolean hasText = StringUtils.hasText(reqDTO.getText());
        boolean hasItems = reqDTO.getRequirementIds() != null && !reqDTO.getRequirementIds().isEmpty();
        if (!hasKeywords && !hasText && !hasItems) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        // saveAsRequirement 非空时 text 必填；分析开始前独立保存，保存失败不阻断分析（3.3，同 3.2.1）
        if (reqDTO.getSaveAsRequirement() != null) {
            if (!hasText) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            requirementContextAssembler.trySaveRequirement(projectId, userId,
                    reqDTO.getSaveAsRequirement().getTitle(), reqDTO.getText());
        }

        // 1. 需求输入归一（4.3）
        String prefixBlock = reqDTO.getKeywords() != null && !reqDTO.getKeywords().isEmpty()
                ? "【需求关键词】" + String.join("、", reqDTO.getKeywords()) + "\n"
                : null;
        String requirementData = requirementContextAssembler.assemble(projectId,
                reqDTO.getRequirementIds(), reqDTO.getText(), prefixBlock).data();
        // 2. 关键词：入参非空直接用；text/需求条目场景由 LLM 抽取（一次同步调用）
        List<String> keywords = hasKeywords ? reqDTO.getKeywords()
                : aiKeywordExtractor.extract(userId, workspaceId, projectId,
                        KEYWORD_TASK_INSTRUCTION, "【需求描述】", requirementData);
        // 3. 候选检索 + 4. LLM 比对（4.3）
        ComparisonContext comparison = buildComparisonData(requirementData, retrieveCandidates(projectId, keywords));
        AiCallContext context = new AiCallContext(userId, workspaceId, projectId);
        MissingPointOut out = aiGatewayService.completeStructured(
                context,
                AiFunctionType.MISSING_POINT_ANALYSIS,
                TASK_INSTRUCTION,
                comparison.data(),
                new ChatCallOptions(null, null, true, AiConstants.LLM_TIMEOUT_MILLIS),
                MissingPointOut.class,
                points -> assertModulePaths(points, comparison.modulePaths()));
        // 5. relatedCaseTitles 幻觉过滤（4.3 步骤 4）
        return response(out, comparison.titles());
    }

    /** 候选检索：每词对项目内 case 节点标题 ILIKE 取前 30，跨词去重，附模块路径 */
    private List<Candidate> retrieveCandidates(UUID projectId, List<String> keywords) {
        List<String> effective = keywords.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (effective.isEmpty()) {
            return List.of();
        }
        List<TestCaseModule> documents = testCaseModuleMapper.findDocumentModulesByProjectId(projectId);
        List<UUID> documentIds = documents.stream().map(TestCaseModule::getId).toList();
        if (documentIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> modulePathById = AiModuleTreeSupport.buildModulePaths(testCaseModuleMapper.listByProjectId(projectId));
        Map<UUID, Candidate> candidates = new LinkedHashMap<>();
        for (String keyword : effective) {
            List<TestCaseNode> nodes = testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(
                    documentIds, keyword, AiConstants.CANDIDATE_LIMIT_PER_KEYWORD);
            for (TestCaseNode node : nodes) {
                if (candidates.containsKey(node.getId())) {
                    continue;
                }
                candidates.put(node.getId(), new Candidate(node.getId(), node.getTitle(),
                        modulePathById.getOrDefault(node.getDocumentId(), "")));
            }
        }
        return new ArrayList<>(candidates.values());
    }

    /** 组装 LLM 比对输入：需求描述块 + 候选用例（标题 + 模块路径清单）；记录实际入参候选供断言与过滤 */
    private ComparisonContext buildComparisonData(String requirementData, List<Candidate> candidates) {
        StringBuilder data = new StringBuilder();
        if (!requirementData.isBlank()) {
            data.append(requirementData);
        }
        data.append("【候选用例清单】\n");
        Set<String> modulePaths = new LinkedHashSet<>();
        Set<String> titles = new LinkedHashSet<>();
        int used = 0;
        for (Candidate candidate : candidates) {
            String line = candidate.title() + (candidate.modulePath().isBlank() ? "" : "｜模块：" + candidate.modulePath()) + "\n";
            int tokens = PromptAssembler.estimateTokens(line);
            if (used + tokens > CANDIDATE_TOKEN_BUDGET) {
                // 防御性截断：候选清单过大时静默丢弃后续，避免整体输入预算失守
                break;
            }
            data.append(line);
            modulePaths.add(candidate.modulePath());
            titles.add(candidate.title());
            used += tokens;
        }
        return new ComparisonContext(data.toString(), modulePaths, titles);
    }

    /** 结构断言：suggestedModulePath 必须为候选清单中出现过的模块路径或空（4.3 步骤 3） */
    private void assertModulePaths(MissingPointOut out, Set<String> modulePaths) {
        if (out.getPoints() == null) {
            throw new AiOutputValidator.OutputValidationException("points 不能为空");
        }
        for (MissingPointOut.Point point : out.getPoints()) {
            String path = point.getSuggestedModulePath();
            if (StringUtils.hasText(path) && !modulePaths.contains(path)) {
                throw new AiOutputValidator.OutputValidationException(
                        "suggestedModulePath 必须为候选中出现过的模块路径或空");
            }
        }
    }

    /** 响应组装 + relatedCaseTitles 幻觉过滤（仅保留候选清单中真实存在的标题，4.3 步骤 4） */
    private AiMissingPointRespDTO response(MissingPointOut out, Set<String> candidateTitles) {
        AiMissingPointRespDTO resp = new AiMissingPointRespDTO();
        resp.setSemanticDegraded(true);
        List<AiMissingPointRespDTO.Point> points = new ArrayList<>();
        for (MissingPointOut.Point point : out.getPoints()) {
            AiMissingPointRespDTO.Point respPoint = new AiMissingPointRespDTO.Point();
            respPoint.setTitle(point.getTitle());
            respPoint.setDescription(point.getDescription());
            respPoint.setSuggestedModulePath(point.getSuggestedModulePath());
            List<String> kept = point.getRelatedCaseTitles() == null ? List.of()
                    : point.getRelatedCaseTitles().stream().filter(candidateTitles::contains).toList();
            respPoint.setRelatedCaseTitles(kept);
            points.add(respPoint);
        }
        resp.setPoints(points);
        return resp;
    }

    /** 候选用例（节点 + 标题 + 所属文档的模块树路径） */
    private record Candidate(UUID nodeId, String title, String modulePath) {
    }

    /** LLM 比对输入 + 实际入参的模块路径/标题集合（供结构断言与幻觉过滤） */
    private record ComparisonContext(String data, Set<String> modulePaths, Set<String> titles) {
    }

    /** LLM 结构化输出：遗漏点数组（结构校验见 4.3 步骤 3，经 Bean Validation 与自定义断言双重兜底） */
    @Data
    public static class MissingPointOut {

        @NotNull(message = "points 不能为空")
        @Size(max = 30, message = "遗漏点数量不能超过 30")
        private List<Point> points;

        @Data
        public static class Point {

            @NotBlank(message = "title 不能为空")
            @Size(max = 200, message = "title 不能超过 200 字符")
            private String title;

            @NotBlank(message = "description 不能为空")
            private String description;

            private String suggestedModulePath;

            private List<String> relatedCaseTitles;
        }
    }
}
