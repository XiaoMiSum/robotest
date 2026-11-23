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
import io.github.xiaomisum.robotest.service.ai.config.AiPromptScenes;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewEvent;
import io.github.xiaomisum.robotest.service.domain.review.ReviewSnapshotService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewWorkflow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/** AI 评审适配器单测（C8）：校验矩阵、需求冻结、上下文装配与提案清洗。 */
@ExtendWith(MockitoExtension.class)
class ReviewCaseReviewTypeAdapterTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID FOREIGN_PROJECT_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();
    private static final UUID CASE_1 = UUID.randomUUID();
    private static final UUID CASE_2 = UUID.randomUUID();
    private static final UUID CASE_3 = UUID.randomUUID();
    private static final UUID FOREIGN_NODE_ID = UUID.randomUUID();

    @Mock
    private TestReviewMapper testReviewMapper;
    @Mock
    private ReviewSnapshotService reviewSnapshotService;
    @Mock
    private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private ReviewWorkflow reviewWorkflow;
    @Mock
    private VectorSearchService vectorSearchService;
    @InjectMocks
    private ReviewCaseReviewTypeAdapter adapter;

    @Test
    void typePromptAndSceneRegistration() {
        assertEquals(Constants.AiTaskType.REVIEW_CASE_REVIEW, adapter.type());
        assertTrue(adapter.systemPrompt().contains("{{reviewCaseContext}}"));
        assertTrue(adapter.systemPrompt().contains("{{requirementContext}}"));
        assertTrue(adapter.systemPrompt().contains("{{ragContext}}"));
        assertTrue(adapter.toolCodes().isEmpty());
        AiPromptScenes.PromptScene scene = AiPromptScenes.get(adapter.type());
        assertNotNull(scene);
        assertEquals(List.of("reviewCaseContext", "requirementContext", "ragContext"),
                scene.variables().stream().map(AiPromptScenes.PromptVariable::name).toList());
    }

    @Test
    void validateInput_requiresReviewId() {
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> adapter.validateInput(Map.of())).getCode());
    }

    @Test
    void validateInputWithContext_missingProjectHeaderRejected() {
        assertEquals(ErrorCodeConstants.CONTEXT_HEADER_MISSING.code(),
                assertThrows(ServiceException.class,
                        () -> adapter.validateInput(input(), new TaskSubmitContext(null, null, null, null)))
                        .getCode());
    }

    @Test
    void validateInputWithContext_foreignReviewRejected() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(FOREIGN_PROJECT_ID));

        assertEquals(ErrorCodeConstants.REVIEW_TASK_NOT_FOUND.code(),
                assertThrows(ServiceException.class, () -> adapter.validateInput(input(), context())).getCode());
    }

    @Test
    void validateInputWithContext_terminalReviewRejected() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(PROJECT_ID));
        when(reviewWorkflow.can(any(), any(ReviewEvent.class))).thenReturn(false);

        assertEquals(ErrorCodeConstants.REVIEW_TASK_STATUS_INVALID.code(),
                assertThrows(ServiceException.class, () -> adapter.validateInput(input(), context())).getCode());
    }

    @Test
    void validateInputWithContext_noAssociatedCasesRejected() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(PROJECT_ID));
        when(reviewWorkflow.can(any(), any(ReviewEvent.class))).thenReturn(true);
        when(reviewSnapshotService.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(List.of());

        assertEquals(ErrorCodeConstants.REVIEW_TASK_CASES_EMPTY.code(),
                assertThrows(ServiceException.class, () -> adapter.validateInput(input(), context())).getCode());
    }

    @Test
    void validateInputWithContext_success_freezesRequirementSnapshot() {
        Map<String, Object> input = input();
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(PROJECT_ID));
        when(reviewWorkflow.can(any(), any(ReviewEvent.class))).thenReturn(true);
        when(reviewSnapshotService.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(cases());
        Requirement requirement = new Requirement();
        requirement.setId(REQUIREMENT_ID);
        requirement.setProjectId(PROJECT_ID);
        requirement.setCode("REQ-001");
        requirement.setTitle("短信登录");
        requirement.setDescription("支持短信验证码登录");
        when(requirementMapper.listByIds(List.of(REQUIREMENT_ID))).thenReturn(List.of(requirement));

        adapter.validateInput(input, context());

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) input.get("requirementSnapshot");
        assertNotNull(snapshot);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requirements = (List<Map<String, Object>>) snapshot.get("requirements");
        assertEquals(1, requirements.size());
        assertEquals(REQUIREMENT_ID.toString(), requirements.get(0).get("id"));
        assertEquals("REQ-001", requirements.get(0).get("code"));
        assertEquals("支持短信验证码登录", requirements.get(0).get("content"));
    }

    @Test
    void prepare_assemblesCaseRequirementAndRagContext() {
        AiVectorSearchHitRespDTO hit = new AiVectorSearchHitRespDTO();
        hit.setEntityId(REQUIREMENT_ID);
        hit.setContent("验证码错误时提示具体原因");
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(allNodes());
        when(reviewSnapshotService.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(cases());
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of(hit));

        AgentExecutionPreparation preparation = prepare(input());

        String caseContext = preparation.promptVariables().get("reviewCaseContext");
        assertTrue(caseContext.contains("获取验证码"));
        assertTrue(caseContext.contains("步骤 1"));
        assertTrue(preparation.promptVariables().get("requirementContext").contains("REQ-001"));
        assertTrue(preparation.promptVariables().get("ragContext").contains("验证码错误时提示具体原因"));
    }

    @Test
    void prepare_degradesWhenVectorUnavailable() {
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(allNodes());
        when(reviewSnapshotService.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(cases());
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE));

        AgentExecutionPreparation preparation = prepare(input());

        assertEquals("", preparation.promptVariables().get("ragContext"));
        assertTrue(preparation.promptVariables().get("reviewCaseContext").contains("校验手机号"));
    }

    @Test
    void proposalParser_dropsIllegalNodesAndMissingSources() {
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(allNodes());
        when(reviewSnapshotService.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(cases());
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of());
        AgentExecutionPreparation preparation = prepare(input());

        String output = """
                {"artifacts":[
                  {"content":{"snapshotNodeId":"%s","caseTitle":"获取验证码","suggestedMark":"fail",
                    "reason":"缺少错误分支预期",
                    "issues":[{"severity":"medium","category":"预期不完整","description":"未覆盖错误输入","path":"获取验证码 › 步骤 2"}],
                    "sourceRefs":[{"type":"requirement","id":"%s","title":"REQ-001","quote":"验证码错误时提示"},
                                  {"type":"requirement","id":"%s","title":"越界","quote":"越界来源"}]}},
                  {"content":{"snapshotNodeId":"%s","suggestedMark":"fail","reason":"无来源",
                    "issues":[{"severity":"high","category":"缺失","description":"…","path":"…"}],
                    "sourceRefs":[{"type":"requirement","id":"%s","quote":"越界来源"}]}},
                  {"content":{"snapshotNodeId":"%s","suggestedMark":"pass","sourceRefs":[{"id":"%s"}]}},
                  {"content":{"snapshotNodeId":"%s","suggestedMark":"pass","reason":"覆盖充分","issues":[],
                    "sourceRefs":[{"type":"requirement","id":"%s","quote":"支持短信验证码登录"}]}}
                ]}
                """.formatted(CASE_1, REQUIREMENT_ID, UUID.randomUUID(), CASE_2, UUID.randomUUID(),
                FOREIGN_NODE_ID, REQUIREMENT_ID, CASE_3, REQUIREMENT_ID);

        List<ProposalDraft> drafts = preparation.proposalParser().apply(output);

        assertEquals(2, drafts.size());
        ProposalDraft first = drafts.get(0);
        assertEquals("case-1", first.proposalKey());
        assertEquals(Constants.AiProposalKind.REVIEW_SUGGESTION, first.kind());
        assertEquals(Constants.AiProposalConfirmStatus.PENDING, first.confirmStatus());
        assertEquals("获取验证码", first.title());
        Map<String, Object> content = first.content();
        assertEquals(REVIEW_ID.toString(), content.get("reviewId"));
        assertEquals(CASE_1.toString(), content.get("snapshotNodeId"));
        assertEquals("fail", content.get("suggestedMark"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issues = (List<Map<String, Object>>) content.get("issues");
        assertEquals(1, issues.size());
        assertEquals("medium", issues.get(0).get("severity"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refs = (List<Map<String, Object>>) content.get("sourceRefs");
        assertEquals(1, refs.size());
        assertEquals("REQ-001", refs.get(0).get("title"));
        assertEquals(REQUIREMENT_ID.toString(), refs.get(0).get("id"));

        ProposalDraft second = drafts.get(1);
        assertEquals("case-2", second.proposalKey());
        assertEquals("校验手机号", second.title());
        assertEquals("pass", second.content().get("suggestedMark"));
        assertEquals(List.of(), second.content().get("issues"));
    }

    @Test
    void proposalParser_allIllegal_failsModel() {
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(allNodes());
        when(reviewSnapshotService.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(cases());
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of());
        AgentExecutionPreparation preparation = prepare(input());

        ServiceException error = assertThrows(ServiceException.class,
                () -> preparation.proposalParser().apply("""
                        {"artifacts":[{"content":{"snapshotNodeId":"%s","suggestedMark":"pass",
                        "sourceRefs":[{"id":"%s"}]}}]}
                        """.formatted(FOREIGN_NODE_ID, REQUIREMENT_ID)));
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), error.getCode());
        assertTrue(error.getMessage().contains("invalid_node=1"));
    }

    @Test
    void proposalParser_reportsDropReasonsWhenAllInvalid() {
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(allNodes());
        when(reviewSnapshotService.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(cases());
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of());
        AgentExecutionPreparation preparation = prepare(input());

        ServiceException error = assertThrows(ServiceException.class,
                () -> preparation.proposalParser().apply("""
                        {"artifacts":[
                          {"content":{"snapshotNodeId":"%s","suggestedMark":"fail","reason":"缺来源",
                            "issues":[{"severity":"high","category":"缺失","description":"...","path":"p"}],
                            "sourceRefs":[{"type":"requirement","id":"%s","quote":"越界"}]}},
                          {"content":{"snapshotNodeId":"%s","suggestedMark":"unknown","reason":"bad",
                            "issues":[],"sourceRefs":[{"type":"requirement","id":"%s","quote":"q"}]}}
                        ]}
                        """.formatted(CASE_1, UUID.randomUUID(), CASE_2, REQUIREMENT_ID)));
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), error.getCode());
        assertTrue(error.getMessage().contains("missing_source_refs=1"));
        assertTrue(error.getMessage().contains("invalid_mark=1"));
    }

    private AgentExecutionPreparation prepare(Map<String, Object> input) {
        return adapter.prepare(new TaskTypeAdapter.PreparationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PROJECT_ID, input, null));
    }

    private static Map<String, Object> input() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("reviewId", REVIEW_ID.toString());
        input.put("requirementSnapshot", Map.of("requirements", List.of(Map.of(
                "id", REQUIREMENT_ID.toString(), "code", "REQ-001", "title", "短信登录",
                "content", "支持短信验证码登录"))));
        return input;
    }

    private static TaskSubmitContext context() {
        return new TaskSubmitContext(PROJECT_ID, UUID.randomUUID(), UUID.randomUUID(), null);
    }

    private static TestReview review(UUID projectId) {
        TestReview review = new TestReview();
        review.setId(REVIEW_ID);
        review.setProjectId(projectId);
        review.setStatus(Constants.Status.NEW);
        review.setRequirementIds(List.of(REQUIREMENT_ID));
        return review;
    }

    private static List<TestReviewNodeSnapshot> cases() {
        return List.of(node(CASE_1, "获取验证码", null), node(CASE_2, "提交登录", null),
                node(CASE_3, "校验手机号", null));
    }

    private static List<TestReviewNodeSnapshot> allNodes() {
        TestReviewNodeSnapshot root = node(UUID.randomUUID(), "登录模块", null);
        root.setType(Constants.NodeType.NORMAL);
        TestReviewNodeSnapshot first = node(CASE_1, "获取验证码", root.getId());
        TestReviewNodeSnapshot step = node(UUID.randomUUID(), "步骤 1", first.getId());
        step.setType(Constants.NodeType.STEP);
        step.setIsAssociated(false);
        return List.of(root, first, step, node(CASE_2, "提交登录", root.getId()),
                node(CASE_3, "校验手机号", root.getId()));
    }

    private static TestReviewNodeSnapshot node(UUID id, String title, UUID parentId) {
        TestReviewNodeSnapshot node = new TestReviewNodeSnapshot();
        node.setId(id);
        node.setReviewId(REVIEW_ID);
        node.setParentId(parentId);
        node.setTitle(title);
        node.setType(Constants.NodeType.CASE);
        node.setPriority("P1");
        node.setIsAssociated(true);
        return node;
    }
}
