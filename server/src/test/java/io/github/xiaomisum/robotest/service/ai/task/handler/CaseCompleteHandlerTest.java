package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用例补全处理器单测（C8）：SPI 契约、权限与输入校验矩阵、产物逐节点清洗与模型零命中失败口径。
 */
@ExtendWith(MockitoExtension.class)
class CaseCompleteHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();

    @Mock
    private CaseAssistSupport assistSupport;
    @Mock
    private TaskExecutionContext context;
    @Captor
    private ArgumentCaptor<Map<String, String>> promptVars;

    @InjectMocks
    private CaseCompleteHandler handler;

    // ---------- SPI 契约 ----------

    @Test
    void type_andDefaultPrompt_carryContextPlaceholders() {
        assertEquals("case_complete", handler.type());
        assertTrue(handler.defaultPrompt().contains("{{caseContext}}"));
        assertTrue(handler.defaultPrompt().contains("{{documentContext}}"));
        assertTrue(handler.defaultPrompt().contains("{{requirementContext}}"));
    }

    @Test
    void checkPermission_withoutCaseEdit_throws306() {
        LoginUser user = new LoginUser();
        user.setAuthorities(List.of(new SimpleGrantedAuthority("case:view")));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.checkPermission(user));
        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(), exception.getCode());
        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(),
                assertThrows(ServiceException.class, () -> handler.checkPermission(null)).getCode());
    }

    @Test
    void checkPermission_withCaseEdit_passes() {
        LoginUser user = new LoginUser();
        user.setAuthorities(List.of(new SimpleGrantedAuthority("case:edit")));

        handler.checkPermission(user);
    }

    // ---------- 输入校验 ----------

    @Test
    void validateInput_missingDocumentId_throws115() {
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class,
                        () -> handler.validateInput(Map.of())).getCode());
    }

    @Test
    void validateInput_missingNodeIds_throws302() {
        assertEquals(ErrorCodeConstants.ASSISTED_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> handler.validateInput(
                        Map.of("documentId", DOCUMENT_ID.toString()))).getCode());
    }

    @Test
    void validateInput_withoutProjectContext_throws115() {
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> handler.validateInput(baseInput(),
                        new TaskSubmitContext(null, null, OPERATOR_ID, null))).getCode());
    }

    @Test
    void validateInput_resolvesScope_andThrowsMappedCodes() {
        when(assistSupport.resolve(any(), any(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_NODE_NOT_FOUND));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(baseInput(), submitContext()));
        assertEquals(ErrorCodeConstants.ASSISTED_NODE_NOT_FOUND.code(), exception.getCode());
    }

    // ---------- 执行 ----------

    @Test
    void execute_emitsOneArtifactPerNodeWithNoChangeFallback() {
        TestCaseNode node = caseNode("验证码 60 秒重发限制");
        TestCaseNode missed = caseNode("登录失败提示");
        Requirement requirement = requirement();
        when(assistSupport.resolve(any(), any(), any()))
                .thenReturn(resolved(node, missed, attrNode(missed.getId(), Constants.NodeType.STEP, "点击登录")));
        when(assistSupport.upstreamRequirements(any(), any(), any())).thenReturn(List.of(requirement));
        stubExecution(replyOf(node.getId(), "手机号已输入并获取过一次验证码"));

        TaskResult result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(2, artifacts.size());

        Map<String, Object> hit = artifacts.get(0);
        assertEquals("node-1", hit.get("key"));
        assertEquals(Constants.AiArtifactKind.CASE_SUGGESTION, hit.get("kind"));
        assertEquals("pending", hit.get("confirmStatus"));
        assertEquals(node.getTitle(), hit.get("title"));

        Map<String, Object> hitContent = content(hit);
        assertEquals(node.getId(), UUID.fromString(String.valueOf(hitContent.get("nodeId"))));
        Map<String, Object> fields = map(hitContent.get("fields"));
        assertEquals("手机号已输入并获取过一次验证码", map(fields.get("precondition")).get("suggested"));
        // 模型省略字段回退现有值，不制造伪建议
        assertEquals(List.of("点击获取"), map(fields.get("steps")).get("suggested"));
        // 模型省略 tags → suggested 回退现有标签
        assertEquals(List.of("登录"), map(fields.get("tags")).get("suggested"));

        Map<String, Object> extra = list(hitContent.get("extraNodes")).get(0);
        assertEquals("验证码 10 分钟后过期校验", extra.get("title"));
        assertEquals(Boolean.TRUE, extra.get("isTestCase"));
        assertEquals(node.getId(), UUID.fromString(String.valueOf(extra.get("parentNodeId"))));

        Map<String, Object> ref = list(hitContent.get("sourceRefs")).get(0);
        assertEquals("requirement", ref.get("type"));
        assertEquals("REQ-1", ref.get("title"));
        assertTrue(ref.containsKey("quote"));

        Map<String, Object> miss = artifacts.get(1);
        assertEquals("node-2", miss.get("key"));
        assertEquals(missed.getTitle(), miss.get("title"));
        Map<String, Object> missFields = map(content(miss).get("fields"));
        assertEquals(List.of("点击登录"), map(missFields.get("steps")).get("suggested"));
        assertTrue(list(content(miss).get("extraNodes")).isEmpty());
    }

    @Test
    void execute_modelMissesEverySelectedNode_failsWith117() {
        when(assistSupport.resolve(any(), any(), any())).thenReturn(resolved(caseNode("登录")));
        when(assistSupport.upstreamRequirements(any(), any(), any())).thenReturn(List.of());
        stubExecution("{\"artifacts\":[{\"content\":{\"nodeId\":\"" + UUID.randomUUID() + "\"}}]}");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), exception.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_offersAllVariablesToPrompt() {
        TestCaseNode node = caseNode("登录");
        when(assistSupport.resolve(any(), any(), any())).thenReturn(resolved(node));
        when(assistSupport.upstreamRequirements(any(), any(), any())).thenReturn(List.of(requirement()));
        stubExecution(replyOf(node.getId(), null));

        handler.execute(context);

        verify(context).prompt(anyString(), promptVars.capture());
        assertTrue(promptVars.getValue().containsKey("caseContext"));
        assertTrue(promptVars.getValue().containsKey("documentContext"));
        assertTrue(promptVars.getValue().containsKey("requirementContext"));
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

    private TaskSubmitContext submitContext() {
        return new TaskSubmitContext(PROJECT_ID, UUID.randomUUID(), OPERATOR_ID, null);
    }

    private CaseAssistSupport.ResolvedCases resolved(TestCaseNode... nodes) {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(PROJECT_ID);
        document.setName("登录用例");

        Map<UUID, TestCaseNode> byId = new LinkedHashMap<>();
        Map<UUID, List<TestCaseNode>> children = new LinkedHashMap<>();
        for (TestCaseNode node : nodes) {
            byId.put(node.getId(), node);
            if (node.getParentId() != null) {
                children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node);
            }
        }
        // 选中节点本身不进 children 索引（属性子节点才进）
        List<TestCaseNode> selected = new ArrayList<>();
        for (TestCaseNode node : nodes) {
            if (node.getParentId() == null) {
                selected.add(node);
            }
        }
        return new CaseAssistSupport.ResolvedCases(document, List.copyOf(selected), byId, children);
    }

    private TestCaseNode attrNode(UUID parentId, String type, String title) {
        TestCaseNode node = new TestCaseNode();
        node.setId(UUID.randomUUID());
        node.setDocumentId(DOCUMENT_ID);
        node.setParentId(parentId);
        node.setType(type);
        node.setTitle(title);
        return node;
    }

    private TestCaseNode caseNode(String title) {
        TestCaseNode node = new TestCaseNode();
        node.setId(UUID.randomUUID());
        node.setDocumentId(DOCUMENT_ID);
        node.setType(Constants.NodeType.CASE);
        node.setTitle(title);
        node.setPriority("P1");
        node.setTags(List.of("登录"));
        return node;
    }

    private Requirement requirement() {
        Requirement requirement = new Requirement();
        requirement.setId(REQUIREMENT_ID);
        requirement.setProjectId(PROJECT_ID);
        requirement.setCode("REQ-1");
        requirement.setTitle("验证码登录");
        requirement.setDescription("验证码 60 秒内不可重发");
        return requirement;
    }

    private static String replyOf(UUID nodeId, String precondition) {
        String fields = precondition == null
                ? "\"steps\":[\"点击登录\"]"
                : "\"precondition\":\"" + precondition + "\",\"steps\":[\"点击获取\"]";
        return "{\"artifacts\":[{\"content\":{\"nodeId\":\"" + nodeId + "\",\"fields\":{" + fields + "},"
                + "\"extraNodes\":[{\"title\":\"验证码 10 分钟后过期校验\",\"isTestCase\":true}],"
                + "\"sourceRefs\":[{\"requirementId\":\"" + REQUIREMENT_ID
                + "\",\"quote\":\"验证码 60 秒内不可重发\"}]}}]}";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> content(Map<String, Object> artifact) {
        return (Map<String, Object>) artifact.get("content");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object raw) {
        return raw instanceof Map ? (Map<String, Object>) raw : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object raw) {
        return raw instanceof List ? (List<Map<String, Object>>) raw : List.of();
    }
}
