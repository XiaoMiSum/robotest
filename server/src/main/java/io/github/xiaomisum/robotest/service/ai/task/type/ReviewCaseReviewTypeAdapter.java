package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewNodeSnapshotMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewEvent;
import io.github.xiaomisum.robotest.service.domain.review.ReviewSnapshotService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewWorkflow;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.abbreviate;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.modelFailed;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parsedArtifacts;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parseUuid;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ReviewContextSupport.ragContext;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ReviewContextSupport.requirementContext;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ReviewContextSupport.reviewCaseContext;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ReviewContextSupport.sanitizeContentWithReason;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ReviewContextSupport.snapshotRequirementsById;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ReviewContextSupport.snapshotOf;

/**
 * AI 评审规划用例（详设 4.1 / 4.2）：提交期冻结关联需求快照并校验归属 / 活跃态 / 用例集，
 * 执行期以用例与需求快照装配上下文，逐关联用例产出一条 review_suggestion 提案待人工采纳。
 */
@Slf4j
@Component
public class ReviewCaseReviewTypeAdapter extends AbstractOrdinaryTaskTypeAdapter {

    public static final String TYPE = Constants.AiTaskType.REVIEW_CASE_REVIEW;

    private static final int RAG_TOP_K = 8;
    private static final int MAX_TITLE_CHARS = 200;

    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private ReviewSnapshotService reviewSnapshotService;
    @Resource
    private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private ReviewWorkflow reviewWorkflow;
    @Resource
    private VectorSearchService vectorSearchService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深软件测试评审专家。针对下方每条关联规划用例给出评审建议，只输出 JSON，
                不输出解释或代码块标记以外的任何文字。
                1. artifacts 数组逐用例一条，content.snapshotNodeId 必须取自「关联规划用例」清单中的 id，
                   每条用例只输出一条建议，不得引用清单外的节点；
                2. content.suggestedMark 只能是 pass 或 fail（不得输出 pending）；
                   fail 时 issues 至少一项，每项含 severity（high / medium / low）、category、description、path；
                   pass 时 issues 为空数组；
                3. content.reason 给出简短理由，采纳时写入评审记录；
                4. content.sourceRefs 必填，只引用「关联需求」快照中给出的需求 id，
                   每项含 type=requirement、id、title、quote（需求原文摘录，至多 300 字）；
                   无有效来源的建议不要输出；
                5. 只依据下方用例与需求快照判断，不读取评审记录、历史缺陷或执行数据；
                   缺失维度显式留空，不臆造。
                输出结构（JSON 对象）：
                {"artifacts":[{"content":{"snapshotNodeId":"…","caseTitle":"…",
                "suggestedMark":"fail","reason":"…",
                "issues":[{"severity":"medium","category":"预期不完整","description":"…","path":"…"}],
                "sourceRefs":[{"type":"requirement","id":"…","title":"REQ-001","quote":"…"}]}}]}

                {{reviewCaseContext}}

                {{requirementContext}}

