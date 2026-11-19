package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptFixtures;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseNodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 用例优化承接单测（C8）：属性字段整体替换、编辑值落库、标签仅补空缺与级别应用、同级留痕、畸形载荷拒绝。
 */
@ExtendWith(MockitoExtension.class)
class CaseOptimizeAdopterTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID PARENT_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();

    @Mock
    private AssistAdoptSupport support;
    @Mock
    private TestCaseNodeService testCaseNodeService;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;

    @Captor
    private ArgumentCaptor<List<Map<String, Object>>> changesCaptor;
    @Captor
    private ArgumentCaptor<TestCaseNode> nodeCaptor;

    @InjectMocks
    private CaseOptimizeAdopter adopter;

    @Test
    void type_isCaseOptimize() {
        assertEquals("case_optimize", adopter.type());
    }

    @Test
    void adopt_rejected_returnsNullAndSkipsEverything() {
        assertNull(adopter.adopt(context(Constants.AiProposalAction.REJECTED, null, defaultArtifact("P1", "P0"))));

        verify(testCaseNodeService, never()).updateCaseFields(any(), any(), any(), any());
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
    }

    @Test
    void adopt_replacesAttributeFieldsAndAppliesPriority() {
        // 前置条件与步骤已有旧值但建议值不同 → 整体替换；预期结果空缺 → 补齐；标签已填保持原值（详设 4.2）
        stubTarget(List.of(child(Constants.NodeType.PRECONDITION, "手机号已输入", 0),
                child(Constants.NodeType.STEP, "点击获取验证码", 1)), node(List.of("登录"), "P1"));

        AdoptOutcome outcome = adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null,
                defaultArtifact("P1", "P0")));

        verify(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(OPERATOR_ID), eq(NODE_ID),
                changesCaptor.capture());
        List<Map<String, Object>> changes = changesCaptor.getValue();
        assertEquals(List.of("precondition", "steps", "expected"),
                changes.stream().map(change -> String.valueOf(change.get("field"))).toList());
        for (Map<String, Object> change : changes) {
            assertEquals("replace", change.get("op"));
        }
        assertEquals("手机号已输入并获取过一次验证码", changes.get(0).get("value"));
        assertEquals(List.of("点击获取验证码", "60 秒内再次点击"), changes.get(1).get("value"));
        assertEquals(List.of("发送成功提示"), changes.get(2).get("value"));

        // 级别变化 → 部分更新载体只带 id + priority（C11）；标签未变化不写载体
        verify(testCaseNodeMapper).updateById(nodeCaptor.capture());
        assertEquals(NODE_ID, nodeCaptor.getValue().getId());
        assertEquals("P0", nodeCaptor.getValue().getPriority());
        assertNull(nodeCaptor.getValue().getTags());

        assertEquals(NODE_ID, outcome.createdId());
        assertEquals(NODE_ID.toString(), outcome.adoptedRef().get("nodeId"));
        assertEquals("P1", outcome.adoptedRef().get("from"));
        assertEquals("P0", outcome.adoptedRef().get("to"));
        verify(support).record(any(), eq("TEST_CASE_NODE"), eq(NODE_ID), anyString(),
                eq("CASE_UPDATED"), anyString());
    }

    @Test
    void adopt_editedContentWritesEditedFieldsTagsAndPriority() {
        stubTarget(List.of(child(Constants.NodeType.PRECONDITION, "手机号已输入", 0),
                child(Constants.NodeType.STEP, "点击获取验证码", 1)), node(List.of("登录"), "P1"));
        Map<String, Object> edited = Map.of(
                "fields", Map.of("precondition", "手机号已输入并获取过一次验证码",
                        "tags", List.of("登录", "验证码")),
                "priority", "P2");

        adopter.adopt(context(Constants.AiProposalAction.ADOPTED_EDITED, edited, defaultArtifact("P1", "P0")));

        verify(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(OPERATOR_ID), eq(NODE_ID),
                changesCaptor.capture());
        List<String> fields = changesCaptor.getValue().stream()
                .map(entry -> String.valueOf(entry.get("field"))).toList();
        // 编辑载荷的前置条件改写 + 步骤与预期结果按建议值整体替换（步骤旧值与建议不同）
        assertEquals(List.of("precondition", "steps", "expected"), fields);
        assertEquals("手机号已输入并获取过一次验证码", changesCaptor.getValue().get(0).get("value"));
        assertEquals(List.of("点击获取验证码", "60 秒内再次点击"), changesCaptor.getValue().get(1).get("value"));

        verify(testCaseNodeMapper, times(2)).updateById(nodeCaptor.capture());
        List<TestCaseNode> carriers = nodeCaptor.getAllValues();
        assertEquals(List.of("登录", "验证码"), carriers.get(0).getTags());
        assertNull(carriers.get(0).getPriority());
        assertEquals("P2", carriers.get(1).getPriority());
        assertNull(carriers.get(1).getTags());
    }

    @Test
    void adopt_emptySuggestionKeepsExistingAttribute() {
        stubTarget(List.of(child(Constants.NodeType.PRECONDITION, "手机号已输入", 0)), node(List.of(), "P1"));
        Map<String, Object> artifact = artifact(
                Map.of("precondition", pair("手机号已输入", null),
                        "steps", pair(List.of(), List.of()),
                        "expected", pair(List.of(), List.of()),
                        "tags", pair(List.of(), List.of())),
                "P1", "P1");

        adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null, artifact));

        verify(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(OPERATOR_ID), eq(NODE_ID),
                changesCaptor.capture());
        assertTrue(changesCaptor.getValue().isEmpty());
    }

    @Test
    void adopt_unchangedFieldsSkipWriteButStillRecordAdoption() {
        // 既有值已等于建议值、级别也一致 → 无字段与级别写入，但采纳仍有可见留痕（详设 4.2）
        stubTarget(List.of(
                child(Constants.NodeType.PRECONDITION, "手机号已输入并获取过一次验证码", 0),
                child(Constants.NodeType.STEP, "点击获取验证码", 1),
                child(Constants.NodeType.EXPECTED, "发送成功提示", 2)), node(List.of("登录", "验证码"), "P0"));
        Map<String, Object> artifact = artifact(
                Map.of("precondition", pair("手机号已输入并获取过一次验证码", "手机号已输入并获取过一次验证码"),
                        "steps", pair(List.of("点击获取验证码"), List.of("点击获取验证码")),
                        "expected", pair(List.of("发送成功提示"), List.of("发送成功提示")),
                        "tags", pair(List.of("登录", "验证码"), List.of("登录", "验证码"))),
                "P0", "P0");

        AdoptOutcome outcome = adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null, artifact));

        verify(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(OPERATOR_ID), eq(NODE_ID),
                changesCaptor.capture());
        assertTrue(changesCaptor.getValue().isEmpty());
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
        verify(support).record(any(), eq("TEST_CASE_NODE"), eq(NODE_ID), anyString(),
                eq("CASE_UPDATED"), anyString());
        assertEquals("P0", outcome.adoptedRef().get("from"));
        assertEquals("P0", outcome.adoptedRef().get("to"));
    }

    @Test
    void adopt_samePrioritySkipsColumnButRecordsAdoption() {
        stubTarget(List.of(), node(List.of(), "P1"));
        Map<String, Object> artifact = artifact(
                Map.of("precondition", pair(null, null),
                        "steps", pair(List.of(), List.of()),
                        "expected", pair(List.of(), List.of()),
                        "tags", pair(List.of(), List.of())),
                "P1", "P1");

        AdoptOutcome outcome = adopter.adopt(context(Constants.AiProposalAction.ADOPTED, null, artifact));

        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
        verify(support).record(any(), eq("TEST_CASE_NODE"), eq(NODE_ID), anyString(),
                eq("CASE_UPDATED"), anyString());
        assertEquals("P1", outcome.adoptedRef().get("from"));
        assertEquals("P1", outcome.adoptedRef().get("to"));
    }

    @Test
    void adopt_malformedStepsInEditedContentThrowsInputInvalid() {
        stubTarget(List.of(), node(List.of(), "P1"));
        Map<String, Object> edited = Map.of("fields", Map.of("steps", "不是列表"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiProposalAction.ADOPTED_EDITED, edited,
                        defaultArtifact("P1", "P0"))));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
        verify(testCaseNodeService, never()).updateCaseFields(any(), any(), any(), any());
    }

    @Test
    void adopt_malformedEditedPriorityThrowsBeforeAnyWrite() {
        stubTarget(List.of(), node(List.of(), "P1"));
        Map<String, Object> edited = Map.of("fields", Map.of("precondition", "改写值"), "priority", "urgent");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiProposalAction.ADOPTED_EDITED, edited,
                        defaultArtifact("P1", "P0"))));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
        verify(testCaseNodeService, never()).updateCaseFields(any(), any(), any(), any());
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
    }

    // ---------- fixtures ----------

    private void stubTarget(List<TestCaseNode> children, TestCaseNode node) {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(PROJECT_ID);
        Map<UUID, List<TestCaseNode>> childrenByParent = new LinkedHashMap<>();
        childrenByParent.put(NODE_ID, children);
        CaseAssistSupport.ResolvedCases resolved = new CaseAssistSupport.ResolvedCases(
                document, List.of(node), Map.of(NODE_ID, node), childrenByParent);
        lenient().when(support.resolveCaseTarget(any()))
                .thenReturn(new AssistAdoptSupport.CaseTarget(document, node, resolved));
    }

    private TestCaseNode node(List<String> tags, String priority) {
        TestCaseNode node = new TestCaseNode();
        node.setId(NODE_ID);
        node.setDocumentId(DOCUMENT_ID);
        node.setParentId(PARENT_ID);
        node.setType(Constants.NodeType.CASE);
        node.setTitle("验证码 60 秒重发限制");
        node.setPriority(priority);
        node.setTags(tags);
        return node;
    }

    private TestCaseNode child(String type, String title, int order) {
        TestCaseNode child = new TestCaseNode();
        child.setId(UUID.randomUUID());
        child.setParentId(NODE_ID);
        child.setType(type);
        child.setTitle(title);
        child.setSortOrder(order);
        return child;
    }

    private AdoptContext context(String action, Map<String, Object> edited, Map<String, Object> artifact) {
        LoginUser loginUser = mock(LoginUser.class);
        AiTask task = new AiTask();
        task.setType("case_optimize");
        return new AdoptContext(task, AdoptFixtures.proposal(artifact), action, edited, null,
                null, null, null, DOCUMENT_ID, null, null, PROJECT_ID, OPERATOR_ID, Set.of(), loginUser);
    }

    private Map<String, Object> defaultArtifact(String priority, String suggested) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("precondition", pair("手机号已输入", "手机号已输入并获取过一次验证码"));
        fields.put("steps", pair(List.of("点击获取验证码"), List.of("点击获取验证码", "60 秒内再次点击")));
        fields.put("expected", pair(List.of(), List.of("发送成功提示")));
        fields.put("tags", pair(List.of("登录"), List.of("登录", "验证码")));
        return artifact(fields, priority, suggested);
    }

    private static Map<String, Object> pair(Object existing, Object suggested) {
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("existing", existing);
        pair.put("suggested", suggested);
        return pair;
    }

    private Map<String, Object> artifact(Map<String, Object> fields, String priority, String suggested) {
        Map<String, Object> prioritySuggestion = new LinkedHashMap<>();
        prioritySuggestion.put("current", priority);
        prioritySuggestion.put("suggested", suggested);
        prioritySuggestion.put("reason", "上游需求优先级为高");

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("nodeId", NODE_ID.toString());
        content.put("fields", fields);
        content.put("priority", prioritySuggestion);
        content.put("sourceRefs", List.of());
        return Map.of("key", "node-" + NODE_ID, "kind", Constants.AiProposalKind.CASE_OPTIMIZE_SUGGESTION,
                "content", content);
    }
}
