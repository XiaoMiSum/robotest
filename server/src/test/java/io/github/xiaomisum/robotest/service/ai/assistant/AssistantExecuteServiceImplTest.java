package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewDetailRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.domain.plan.TestPlanService;
import io.github.xiaomisum.robotest.service.domain.review.TestReviewService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseNodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantExecuteServiceImplTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID CASE_A = UUID.randomUUID();
    private static final UUID CASE_B = UUID.randomUUID();
    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    @Mock
    private AssistantService assistantService;
    @Mock
    private AiAssistantMessageMapper messageMapper;
    @Mock
    private TestCaseNodeService testCaseNodeService;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestReviewService testReviewService;
    @Mock
    private TestPlanService testPlanService;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private AssistantExecuteServiceImpl service;

    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        loginUser = new LoginUser();
        loginUser.setId(USER_ID);
        loginUser.setAuthorities(List.of(new SimpleGrantedAuthority("ai:confirm")));
        loginUser.setWorkspaceAuthorities(new ArrayList<>());
        loginUser.setActiveWorkspaceId(WORKSPACE_ID);
        loginUser.setActiveProjectId(PROJECT_ID);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(messageMapper.update(any(AiAssistantMessage.class), any())).thenReturn(1);
    }

    // ---------- 校验链（3.7：251 → 252 → 255 → 253 → 254 分档 → 256 → 257 → 结构） ----------

    @Test
    void execute_notOwned_throws251() {
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CONVERSATION_NOT_FOUND))
                .when(assistantService).getOwnedConversation(CONVERSATION_ID, USER_ID);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_CONVERSATION_NOT_FOUND.code(), exception.getCode());
        verifyNoInteractions(messageMapper);
    }

    @Test
    void execute_messageMissing_throws252() {
        when(assistantService.getOwnedConversation(CONVERSATION_ID, USER_ID))
                .thenReturn(new AiAssistantConversation());
        when(messageMapper.selectById(MESSAGE_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_MESSAGE_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void execute_messageOfOtherConversation_throws252() {
        AiAssistantMessage message = message(intent("create_case"), null);
        message.setConversationId(UUID.randomUUID());
        stubMessage(message);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_MESSAGE_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void execute_noIntent_throws255() {
        stubMessage(message(null, null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PARSE_FAILED.code(), exception.getCode());
    }

    @Test
    void execute_expired_throws253() {
        Map<String, Object> intent = intent("create_case");
        intent.put("expiresAt", LocalDateTime.now().minusMinutes(1).toString());
        stubMessage(message(intent, null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PREVIEW_EXPIRED.code(), exception.getCode());
    }

    @Test
    void execute_malformedExpiresAt_throws253() {
        Map<String, Object> intent = intent("create_case");
        intent.put("expiresAt", "not-a-time");
        stubMessage(message(intent, null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PREVIEW_EXPIRED.code(), exception.getCode());
    }

    @Test
    void execute_firstRoundWithExecution_throws254() {
        stubMessage(message(intent("create_case"), Map.of("status", "rejected")));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PREVIEW_RESOLVED.code(), exception.getCode());
    }

    @Test
    void execute_retryWithoutExecution_throws254() {
        stubMessage(message(intent("update_case"), null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, retry(0), loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PREVIEW_RESOLVED.code(), exception.getCode());
    }

    @Test
    void execute_retryOnRejected_throws254() {
        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("status", "rejected");
        execution.put("results", List.of());
        stubMessage(message(intent("update_case"), execution));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, retry(0), loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PREVIEW_RESOLVED.code(), exception.getCode());
    }

    @Test
    void execute_retryIndexOfNull_throws1000001001() {
        stubMessage(message(intent("update_case"), executedWith(Map.of("seq", 0, "success", false))));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID,
                        retryIndexes(Arrays.asList(0, null)), loginUser));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void execute_retryIndexOutOfBounds_throws1000001001() {
        stubMessage(message(intent("update_case"), executedWith(Map.of("seq", 0, "success", false))));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, retry(7), loginUser));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void execute_retryIndexOnSuccess_throws1000001001() {
        stubMessage(message(intent("update_case"), executedWith(Map.of("seq", 0, "success", true))));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, retry(0), loginUser));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void execute_scopeMismatch_throws256() {
        Map<String, Object> intent = intent("create_case");
        intent.put("scope", Map.of("workspaceId", WORKSPACE_ID.toString(),
                "projectId", UUID.randomUUID().toString()));
        stubMessage(message(intent, null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_SCOPE_MISMATCH.code(), exception.getCode());
    }

    @Test
    void execute_missingContextHeader_throws256() {
        loginUser.setActiveProjectId(null);
        stubMessage(message(intent("create_case"), null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_SCOPE_MISMATCH.code(), exception.getCode());
    }

    @Test
    void execute_withoutConfirmPermission_throws257() {
        loginUser.setAuthorities(List.of(new SimpleGrantedAuthority("ai:task")));
        stubMessage(message(intent("create_case"), null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void execute_unknownKind_throws255() {
        stubMessage(message(intent("delete_project"), null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PARSE_FAILED.code(), exception.getCode());
    }

    @Test
    void execute_adjustWithoutTargetId_throws255() {
        stubMessage(message(intent("adjust_review", new LinkedHashMap<>(), new ArrayList<>()), null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PARSE_FAILED.code(), exception.getCode());
    }

    @Test
    void execute_emptyCaseIds_throws1000001001() {
        stubMessage(message(intent("update_case"), null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void execute_claimLoses_throws254() {
        stubMessage(message(caseIdsIntent("update_case", List.of(CASE_A)), null));
        when(messageMapper.update(any(AiAssistantMessage.class), any())).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PREVIEW_RESOLVED.code(), exception.getCode());
        verifyNoInteractions(testCaseNodeService);
    }

    // ---------- 首执回执与逐项成败 ----------

    @Test
    void execute_updateCase_writesReceiptWithSeq() {
        stubMessage(caseMessage("update_case", List.of(CASE_A, CASE_B),
                List.of(Map.of("field", "title", "op", "replace", "value", "新标题"))));
        ArgumentCaptor<AiAssistantMessage> carrier = ArgumentCaptor.forClass(AiAssistantMessage.class);

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        assertEquals("executed", resp.getExecution().get("status"));
        assertEquals(String.valueOf(USER_ID), resp.getExecution().get("executedBy"));
        assertNotNull(resp.getExecution().get("executedAt"));
        List<Map<String, Object>> results = results(resp);
        assertEquals(2, results.size());
        assertEquals(0, results.get(0).get("seq"));
        assertEquals("update_case", results.get(0).get("action"));
        assertEquals(Boolean.TRUE, results.get(0).get("success"));
        assertEquals(CASE_A.toString(), results.get(0).get("caseId"));
        assertEquals("/workspace/projects/functional-testing", results.get(0).get("link"));
        assertEquals(1, results.get(1).get("seq"));
        assertEquals(CASE_B.toString(), results.get(1).get("caseId"));
        verify(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(USER_ID), eq(CASE_A), anyList());
        verify(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(USER_ID), eq(CASE_B), anyList());
        verify(messageMapper).update(carrier.capture(), any());
        assertNotNull(carrier.getValue().getExecution());
        verify(messageMapper).updateById(any(AiAssistantMessage.class));
    }

    @Test
    void execute_partialFailure_keepsSuccessItems() {
        stubMessage(caseMessage("update_case", List.of(CASE_A, CASE_B),
                List.of(Map.of("field", "priority", "op", "add", "value", "P0"))));
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND))
                .when(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(USER_ID), eq(CASE_A), anyList());

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        List<Map<String, Object>> results = results(resp);
        assertEquals(Boolean.FALSE, results.get(0).get("success"));
        assertEquals(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND.code(), results.get(0).get("errorCode"));
        assertNotNull(results.get(0).get("errorMsg"));
        // 失败项仍带目标 caseId 便于定位（详设 3.7 回执结构）
        assertEquals(CASE_A.toString(), results.get(0).get("caseId"));
        assertEquals(Boolean.TRUE, results.get(1).get("success"));
        verify(messageMapper).updateById(any(AiAssistantMessage.class));
    }

    @Test
    void execute_unexpectedItemFailure_fallsBackTo500() {
        stubMessage(caseMessage("batch_tag", List.of(CASE_A),
                List.of(Map.of("field", "priority", "op", "add", "value", "high"))));
        doThrow(new IllegalStateException("db down"))
                .when(testCaseNodeService).tagCase(eq(PROJECT_ID), eq(USER_ID), eq(CASE_A), anyList());

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        List<Map<String, Object>> results = results(resp);
        assertEquals(500, results.get(0).get("errorCode"));
        assertEquals(Boolean.FALSE, results.get(0).get("success"));
    }

    @Test
    void execute_createCaseMultiUnits_oneResultPerCase() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("cases", List.of(Map.of("title", "用例一"), Map.of("title", "用例二")));
        when(testCaseNodeService.createCase(eq(PROJECT_ID), eq(USER_ID), any(), any(), any()))
                .thenAnswer(invocation -> UUID.randomUUID());
        stubMessage(message(intent("create_case", params, new ArrayList<>()), null));

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        List<Map<String, Object>> results = results(resp);
        assertEquals(2, results.size());
        assertNotNull(results.get(0).get("caseId"));
        assertNotNull(results.get(1).get("caseId"));
        assertNotEquals(results.get(0).get("caseId"), results.get(1).get("caseId"));
        verify(testCaseNodeService, times(2))
                .createCase(eq(PROJECT_ID), eq(USER_ID), any(), any(), any());
    }

    @Test
    void execute_createCaseFallbackSingle_mergesTargetTitleAndChanges() {
        List<Map<String, Object>> changes = List.of(
                Map.of("field", "title", "op", "replace", "value", "改名标题"),
                Map.of("field", "priority", "op", "add", "value", "low"));
        when(testCaseNodeService.createCase(eq(PROJECT_ID), eq(USER_ID), isNull(), isNull(), any()))
                .thenReturn(CASE_A);
        stubMessage(message(intent("create_case", new LinkedHashMap<>(), changes), null));

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(testCaseNodeService).createCase(eq(PROJECT_ID), eq(USER_ID), isNull(), isNull(), fields.capture());
        // changes 同字段后写覆盖 targetTitle（last wins）
        assertEquals("改名标题", fields.getValue().get("title"));
        assertEquals("low", fields.getValue().get("priority"));
        List<Map<String, Object>> results = results(resp);
        assertEquals(1, results.size());
        assertEquals(CASE_A.toString(), results.get(0).get("caseId"));
    }

    @Test
    void execute_viewProgress_linkOnlyWithoutBusinessWrite() {
        Map<String, Object> intent = intent("view_review_progress");
        intent.put("targetId", TARGET_ID.toString());
        stubMessage(message(intent, null));

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        assertTrue(results(resp).isEmpty());
        assertEquals("/workspace/projects/reviews/" + TARGET_ID, resp.getExecution().get("link"));
        assertEquals("executed", resp.getExecution().get("status"));
        verifyNoInteractions(testReviewService, testPlanService);
        verify(messageMapper).updateById(any(AiAssistantMessage.class));
    }

    @Test
    void execute_createReview_defaultsParticipantToExecutor() {
        when(testCaseNodeMapper.listByIds(List.of(CASE_A))).thenReturn(List.of(node(CASE_A, DOC_ID)));
        TestReviewDetailRespDTO created = new TestReviewDetailRespDTO();
        created.setId(TARGET_ID);
        when(testReviewService.createReview(eq(PROJECT_ID), eq(USER_ID), any())).thenReturn(created);
        stubMessage(message(caseIdsIntent("create_review", List.of(CASE_A)), null));

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<TestReviewCreateReqDTO> dto = ArgumentCaptor.forClass(TestReviewCreateReqDTO.class);
        verify(testReviewService).createReview(eq(PROJECT_ID), eq(USER_ID), dto.capture());
        assertEquals("登录用例", dto.getValue().getTitle());
        assertEquals(List.of(USER_ID), dto.getValue().getParticipantIds());
        assertEquals(DOC_ID, dto.getValue().getSelectedNodes().getFirst().getDocumentId());
        assertEquals(List.of(CASE_A), dto.getValue().getSelectedNodes().getFirst().getCaseIds());
        List<Map<String, Object>> results = results(resp);
        assertEquals(TARGET_ID.toString(), results.get(0).get("createdId"));
        assertEquals("/workspace/projects/reviews/" + TARGET_ID, results.get(0).get("link"));
    }

    @Test
    void execute_createReview_missingCaseNode_failsItemWith11022() {
        when(testCaseNodeMapper.listByIds(List.of(CASE_A))).thenReturn(List.of());
        stubMessage(message(caseIdsIntent("create_review", List.of(CASE_A)), null));

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        List<Map<String, Object>> results = results(resp);
        assertEquals(Boolean.FALSE, results.get(0).get("success"));
        assertEquals(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND.code(), results.get(0).get("errorCode"));
        verify(testReviewService, never()).createReview(any(), any(), any());
    }

    @Test
    void execute_createPlan_badStartTime_failsItemWith1001() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("caseIds", List.of(CASE_A.toString()));
        params.put("startTime", "not-a-time");
        stubMessage(message(intent("create_plan", params, new ArrayList<>()), null));

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        List<Map<String, Object>> results = results(resp);
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), results.get(0).get("errorCode"));
        verify(testPlanService, never()).createPlan(any(), any(), any());
    }

    @Test
    void execute_createPlan_successParsesWallClockTime() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("caseIds", List.of(CASE_A.toString()));
        params.put("startTime", "2026-10-05 00:00:00Z");
        when(testCaseNodeMapper.listByIds(List.of(CASE_A))).thenReturn(List.of(node(CASE_A, DOC_ID)));
        TestPlanDetailRespDTO created = new TestPlanDetailRespDTO();
        created.setId(TARGET_ID);
        when(testPlanService.createPlan(eq(PROJECT_ID), eq(USER_ID), any())).thenReturn(created);
        stubMessage(message(intent("create_plan", params, new ArrayList<>()), null));

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<TestPlanCreateReqDTO> dto = ArgumentCaptor.forClass(TestPlanCreateReqDTO.class);
        verify(testPlanService).createPlan(eq(PROJECT_ID), eq(USER_ID), dto.capture());
        assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0), dto.getValue().getStartTime());
        assertEquals("/workspace/projects/plans/" + TARGET_ID, results(resp).get(0).get("link"));
    }

    @Test
    void execute_adjustReview_updatesCasesWithTargetId() {
        Map<String, Object> intent = caseIdsIntent("adjust_review", List.of(CASE_A));
        intent.put("targetId", TARGET_ID.toString());
        when(testCaseNodeMapper.listByIds(List.of(CASE_A))).thenReturn(List.of(node(CASE_A, DOC_ID)));
        stubMessage(message(intent, null));

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, null, loginUser);

        verify(testReviewService).updateReviewCases(eq(PROJECT_ID), eq(TARGET_ID), eq(USER_ID), any());
        assertEquals("/workspace/projects/reviews/" + TARGET_ID, results(resp).get(0).get("link"));
    }

    // ---------- 重试追加（seq 映射、原回执不变） ----------

    @Test
    void execute_retryAppendsAndKeepsOriginalReceipt() {
        Map<String, Object> priorEntry = new LinkedHashMap<>();
        priorEntry.put("seq", 0);
        priorEntry.put("action", "update_case");
        priorEntry.put("success", false);
        priorEntry.put("caseId", CASE_A.toString());
        priorEntry.put("errorCode", 500);
        Map<String, Object> execution = executedWith(priorEntry);
        stubMessage(message(caseIdsIntent("update_case", List.of(CASE_A)), execution));

        AiAssistantExecuteRespDTO resp = service.execute(CONVERSATION_ID, MESSAGE_ID, retry(0), loginUser);

        verify(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(USER_ID), eq(CASE_A), anyList());
        // 重试不认领（254 分档后直接执行）
        verify(messageMapper, never()).update(any(AiAssistantMessage.class), any());
        // status / executedBy / executedAt 沿用原回执（回执写后不可变更）
        assertEquals(execution.get("executedBy"), resp.getExecution().get("executedBy"));
        assertEquals(execution.get("executedAt"), resp.getExecution().get("executedAt"));
        assertEquals("executed", resp.getExecution().get("status"));
        List<Map<String, Object>> results = results(resp);
        assertEquals(2, results.size());
        assertEquals(500, results.get(0).get("errorCode"));
        assertEquals(Boolean.TRUE, results.get(1).get("success"));
        assertEquals(0, results.get(1).get("seq"));
        verify(messageMapper).updateById(any(AiAssistantMessage.class));
    }

    @Test
    void execute_retrySeqOutOfRange_throws1000001001() {
        Map<String, Object> priorEntry = new LinkedHashMap<>();
        priorEntry.put("seq", 9);
        priorEntry.put("action", "update_case");
        priorEntry.put("success", false);
        stubMessage(message(caseIdsIntent("update_case", List.of(CASE_A)), executedWith(priorEntry)));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.execute(CONVERSATION_ID, MESSAGE_ID, retry(0), loginUser));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
        verify(testCaseNodeService, never()).updateCaseFields(any(), any(), any(), anyList());
        verify(messageMapper, never()).updateById(any(AiAssistantMessage.class));
    }

    // ---------- 取消（3.8：251 → 252 → 255 → 254） ----------

    @Test
    void cancel_writesRejectedReceipt() {
        stubMessage(message(intent("create_case"), null));
        ArgumentCaptor<AiAssistantMessage> carrier = ArgumentCaptor.forClass(AiAssistantMessage.class);

        AiAssistantExecuteRespDTO resp = service.cancel(CONVERSATION_ID, MESSAGE_ID, loginUser);

        assertEquals("rejected", resp.getExecution().get("status"));
        assertEquals(String.valueOf(USER_ID), resp.getExecution().get("rejectedBy"));
        assertNotNull(resp.getExecution().get("rejectedAt"));
        verify(messageMapper).update(carrier.capture(), any());
        assertEquals("rejected", carrier.getValue().getExecution().get("status"));
        verify(messageMapper, never()).updateById(any(AiAssistantMessage.class));
        verifyNoInteractions(testCaseNodeService, testReviewService, testPlanService);
    }

    @Test
    void cancel_noIntent_throws255() {
        stubMessage(message(null, null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.cancel(CONVERSATION_ID, MESSAGE_ID, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PARSE_FAILED.code(), exception.getCode());
    }

    @Test
    void cancel_alreadyResolved_throws254() {
        stubMessage(message(intent("create_case"), Map.of("status", "executed")));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.cancel(CONVERSATION_ID, MESSAGE_ID, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PREVIEW_RESOLVED.code(), exception.getCode());
        verify(messageMapper, never()).update(any(AiAssistantMessage.class), any());
    }

    @Test
    void cancel_claimLoses_throws254() {
        stubMessage(message(intent("create_case"), null));
        when(messageMapper.update(any(AiAssistantMessage.class), any())).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.cancel(CONVERSATION_ID, MESSAGE_ID, loginUser));

        assertEquals(ErrorCodeConstants.ASSISTANT_PREVIEW_RESOLVED.code(), exception.getCode());
    }

    // ---------- 辅助 ----------

    private void stubMessage(AiAssistantMessage message) {
        when(assistantService.getOwnedConversation(CONVERSATION_ID, USER_ID))
                .thenReturn(new AiAssistantConversation());
        when(messageMapper.selectById(MESSAGE_ID)).thenReturn(message);
    }

    private AiAssistantMessage message(Map<String, Object> intent, Map<String, Object> execution) {
        AiAssistantMessage message = new AiAssistantMessage();
        message.setId(MESSAGE_ID);
        message.setConversationId(CONVERSATION_ID);
        message.setIntent(intent);
        message.setExecution(execution);
        return message;
    }

    private Map<String, Object> intent(String kind) {
        return intent(kind, new LinkedHashMap<>(), new ArrayList<>());
    }

    private Map<String, Object> intent(String kind, Map<String, Object> params,
            List<Map<String, Object>> changes) {
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("kind", kind);
        intent.put("targetTitle", "登录用例");
        intent.put("projectId", PROJECT_ID);
        intent.put("params", params);
        intent.put("changes", new ArrayList<>(changes));
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("workspaceId", WORKSPACE_ID.toString());
        scope.put("projectId", PROJECT_ID.toString());
        intent.put("scope", scope);
        intent.put("expiresAt", LocalDateTime.now().plusMinutes(10).toString());
        return intent;
    }

    private Map<String, Object> caseIdsIntent(String kind, List<UUID> caseIds) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("caseIds", caseIds.stream().map(UUID::toString).toList());
        return intent(kind, params, new ArrayList<>());
    }

    private AiAssistantMessage caseMessage(String kind, List<UUID> caseIds,
            List<Map<String, Object>> changes) {
        Map<String, Object> intent = caseIdsIntent(kind, caseIds);
        intent.put("changes", new ArrayList<>(changes));
        return message(intent, null);
    }

    private Map<String, Object> executedWith(Map<String, Object> entry) {
        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("status", "executed");
        execution.put("executedBy", String.valueOf(USER_ID));
        execution.put("executedAt", "2026-10-02T09:12:00Z");
        execution.put("results", new ArrayList<>(List.of(entry)));
        return execution;
    }

    private static TestCaseNode node(UUID caseId, UUID documentId) {
        TestCaseNode node = new TestCaseNode();
        node.setId(caseId);
        node.setDocumentId(documentId);
        node.setType("case");
        return node;
    }

    private AiAssistantExecuteReqDTO retry(int... indexes) {
        List<Integer> values = new ArrayList<>();
        for (int index : indexes) {
            values.add(index);
        }
        return retryIndexes(values);
    }

    private AiAssistantExecuteReqDTO retryIndexes(List<Integer> values) {
        AiAssistantExecuteReqDTO reqDTO = new AiAssistantExecuteReqDTO();
        reqDTO.setRetryIndexes(values);
        return reqDTO;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> results(AiAssistantExecuteRespDTO resp) {
        return (List<Map<String, Object>>) resp.getExecution().get("results");
    }
}
