package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewDetailRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.domain.review.TestReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 圈选建议采纳支撑单测（C8）：权限 1000018208、createParams 缺失 1000018206、
 * caseIds 归属 1000018207 与既有 createReview 委托。
 */
@ExtendWith(MockitoExtension.class)
class SelectionAdoptSupportTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();

    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private TestReviewService testReviewService;

    @InjectMocks
    private SelectionAdoptSupport support;

    @Test
    void create_rejected_returnsNull() {
        AdoptOutcome outcome = support.create(context(Map.of("title", "评审"), "rejected",
                mock(LoginUser.class)), "review:create", false);

        assertNull(outcome);
        verify(testReviewService, never()).createReview(any(), any(), any());
    }

    @Test
    void create_withoutPermission_throws208() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> support.create(context(Map.of("title", "评审"), "adopted", user("case:view")),
                        "review:create", false));
        assertEquals(ErrorCodeConstants.GENERATION_NO_ADOPT_PERMISSION.code(), exception.getCode());
    }

    @Test
    void create_missingCreateParams_throws206() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> support.create(context(null, "adopted", user("review:create")),
                        "review:create", false));
        assertEquals(ErrorCodeConstants.SELECTION_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void create_caseNotInProject_throws2007() {
        when(testCaseNodeMapper.listByIds(any())).thenReturn(List.of());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> support.create(context(reviewParams(), "adopted", user("review:create")),
                        "review:create", false));
        assertEquals(ErrorCodeConstants.SELECTION_CASE_NOT_FOUND.code(), exception.getCode());
        verify(testReviewService, never()).createReview(any(), any(), any());
    }

    @Test
    void create_delegatesToCreateReview() {
        when(testCaseNodeMapper.listByIds(any())).thenReturn(List.of(caseNode()));
        when(testCaseDocumentMapper.listByIds(any())).thenReturn(List.of(document()));
        TestReviewDetailRespDTO created = new TestReviewDetailRespDTO();
        created.setId(UUID.randomUUID());
        when(testReviewService.createReview(eq(PROJECT_ID), eq(OPERATOR_ID), any())).thenReturn(created);

        AdoptOutcome outcome = support.create(context(reviewParams(), "adopted", user("review:create")),
                "review:create", false);

        assertNotNull(outcome);
        assertEquals(created.getId(), outcome.createdId());
        assertEquals(String.valueOf(created.getId()), outcome.adoptedRef().get("reviewId"));
        ArgumentCaptor<TestReviewCreateReqDTO> captor =
                ArgumentCaptor.forClass(TestReviewCreateReqDTO.class);
        verify(testReviewService).createReview(eq(PROJECT_ID), eq(OPERATOR_ID), captor.capture());
        assertEquals("登录评审", captor.getValue().getTitle());
        assertEquals(1, captor.getValue().getSelectedNodes().size());
    }

    @Test
    void create_planSideMissingName_throws206() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> support.create(context(Map.of("selectedNodes", List.of(
                                Map.of("documentId", DOCUMENT_ID, "caseIds", List.of(CASE_ID)))),
                        "adopted", user("plan:create")), "plan:create", true));
        assertEquals(ErrorCodeConstants.SELECTION_INPUT_INVALID.code(), exception.getCode());
    }

    // ---------- 辅助 ----------

    private static LoginUser user(String permission) {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of(permission));
        return user;
    }

    private AdoptContext context(Map<String, Object> createParams, String action, LoginUser user) {
        AiTask task = new AiTask();
        task.setId(UUID.randomUUID());
        task.setProjectId(PROJECT_ID);
        return new AdoptContext(task, Map.of("key", "sel-1", "kind", "review_selection"),
                action, null, null, null, null, null, createParams,
                null, null, null, null, PROJECT_ID, OPERATOR_ID, user);
    }

    private static Map<String, Object> reviewParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", "登录评审");
        params.put("selectedNodes", List.of(
                Map.of("documentId", DOCUMENT_ID, "caseIds", List.of(CASE_ID))));
        return params;
    }

    private static TestCaseNode caseNode() {
        TestCaseNode node = new TestCaseNode();
        node.setId(CASE_ID);
        node.setDocumentId(DOCUMENT_ID);
        node.setType("case");
        node.setTitle("正确密码登录");
        return node;
    }

    private static TestCaseDocument document() {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(PROJECT_ID);
        document.setName("登录用例");
        return document;
    }
}
