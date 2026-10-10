package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CasePriorityAdopterTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();

    @Mock
    private AssistAdoptSupport support;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;

    @Captor
    private ArgumentCaptor<TestCaseNode> nodeCaptor;

    @InjectMocks
    private CasePriorityAdopter adopter;

    @Test
    void type_isCasePriority() {
        assertEquals("case_priority", adopter.type());
    }

    @Test
    void adopt_rejected_returnsNullAndSkipsWrite() {
        assertNull(adopter.adopt(context(Constants.AiArtifactAction.REJECTED, null, "P1", null)));

        verify(support, never()).requirePermission(any(), anyString());
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
    }

    @Test
    void adopt_withoutCaseEditPermission_failsBeforeAnyWrite() {
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_NO_PERMISSION))
                .when(support).requirePermission(any(), eq("case:edit"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, "P1", "high")));

        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(), exception.getCode());
        verify(support, never()).resolveCaseTarget(any());
    }

    @Test
    void adopt_writesPriorityOnlyWhenSuggestedDiffers() {
        AdoptOutcome outcome = adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, "P1", "high"));

        verify(testCaseNodeMapper).updateById(nodeCaptor.capture());
        assertEquals(NODE_ID, nodeCaptor.getValue().getId());
        assertEquals("P0", nodeCaptor.getValue().getPriority());
        // 部分更新：级别之外的字段不得进更新载体（C11）
        assertNull(nodeCaptor.getValue().getTitle());

        assertEquals(NODE_ID, outcome.createdId());
        assertEquals("medium", outcome.adoptedRef().get("from"));
        assertEquals("high", outcome.adoptedRef().get("to"));
        verify(support).record(any(), eq("TEST_CASE_NODE"), eq(NODE_ID), anyString(),
                eq("CASE_UPDATED"), anyString());
    }

    @Test
    void adopt_samePrioritySkipsWriteAndActivity() {
        AdoptOutcome outcome = adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, "P1", "medium"));

        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
        verify(support, never()).record(any(), anyString(), any(), anyString(), anyString(), anyString());
        assertEquals("medium", outcome.adoptedRef().get("to"));
    }

    @Test
    void adopt_editedContentOverridesSuggested() {
        Map<String, Object> edited = Map.of("suggested", "low");

        adopter.adopt(context(Constants.AiArtifactAction.ADOPTED_EDITED, edited, "P1", "high"));

        verify(testCaseNodeMapper).updateById(nodeCaptor.capture());
        assertEquals("P2", nodeCaptor.getValue().getPriority());
    }

    @Test
    void adopt_missingSuggestedSkipsWrite() {
        // 模型未响应且无人工级别 → 无可推荐值，无变化即无写入
        AdoptOutcome outcome = adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, null, null));

        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
        assertNull(outcome.adoptedRef().get("to"));
    }

    @Test
    void adopt_malformedEditedSuggestedThrowsInputInvalid() {
        Map<String, Object> edited = Map.of("suggested", "urgent");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED_EDITED, edited, "P1", "high")));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
    }

    // ---------- fixtures ----------

    private AdoptContext context(String action, Map<String, Object> edited, String priority, String suggested) {
        LoginUser loginUser = mock(LoginUser.class);
        lenient().when(loginUser.getPermissions()).thenReturn(List.of("case:edit"));
        AiTask task = new AiTask();
        task.setType("case_priority");

        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(PROJECT_ID);
        TestCaseNode node = new TestCaseNode();
        node.setId(NODE_ID);
        node.setDocumentId(DOCUMENT_ID);
        node.setType(Constants.NodeType.CASE);
        node.setTitle("验证码 60 秒重发限制");
        node.setPriority(priority);
        CaseAssistSupport.ResolvedCases resolved = new CaseAssistSupport.ResolvedCases(
                document, List.of(node), Map.of(NODE_ID, node), Map.of());
        lenient().when(support.resolveCaseTarget(any()))
                .thenReturn(new AssistAdoptSupport.CaseTarget(document, node, resolved));

        return new AdoptContext(task, artifact(suggested), action, edited, null, null, null, null, null,
                DOCUMENT_ID, null, null, null, PROJECT_ID, OPERATOR_ID, loginUser);
    }

    private static Map<String, Object> artifact(String suggested) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("nodeId", NODE_ID.toString());
        content.put("current", "medium");
        if (suggested != null) {
            content.put("suggested", suggested);
            content.put("reason", "缺陷多集中在该链路");
        }
        return Map.of("key", "node-" + NODE_ID, "kind", Constants.AiArtifactKind.PRIORITY_SUGGESTION,
                "content", content);
    }
}
