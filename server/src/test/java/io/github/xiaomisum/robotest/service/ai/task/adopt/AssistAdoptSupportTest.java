package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import io.github.xiaomisum.robotest.service.project.ProjectActivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistAdoptSupportTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();

    @Mock
    private CaseAssistSupport assistSupport;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private ProjectActivityService projectActivityService;

    @InjectMocks
    private AssistAdoptSupport support;

    @Test
    void requirePermission_absentLoginUser_throwsNoPermission() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> support.requirePermission(null, "case:edit"));

        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void requirePermission_missingPermission_throwsNoPermission() {
        LoginUser loginUser = mock(LoginUser.class);
        lenient().when(loginUser.getPermissions()).thenReturn(List.of("plan:execute"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> support.requirePermission(loginUser, "case:edit"));

        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void requirePermission_holdingPermission_passes() {
        LoginUser loginUser = mock(LoginUser.class);
        lenient().when(loginUser.getPermissions()).thenReturn(List.of("case:edit"));

        support.requirePermission(loginUser, "case:edit");
    }

    @Test
    void taskInput_absentInput_returnsEmptyMap() {
        Map<String, Object> nodeContent = Map.of("nodeId", NODE_ID.toString());

        assertTrue(AssistAdoptSupport.taskInput(context(null, nodeContent, null)).isEmpty());
    }

    @Test
    void resolveCaseTarget_prefersTargetDocumentIdAndChecksMembership() {
        Map<String, Object> nodeContent = Map.of("nodeId", NODE_ID.toString());
        CaseAssistSupport.ResolvedCases resolved = stubResolve();

        AssistAdoptSupport.CaseTarget target = support.resolveCaseTarget(
                context(DOCUMENT_ID, nodeContent, null));

        assertSame(resolved.document(), target.document());
        assertEquals(NODE_ID, target.node().getId());
        verify(assistSupport).resolve(PROJECT_ID, DOCUMENT_ID, List.of(NODE_ID));
        verify(projectAccessGuard).requireProjectMember(PROJECT_ID, OPERATOR_ID);
    }

    @Test
    void resolveCaseTarget_fallsBackToTaskInputDocumentId() {
        Map<String, Object> nodeContent = Map.of("nodeId", NODE_ID.toString());
        stubResolve();

        support.resolveCaseTarget(context(null, nodeContent, Map.of("documentId", DOCUMENT_ID.toString())));

        verify(assistSupport).resolve(PROJECT_ID, DOCUMENT_ID, List.of(NODE_ID));
    }

    @Test
    void resolveCaseTarget_missingDocumentId_throwsInputInvalid() {
        Map<String, Object> nodeContent = Map.of("nodeId", NODE_ID.toString());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> support.resolveCaseTarget(context(null, nodeContent, null)));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void resolveCaseTarget_malformedNodeId_throwsInputInvalid() {
        Map<String, Object> nodeContent = Map.of("nodeId", "不是 uuid");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> support.resolveCaseTarget(context(DOCUMENT_ID, nodeContent, null)));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void record_delegatesToActivityService() {
        Map<String, Object> nodeContent = Map.of("nodeId", NODE_ID.toString());
        AdoptContext context = context(DOCUMENT_ID, nodeContent, null);

        support.record(context, "TEST_CASE_NODE", NODE_ID, "用例标题", "CASE_UPDATED", "采纳 AI 用例补全建议");

        verify(projectActivityService).record(eq(PROJECT_ID), eq(OPERATOR_ID), eq("TEST_CASE_NODE"),
                eq(NODE_ID), eq("用例标题"), eq("CASE_UPDATED"), eq("采纳 AI 用例补全建议"));
    }

    // ---------- fixtures ----------

    private CaseAssistSupport.ResolvedCases stubResolve() {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(PROJECT_ID);
        TestCaseNode node = new TestCaseNode();
        node.setId(NODE_ID);
        node.setDocumentId(DOCUMENT_ID);
        node.setType("case");
        node.setTitle("验证码 60 秒重发限制");
        CaseAssistSupport.ResolvedCases resolved = new CaseAssistSupport.ResolvedCases(
                document, List.of(node), Map.of(NODE_ID, node), Map.of());
        when(assistSupport.resolve(eq(PROJECT_ID), any(), eq(List.of(NODE_ID)))).thenReturn(resolved);
        return resolved;
    }

    private AdoptContext context(UUID targetDocumentId, Map<String, Object> nodeContent, Map<String, Object> input) {
        LoginUser loginUser = mock(LoginUser.class);
        lenient().when(loginUser.getPermissions()).thenReturn(List.of("case:edit"));
        AiTask task = new AiTask();
        task.setType("case_complete");
        task.setInput(input);
        Map<String, Object> artifact = Map.of("key", "node-1", "content", nodeContent);
        return new AdoptContext(task, artifact, "adopted", null, null, null, null, null, null,
                targetDocumentId, null, null, null, PROJECT_ID, OPERATOR_ID, loginUser);
    }
}
