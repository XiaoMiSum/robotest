package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用例级别推荐处理器单测（C8）：权限与输入校验、级别映射回退、缺陷热点变量装配与模型零命中口径。
 */
@ExtendWith(MockitoExtension.class)
class CasePriorityHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();

    @Mock
    private CaseAssistSupport assistSupport;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private TaskExecutionContext context;
    @Captor
    private org.mockito.ArgumentCaptor<Map<String, String>> promptVars;

    @InjectMocks
    private CasePriorityHandler handler;

    // ---------- SPI 契约 ----------

    @Test
    void type_andDefaultPrompt_carryContextPlaceholders() {
        assertEquals("case_priority", handler.type());
        assertTrue(handler.defaultPrompt().contains("{{caseContext}}"));
        assertTrue(handler.defaultPrompt().contains("{{requirementContext}}"));
        assertTrue(handler.defaultPrompt().contains("{{bugContext}}"));
    }

    @Test
    void checkPermission_withoutCaseEdit_throws306() {
        LoginUser user = new LoginUser();
        user.setAuthorities(List.of(new SimpleGrantedAuthority("case:edit")));

        handler.checkPermission(user);

        LoginUser viewer = new LoginUser();
        viewer.setAuthorities(List.of(new SimpleGrantedAuthority("case:view")));
        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(),
                assertThrows(ServiceException.class, () -> handler.checkPermission(viewer)).getCode());
        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(),
                assertThrows(ServiceException.class, () -> handler.checkPermission(null)).getCode());
    }

    @Test
    void validateInput_missingDocumentId_throws115() {
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> handler.validateInput(Map.of())).getCode());
    }

    @Test
    void validateInput_withoutProjectContext_throws115() {
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> handler.validateInput(baseInput(),
                        new TaskSubmitContext(null, null, OPERATOR_ID, null))).getCode());
    }

    // ---------- 执行 ----------

    @Test
    void execute_keepsCurrentPriority_whenSuggestedMissingOrIllegal() {
        TestCaseNode node = caseNode("验证码重发", "P1");
        when(assistSupport.resolve(any(), any(), any())).thenReturn(resolved(node, (UUID) null));
        when(assistSupport.upstreamRequirements(any(), any(), any())).thenReturn(List.of());
        stubExecution("{\"artifacts\":[{\"content\":{\"nodeId\":\"" + node.getId()
                + "\",\"suggested\":\"critical\",\"reason\":\"越界值应回退\"}}]}");

        TaskResult result = handler.execute(context);

        Map<String, Object> artifact = artifacts(result).get(0);
        assertEquals("node-1", artifact.get("key"));
        assertEquals(Constants.AiArtifactKind.PRIORITY_SUGGESTION, artifact.get("kind"));
        assertEquals("pending", artifact.get("confirmStatus"));
        assertEquals("级别推荐：验证码重发", artifact.get("title"));

        Map<String, Object> content = content(artifact);
        assertEquals(node.getId(), UUID.fromString(String.valueOf(content.get("nodeId"))));
        assertEquals("medium", content.get("current"));
        assertEquals("medium", content.get("suggested"));
        assertEquals("越界值应回退", content.get("reason"));
        assertTrue(list(content.get("sourceRefs")).isEmpty());
    }

    @Test
    void execute_keepsValidSuggested_andUnansweredNodeFallsBackToCurrent() {
        TestCaseNode known = caseNode("验证码重发", "P2");
        TestCaseNode unknown = caseNode("弱网切换", "P9");
        when(assistSupport.resolve(any(), any(), any())).thenReturn(resolved(known, unknown));
        when(assistSupport.upstreamRequirements(any(), any(), any())).thenReturn(List.of());
        stubExecution("{\"artifacts\":[{\"content\":{\"nodeId\":\"" + known.getId()
                + "\",\"suggested\":\"high\",\"reason\":\"主流程\"}}]}");

        TaskResult result = handler.execute(context);

        List<Map<String, Object>> artifacts = artifacts(result);
        assertEquals(2, artifacts.size());
        assertEquals("high", content(artifacts.get(0)).get("suggested"));
        assertEquals("low", content(artifacts.get(0)).get("current"));
        // 模型未响应的节点仍产产物，current / suggested 均回退用例现状（未知值保持 null）
        Map<String, Object> unanswered = content(artifacts.get(1));
        assertEquals(null, unanswered.get("current"));
        assertEquals(null, unanswered.get("suggested"));
        assertEquals("", unanswered.get("reason"));
    }

    @Test
    void execute_bugContextCountedPerModuleWindow() {
        TestCaseNode node = caseNode("验证码重发", "P1");
        when(assistSupport.resolve(any(), any(), any())).thenReturn(resolved(node, MODULE_ID));
        when(assistSupport.upstreamRequirements(any(), any(), any())).thenReturn(List.of());
        when(bugMapper.findForAnalysis(eq(PROJECT_ID), any(), any())).thenReturn(List.of(
                bug("验证码偶发失效", MODULE_ID, LocalDateTime.now().minusDays(1)),
                bug("其他模块缺陷", UUID.randomUUID(), LocalDateTime.now().minusDays(1)),
                bug("上月旧缺陷", MODULE_ID, LocalDateTime.now().minusDays(60))));
        stubExecution("{\"artifacts\":[{\"content\":{\"nodeId\":\"" + node.getId()
                + "\",\"suggested\":\"high\"}}]}");

        handler.execute(context);

        verify(context).prompt(anyString(), promptVars.capture());
        String bugContext = promptVars.getValue().get("bugContext");
        assertTrue(bugContext.contains("近 30 天共 1 个缺陷"));
        assertTrue(bugContext.contains("验证码偶发失效"));
        verify(bugMapper).findForAnalysis(eq(PROJECT_ID), any(), any());
    }

    @Test
    void execute_modulelessDocumentOffersEmptyBugContext() {
        TestCaseNode node = caseNode("验证码重发", "P1");
        when(assistSupport.resolve(any(), any(), any())).thenReturn(resolved(node, (UUID) null));
        when(assistSupport.upstreamRequirements(any(), any(), any())).thenReturn(List.of());
        stubExecution("{\"artifacts\":[{\"content\":{\"nodeId\":\"" + node.getId() + "\"}}]}");

        handler.execute(context);

        verify(context).prompt(anyString(), promptVars.capture());
        assertEquals("", promptVars.getValue().get("bugContext"));
    }

    @Test
    void execute_modelMissesEverySelectedNode_failsWith117() {
        when(assistSupport.resolve(any(), any(), any()))
                .thenReturn(resolved(caseNode("登录", "P1"), (UUID) null));
        when(assistSupport.upstreamRequirements(any(), any(), any())).thenReturn(List.of());
        stubExecution("{\"artifacts\":[{\"content\":{\"nodeId\":\"" + UUID.randomUUID() + "\"}}]}");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), exception.getCode());
    }

    // ---------- 辅助 ----------

    private void stubExecution(String replyContent) {
        when(context.getInput()).thenReturn(baseInput());
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(context.prompt(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.chat(any(), any())).thenReturn(new AiChatReply(replyContent, 10, 20));
    }

    private Map<String, Object> baseInput() {
        return new LinkedHashMap<>(Map.of("documentId", DOCUMENT_ID.toString(),
                "nodeIds", List.of(CASE_ID.toString())));
    }

    private CaseAssistSupport.ResolvedCases resolved(TestCaseNode node, UUID moduleId) {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(PROJECT_ID);
        document.setModuleId(moduleId);
        document.setName("登录用例");

        Map<UUID, TestCaseNode> byId = new LinkedHashMap<>();
        byId.put(node.getId(), node);
        return new CaseAssistSupport.ResolvedCases(document, List.of(node), byId, Map.of());
    }

    private CaseAssistSupport.ResolvedCases resolved(TestCaseNode first, TestCaseNode second) {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(PROJECT_ID);
        document.setName("登录用例");

        Map<UUID, TestCaseNode> byId = new LinkedHashMap<>();
        byId.put(first.getId(), first);
        byId.put(second.getId(), second);
        return new CaseAssistSupport.ResolvedCases(document, List.of(first, second), byId, Map.of());
    }

    private TestCaseNode caseNode(String title, String priority) {
        TestCaseNode node = new TestCaseNode();
        node.setId(UUID.randomUUID());
        node.setDocumentId(DOCUMENT_ID);
        node.setType(Constants.NodeType.CASE);
        node.setTitle(title);
        node.setPriority(priority);
        return node;
    }

    private static Bug bug(String title, UUID moduleId, LocalDateTime createdAt) {
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(PROJECT_ID);
        bug.setModuleId(moduleId);
        bug.setTitle(title);
        bug.setCreatedAt(createdAt);
        return bug;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> artifacts(TaskResult result) {
        return (List<Map<String, Object>>) result.result().get("artifacts");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> content(Map<String, Object> artifact) {
        return (Map<String, Object>) artifact.get("content");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object raw) {
        return raw instanceof List ? (List<Map<String, Object>>) raw : List.of();
    }
}
