package io.github.xiaomisum.robotest.service.ai.casegen;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiMissingPointReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMissingPointRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import io.github.xiaomisum.robotest.service.ai.support.AiConstants;
import io.github.xiaomisum.robotest.service.ai.support.AiModuleTreeSupport;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.support.AiRequirementContextAssembler;
import io.github.xiaomisum.robotest.service.ai.support.ModuleTreeNode;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AI 遗漏测试点分析实现（详细设计 3.3 / 4.3，文档级全量候选）：
 * <ol>
 *   <li>需求输入归一：text / 需求条目合并为需求描述块（超预算截断，同生成类裁剪规则）；</li>
 *   <li>候选检索：按 documentIds 取所选文档下全部 case 节点，组装目录树 + 文档的模块路径；</li>
 *   <li>LLM 比对（读超时功能级覆盖 300s）：输出遗漏点，结构断言 suggestedModulePath 必须来自候选模块路径；</li>
 *   <li>relatedCaseTitles 与候选清单比对过滤（防幻觉）。</li>
 * </ol>
 * 候选清单整体按 CANDIDATE_TOKEN_BUDGET 截断，超预算部分静默丢弃。
 */
@Service
public class AiMissingPointServiceImpl implements AiMissingPointService {

    /** 候选清单整体 token 预算（防御性：避免超出 PromptAssembler 输入预算，超出静默截断） */
    static final int CANDIDATE_TOKEN_BUDGET = 8_000;

    private static final String TASK_INSTRUCTION = """
            请对比需求描述与候选用例清单，找出需求已提及但现有候选用例未覆盖的测试点。
            输出单个 JSON 对象，仅含 points 数组，每个遗漏点包含：title（建议新增用例标题）、description（遗漏原因说明）、\
            suggestedModulePath（建议归属模块路径，必须来自候选用例清单中出现过的模块路径或留空）、\
            relatedCaseTitles（关联的候选用例标题，仅允许引用候选用例清单中真实存在的标题，无关联时为空数组）。\
            输出结构必须严格遵循如下示例（字段名、类型、层级完全一致）：\
            {"points": [{"title": "建议新增的用例标题", "description": "遗漏原因说明", "suggestedModulePath": "建议归属模块路径", "relatedCaseTitles": ["关联的候选用例标题"]}]}。\
            遗漏点应与候选用例互补：需求已覆盖的测试点不要重复输出。""";

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private AiRequirementContextAssembler requirementContextAssembler;

    @Override
    public AiMissingPointRespDTO analyze(UUID userId, UUID workspaceId, UUID projectId, AiMissingPointReqDTO reqDTO) {
        // documentIds 不可空（DTO @NotEmpty 已拦，此处防御）；text / requirementIds 至少一项非空（3.3）
        boolean hasDocs = reqDTO.getDocumentIds() != null && !reqDTO.getDocumentIds().isEmpty();
        boolean hasText = StringUtils.hasText(reqDTO.getText());
        boolean hasItems = reqDTO.getRequirementIds() != null && !reqDTO.getRequirementIds().isEmpty();
        if (!hasDocs || (!hasText && !hasItems)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        // 1. 需求输入归一（4.3）
        String requirementData = requirementContextAssembler.assemble(projectId,
                reqDTO.getRequirementIds(), reqDTO.getText(), null).data();
        // 2. 候选检索（文档级全量） + 3. LLM 比对（4.3）
        ComparisonContext comparison = buildComparisonData(requirementData,
                retrieveCandidates(projectId, reqDTO.getDocumentIds()));
        AiCallContext context = new AiCallContext(userId, workspaceId, projectId, reqDTO.getModelId());
        MissingPointOut out = aiGatewayService.completeStructured(
                context,
                AiFunctionType.MISSING_POINT_ANALYSIS,
                TASK_INSTRUCTION,
                comparison.data(),
                new ChatCallOptions(null, null, true, AiConstants.LLM_TIMEOUT_MILLIS),
                MissingPointOut.class,
                points -> assertModulePaths(points, comparison.modulePaths()));
        // 4. relatedCaseTitles 幻觉过滤（4.3 步骤 4）
        return response(out, comparison.titles());
    }

    /** 候选检索：按 documentIds 取全部 case 节点（无关键词、无限额），附目录树 + 文档的模块路径 */
    private List<Candidate> retrieveCandidates(UUID projectId, List<UUID> documentIds) {
        List<TestCaseDocument> documents = testCaseDocumentMapper.selectBatchIds(documentIds);
        if (documents.isEmpty()) {
            return List.of();
        }
        List<ModuleTreeNode> allNodes = new ArrayList<>();
        projectModuleMapper.listByProjectId(projectId).forEach(m -> allNodes.add(ModuleTreeNode.fromProjectModule(m)));
        documents.forEach(d -> allNodes.add(ModuleTreeNode.fromTestCaseDocument(d)));
        Map<UUID, String> modulePathById = AiModuleTreeSupport.buildModulePaths(allNodes);
        List<Candidate> candidates = new ArrayList<>();
        for (TestCaseNode node : testCaseNodeMapper.listCaseNodesByDocumentIds(documentIds)) {
            candidates.add(new Candidate(node.getId(), node.getTitle(),
                    modulePathById.getOrDefault(node.getDocumentId(), "")));
        }
        return candidates;
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
