package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewRecord;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewRecordMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptFixtures;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.domain.review.ReviewEvent;
import io.github.xiaomisum.robotest.service.domain.review.ReviewSnapshotService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewWorkflow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AI 评审承接单测（C8）：写回字段、编辑覆盖、驳回、节点失效与终态拒绝。 */
@ExtendWith(MockitoExtension.class)
class ReviewCaseReviewAdopterTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID SNAPSHOT_NODE_ID = UUID.randomUUID();
    private static final UUID RECORD_ID = UUID.randomUUID();

    @Mock
    private TestReviewMapper testReviewMapper;
    @Mock
    private ReviewSnapshotService reviewSnapshotService;
    @Mock
    private ReviewWorkflow reviewWorkflow;
    @Mock
    private TestReviewRecordMapper reviewRecordMapper;
    @InjectMocks
    private ReviewCaseReviewAdopter adopter;

    @Test
    void type_isReviewCaseReview() {
        assertEquals(Constants.AiTaskType.REVIEW_CASE_REVIEW, adopter.type());
    }

    @Test
    void adopt_rejected_returnsNullAndSkipsWrites() {
        assertNull(adopter.adopt(context(Constants.AiProposalAction.REJECTED, null, proposalContent(), REVIEW_ID)));

        verify(reviewRecordMapper, never()).insert(any(TestReviewRecord.class));
        verify(reviewSnapshotService, never()).applyMark(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void adopt_adopted_writesReviewRecordApplyMarkAndReturnRef() {
        stubReview();
        stubCaseNode();
        stubInsert();

        AdoptOutcome outcome = adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null,
                proposalContent(), REVIEW_ID));

        ArgumentCaptor<TestReviewRecord> captor = ArgumentCaptor.forClass(TestReviewRecord.class);
        verify(reviewRecordMapper).insert(captor.capture());
        TestReviewRecord record = captor.getValue();
        assertEquals(REVIEW_ID, record.getReviewId());
        assertEquals(SNAPSHOT_NODE_ID, record.getSnapshotNodeId());
        assertEquals(OPERATOR_ID, record.getReviewerId());
        assertEquals(Constants.ReviewOperation.MARK, record.getOperationType());
        assertEquals("fail", record.getMark());
        assertEquals("[AI 建议] 缺少错误分支预期", record.getComment());
        verify(reviewSnapshotService).applyMark(eq(SNAPSHOT_NODE_ID), eq(OPERATOR_ID), eq("fail"),
                any(LocalDateTime.class), eq(true));
        assertEquals(RECORD_ID, outcome.createdId());
        assertEquals(RECORD_ID.toString(), outcome.adoptedRef().get("recordId"));
        assertEquals(SNAPSHOT_NODE_ID.toString(), outcome.adoptedRef().get("snapshotNodeId"));
    }

    @Test
    void adopt_adoptedEdited_overridesMarkAndReason() {
        stubReview();
        stubCaseNode();
        stubInsert();
        Map<String, Object> edited = Map.of("suggestedMark", "pass", "reason", "人工复核后认为预期充分");

        adopter.adopt(context(Constants.AiProposalAction.ADOPTED_EDITED, edited, proposalContent(), REVIEW_ID));

        ArgumentCaptor<TestReviewRecord> captor = ArgumentCaptor.forClass(TestReviewRecord.class);
        verify(reviewRecordMapper).insert(captor.capture());
        assertEquals("pass", captor.getValue().getMark());
        assertEquals("[AI 建议] 人工复核后认为预期充分", captor.getValue().getComment());
        verify(reviewSnapshotService).applyMark(eq(SNAPSHOT_NODE_ID), eq(OPERATOR_ID), eq("pass"),
                any(LocalDateTime.class), eq(true));
    }

    @Test
    void adopt_missingOrCrossReviewNode_throwsNodeNotFound() {
        stubReview();
        when(reviewSnapshotService.getNode(SNAPSHOT_NODE_ID)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null, proposalContent(), REVIEW_ID)));
        assertEquals(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND.code(), error.getCode());

        TestReviewNodeSnapshot crossReview = node(Constants.NodeType.CASE, UUID.randomUUID());
        when(reviewSnapshotService.getNode(SNAPSHOT_NODE_ID)).thenReturn(crossReview);
        error = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null, proposalContent(), REVIEW_ID)));
        assertEquals(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND.code(), error.getCode());
    }

    @Test
    void adopt_nonCaseNode_throwsUnsupportedType() {
        stubReview();
        when(reviewSnapshotService.getNode(SNAPSHOT_NODE_ID))
                .thenReturn(node(Constants.NodeType.NORMAL, REVIEW_ID));

        ServiceException error = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null, proposalContent(), REVIEW_ID)));
        assertEquals(ErrorCodeConstants.ONLY_CASE_NODE_CAN_MARK_REVIEW.code(), error.getCode());
        verify(reviewRecordMapper, never()).insert(any(TestReviewRecord.class));
    }

    @Test
    void adopt_terminalReviewRejected() {
        TestReview review = new TestReview();
        review.setId(REVIEW_ID);
        review.setProjectId(PROJECT_ID);
        review.setStatus(Constants.Status.COMPLETED);
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review);
        when(reviewWorkflow.can(any(), any(ReviewEvent.class))).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null, proposalContent(), REVIEW_ID)));
        assertEquals(ErrorCodeConstants.REVIEW_TASK_STATUS_INVALID.code(), error.getCode());
        verify(reviewRecordMapper, never()).insert(any(TestReviewRecord.class));
    }

    @Test
    void adopt_missingReviewIdOrInvalidMark_throwsInputInvalid() {
        ServiceException missing = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null, proposalContent(), null)));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), missing.getCode());

        stubReview();
        stubCaseNode();
        Map<String, Object> invalid = new LinkedHashMap<>(proposalContent());
        invalid.put("suggestedMark", "pending");
        ServiceException mark = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null, invalid, REVIEW_ID)));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), mark.getCode());
        verify(reviewRecordMapper, never()).insert(any(TestReviewRecord.class));
    }

    private void stubReview() {
        TestReview review = new TestReview();
        review.setId(REVIEW_ID);
        review.setProjectId(PROJECT_ID);
        review.setStatus(Constants.Status.IN_PROGRESS);
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review);
        when(reviewWorkflow.can(any(), any(ReviewEvent.class))).thenReturn(true);
    }

    private void stubCaseNode() {
        when(reviewSnapshotService.getNode(SNAPSHOT_NODE_ID)).thenReturn(node(Constants.NodeType.CASE, REVIEW_ID));
    }

    private void stubInsert() {
        doAnswer(invocation -> {
            TestReviewRecord record = invocation.getArgument(0);
            record.setId(RECORD_ID);
            return 1;
        }).when(reviewRecordMapper).insert(any(TestReviewRecord.class));
    }

    private static Map<String, Object> proposalContent() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("reviewId", REVIEW_ID.toString());
        content.put("snapshotNodeId", SNAPSHOT_NODE_ID.toString());
        content.put("caseTitle", "获取验证码");
        content.put("suggestedMark", "fail");
        content.put("reason", "缺少错误分支预期");
        content.put("issues", java.util.List.of());
        content.put("sourceRefs", java.util.List.of());
        return content;
    }

    private static TestReviewNodeSnapshot node(String type, UUID reviewId) {
        TestReviewNodeSnapshot node = new TestReviewNodeSnapshot();
        node.setId(SNAPSHOT_NODE_ID);
        node.setReviewId(reviewId);
        node.setType(type);
        node.setIsAssociated(true);
        node.setTitle("获取验证码");
        return node;
    }

    private AdoptContext context(String action, Map<String, Object> edited, Map<String, Object> proposalContent,
            UUID reviewIdInInput) {
        LoginUser loginUser = mock(LoginUser.class);
        AiTask task = new AiTask();
        task.setType(Constants.AiTaskType.REVIEW_CASE_REVIEW);
        if (reviewIdInInput != null) {
            task.setInput(Map.of("reviewId", reviewIdInInput.toString()));
        }
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("key", "case-1");
        artifact.put("kind", Constants.AiProposalKind.REVIEW_SUGGESTION);
        artifact.put("content", proposalContent);
        return new AdoptContext(task, AdoptFixtures.proposal(artifact), action, edited, null, null, null, null,
                null, null, null, PROJECT_ID, OPERATOR_ID, Set.of(), loginUser);
    }
}