                {{ragContext}}
                """;
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        requireReviewId(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        UUID reviewId = requireReviewId(input);
        if (context == null || context.projectId() == null) {
            // 评审归属与可见范围依赖 X-Active-Project（C4），头缺失不冒充输入非法
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_MISSING);
        }
        UUID projectId = context.projectId();
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null || !projectId.equals(review.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_TASK_NOT_FOUND);
        }
        if (!reviewWorkflow.can(review, ReviewEvent.SUBMIT_RECORD)) {
            // 状态机只放行 new / in_progress 的标记事件，终态在此统一拦截
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_TASK_STATUS_INVALID);
        }
        List<TestReviewNodeSnapshot> associated = reviewSnapshotService
                .listAssociatedByReviewId(reviewId, Constants.NodeType.CASE);
        if (associated.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_TASK_CASES_EMPTY);
        }
        input.put("requirementSnapshot", snapshotOf(requirementsOf(projectId, review)));
    }

    @Override
    public AgentExecutionPreparation prepare(TaskTypeAdapter.PreparationContext context) {
        Map<String, Object> input = context.input() == null ? Map.of() : context.input();
        UUID reviewId = requireReviewId(input);
        Map<String, Object> snapshot = asMap(input.get("requirementSnapshot"));
        if (snapshotRequirementsById(snapshot).isEmpty()) {
            TestReview review = testReviewMapper.selectById(reviewId);
            if (review != null && context.projectId() != null) {
                snapshot = snapshotOf(requirementsOf(context.projectId(), review));
            }
        }
        Map<UUID, Map<String, Object>> requirementsById = snapshotRequirementsById(snapshot);

        List<TestReviewNodeSnapshot> allNodes = reviewNodeSnapshotMapper.listByReviewId(reviewId);
        List<TestReviewNodeSnapshot> associated = reviewSnapshotService
                .listAssociatedByReviewId(reviewId, Constants.NodeType.CASE);
        Map<UUID, TestReviewNodeSnapshot> associatedById = associated.stream()
                .collect(Collectors.toMap(TestReviewNodeSnapshot::getId, node -> node, (left, right) -> left,
                        LinkedHashMap::new));

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("reviewCaseContext", reviewCaseContext(associated, allNodes));
        variables.put("requirementContext", requirementContext(snapshot));
        variables.put("ragContext", ragContextOf(context.projectId(), context.userId(), requirementsById));

        return new AgentExecutionPreparation(variables, String.join("\n", variables.values()), List.of(),
                output -> parseCases(output, reviewId, associated, associatedById, requirementsById),
                output -> null);
    }

    /** 无工具：用例与需求快照、RAG 已在 prepare 装配（工具白名单为空） */
    @Override
    public List<String> toolCodes() {
        return List.of();
    }

    @Override
    protected String defaultProposalKind() {
        return Constants.AiProposalKind.REVIEW_SUGGESTION;
    }

    private static UUID requireReviewId(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("reviewId");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /** 需求按评审关联顺序冻结，跨项目需求忽略，避免把越权文本带入提示词 */
    private List<Requirement> requirementsOf(UUID projectId, TestReview review) {
        if (review.getRequirementIds() == null || review.getRequirementIds().isEmpty()) {
            return List.of();
        }
        Map<UUID, Requirement> found = requirementMapper.listByIds(review.getRequirementIds()).stream()
                .collect(Collectors.toMap(Requirement::getId, item -> item, (left, right) -> left));
        List<Requirement> scoped = new ArrayList<>();
        for (UUID id : review.getRequirementIds()) {
            Requirement requirement = found.get(id);
            if (requirement != null && projectId.equals(requirement.getProjectId())) {
                scoped.add(requirement);
            }
        }
        return scoped;
    }

    /** RAG 增强：向量未配置 / 检索失败时降级为无检索，评审按冻结快照继续 */
    private String ragContextOf(UUID projectId, UUID userId, Map<UUID, Map<String, Object>> requirementsById) {
        if (projectId == null || requirementsById.isEmpty()) {
            return "";
        }
        String query = requirementsById.values().stream()
                .map(item -> trimToNull(asString(item.get("title"))))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" "));
        if (query.isBlank()) {
            return "";
        }
        try {
            List<AiVectorSearchHitRespDTO> hits = vectorSearchService.search(query,
                    List.of(projectId), Constants.EntityType.REQUIREMENT, RAG_TOP_K, userId);
            List<AiVectorSearchHitRespDTO> scoped = hits.stream()
                    .filter(hit -> requirementsById.containsKey(hit.getEntityId()))
                    .toList();
            return ragContext(scoped);
        } catch (ServiceException e) {
            // 向量底座未就绪属可降级场景，不阻断本次评审
            log.debug("[AI] AI 评审 RAG 不可用，降级为无检索：{}", e.getMessage());
            return "";
        }
    }

    /** 按关联用例顺序逐条产提案；非法输出（节点越界 / 来源缺失 / 结论非法）在清洗阶段丢弃 */
    private List<ProposalDraft> parseCases(String output, UUID reviewId,
            List<TestReviewNodeSnapshot> associated,
            Map<UUID, TestReviewNodeSnapshot> associatedById,
            Map<UUID, Map<String, Object>> requirementsById) {
        Map<UUID, Map<String, Object>> sanitizedByNode = new LinkedHashMap<>();
        Map<String, Integer> dropReasons = new LinkedHashMap<>();
        List<Map<String, Object>> parsed = parsedArtifacts(new AiChatReply(output, 0, 0));
        for (Map<String, Object> item : parsed) {
            Map<String, Object> rawContent = asMap(item.get("content"));
            var outcome = sanitizeContentWithReason(rawContent, reviewId, associatedById, requirementsById);
            if (!outcome.accepted()) {
                dropReasons.merge(outcome.dropReason(), 1, Integer::sum);
                log.debug("[AI] 丢弃非法评审建议 reviewId={} reason={} snapshotNodeId={}",
                        reviewId, outcome.dropReason(), rawContent.get("snapshotNodeId"));
                continue;
            }
            Map<String, Object> content = outcome.content();
            UUID nodeId = parseUuid(content.get("snapshotNodeId"));
            if (nodeId == null) {
                dropReasons.merge("invalid_node", 1, Integer::sum);
                continue;
            }
            if (sanitizedByNode.putIfAbsent(nodeId, content) != null) {
                dropReasons.merge("duplicate_node", 1, Integer::sum);
            }
        }

        List<Map<String, Object>> artifacts = new ArrayList<>();
        int index = 0;
        for (TestReviewNodeSnapshot node : associated) {
            Map<String, Object> content = sanitizedByNode.get(node.getId());
            if (content == null) {
                continue;
            }
            index++;
            String title = trimToNull(asString(content.get("caseTitle")));
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("key", "case-" + index);
            artifact.put("kind", Constants.AiProposalKind.REVIEW_SUGGESTION);
            artifact.put("parentKey", null);
            artifact.put("title", abbreviate(title == null ? nvl(node.getTitle()) : title, MAX_TITLE_CHARS));
            artifact.put("content", content);
            artifact.put("confirmStatus", Constants.AiProposalConfirmStatus.PENDING);
            artifacts.add(artifact);
        }
        if (artifacts.isEmpty()) {
            String summary = dropSummary(dropReasons);
            log.warn("[AI] 评审建议清洗后为空 reviewId={} associatedCases={} artifacts={} {} output={}",
                    reviewId, associated.size(), parsed.size(), summary, abbreviate(output, 1200));
            throw modelFailed("模型未产出有效评审建议：" + summary);
        }
        return ProposalDrafts.fromArtifacts(artifacts);
    }

    private static String dropSummary(Map<String, Integer> dropReasons) {
        if (dropReasons == null || dropReasons.isEmpty()) {
            return "无可解析产物";
        }
        return dropReasons.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }
}
