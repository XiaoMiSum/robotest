package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseNodeService;
import io.github.xiaomisum.robotest.service.trace.TraceEdgeWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseCompleteAdopterTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID PARENT_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();

    @Mock
    private AssistAdoptSupport support;
    @Mock
    private TestCaseNodeService testCaseNodeService;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TraceEdgeMapper traceEdgeMapper;
    @Mock
    private TraceEdgeWriter traceEdgeWriter;
    @Mock
    private VectorIndexService vectorIndexService;

    @Captor
    private ArgumentCaptor<List<Map<String, Object>>> changesCaptor;
    @Captor
    private ArgumentCaptor<TestCaseNode> nodeCaptor;
    @Captor
    private ArgumentCaptor<List<UUID>> sourcesCaptor;
    @Captor
    private ArgumentCaptor<UUID> targetCaptor;

    @InjectMocks
    private CaseCompleteAdopter adopter;

    @Test
    void type_isCaseComplete() {
        assertEquals("case_complete", adopter.type());
    }

    @Test
    void adopt_rejected_returnsNullAndSkipsEverything() {
        assertNull(adopter.adopt(context(Constants.AiArtifactAction.REJECTED, null, null)));

        verify(support, never()).requirePermission(any(), anyString());
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
    }

    @Test
    void adopt_withoutCaseEditPermission_failsBeforeAnyWrite() {
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_NO_PERMISSION))
                .when(support).requirePermission(any(), eq("case:edit"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, null)));

        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(), exception.getCode());
        verify(support, never()).resolveCaseTarget(any());
    }

    @Test
    void adopt_fillsOnlyEmptyFieldsAndLeavesManuallyFilledOnes() {
        // existing 非空的前置条件与步骤不被覆盖，仅空缺的预期结果补齐（详设 4.2 覆盖口径）
        stubTarget(List.of(child(Constants.NodeType.PRECONDITION, "手机号已输入", 0),
                child(Constants.NodeType.STEP, "点击获取验证码", 1)), node(List.of("登录")));

        AdoptOutcome outcome = adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, null));

        verify(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(OPERATOR_ID), eq(NODE_ID),
                changesCaptor.capture());
        assertEquals(1, changesCaptor.getValue().size());
        Map<String, Object> change = changesCaptor.getValue().get(0);
        assertEquals("expected", change.get("field"));
        assertEquals("replace", change.get("op"));
        assertEquals(List.of("发送成功提示"), change.get("value"));

        // 标签 existing 非空 → 不写 tags 载体；无补充节点 → 不建边、不重嵌
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
        verify(traceEdgeWriter, never()).writeAiDerivationEdges(any(), anyCollection(), anyString(),
                any(), anyString());
        verify(vectorIndexService, never()).upsertTestCase(any(), any());

        assertEquals(NODE_ID, outcome.createdId());
        assertEquals(NODE_ID.toString(), outcome.adoptedRef().get("nodeId"));
        assertEquals(List.of(), outcome.adoptedRef().get("createdNodeIds"));
        verify(support).record(any(), eq("TEST_CASE_NODE"), eq(NODE_ID), anyString(),
                eq("CASE_UPDATED"), anyString());
    }

    @Test
    void adopt_editedContentWritesEditedValuesAndTags() {
        stubTarget(List.of(child(Constants.NodeType.PRECONDITION, "手机号已输入", 0),
                child(Constants.NodeType.STEP, "点击获取验证码", 1)));
        Map<String, Object> edited = Map.of("fields", Map.of(
                "precondition", "手机号已输入并获取过一次验证码",
                "tags", List.of("登录", "验证码")));

        adopter.adopt(context(Constants.AiArtifactAction.ADOPTED_EDITED, edited, null));

        verify(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(OPERATOR_ID), eq(NODE_ID),
                changesCaptor.capture());
        List<String> fields = changesCaptor.getValue().stream()
                .map(entry -> String.valueOf(entry.get("field"))).toList();
        // 编辑载荷中的前置条件改写 + 未编辑且空缺的预期结果补齐；已填的步骤不动
        assertEquals(List.of("precondition", "expected"), fields);
        assertEquals("手机号已输入并获取过一次验证码", changesCaptor.getValue().get(0).get("value"));

        verify(testCaseNodeMapper).updateById(nodeCaptor.capture());
        assertEquals(List.of("登录", "验证码"), nodeCaptor.getValue().getTags());
        assertNull(nodeCaptor.getValue().getPriority());
    }

    @Test
    void adopt_unchangedFieldsSkipWriteAndActivity() {
        // 既有值已等于建议值 → 无变化即无写入
        stubTarget(List.of(
                child(Constants.NodeType.PRECONDITION, "手机号已输入并获取过一次验证码", 0),
                child(Constants.NodeType.STEP, "点击获取验证码", 1),
                child(Constants.NodeType.EXPECTED, "发送成功提示", 2)),
                node(List.of("登录")));

        adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, null));

        verify(testCaseNodeService).updateCaseFields(eq(PROJECT_ID), eq(OPERATOR_ID), eq(NODE_ID),
                changesCaptor.capture());
        assertTrue(changesCaptor.getValue().isEmpty());
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
        verify(support, never()).record(any(), anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void adopt_extraNodesInheritDerivationEdgesAndReindexOnce() {
        stubTarget(List.of());
        assignInsertedIds();
        when(testCaseNodeMapper.listByParentId(PARENT_ID)).thenReturn(List.of());
        when(traceEdgeMapper.listActiveByTarget(Constants.TraceNodeType.TEST_CASE, List.of(NODE_ID)))
                .thenReturn(List.of(derivationEdge()));
        Map<String, Object> edited = Map.of("extraNodes", List.of(
                Map.of("title", "补充用例", "isTestCase", true),
                Map.of("title", "补充结构", "isTestCase", false)));

        AdoptOutcome outcome = adopter.adopt(context(Constants.AiArtifactAction.ADOPTED_EDITED, edited, null));

        verify(testCaseNodeMapper, times(2)).insert(nodeCaptor.capture());
        TestCaseNode created = nodeCaptor.getAllValues().get(0);
        assertEquals(Constants.NodeType.CASE, created.getType());
        assertEquals(PARENT_ID, created.getParentId());
        assertEquals(Integer.valueOf(0), created.getSortOrder());
        assertEquals("P1", created.getPriority());
        assertTrue(created.getAiGenerated());
        TestCaseNode structure = nodeCaptor.getAllValues().get(1);
        assertEquals(Constants.NodeType.NORMAL, structure.getType());
        assertNull(structure.getPriority());

        verify(traceEdgeWriter).writeAiDerivationEdges(eq(PROJECT_ID), sourcesCaptor.capture(),
                eq(Constants.TraceNodeType.TEST_CASE), targetCaptor.capture(), eq("v1"));
        assertEquals(List.of(REQUIREMENT_ID), sourcesCaptor.getValue());
        assertEquals(created.getId(), targetCaptor.getValue());
        // 用例实体批内统一重嵌一次（direct insert 不触发既有建用例入口）
        verify(vectorIndexService, times(1)).upsertTestCase(DOCUMENT_ID, OPERATOR_ID);

        assertEquals(List.of(created.getId().toString(), structure.getId().toString()),
                outcome.adoptedRef().get("createdNodeIds"));
    }

    @Test
    void adopt_extraNodePositionChildPlacesUnderSourceNode() {
        stubTarget(List.of());
        assignInsertedIds();
        when(testCaseNodeMapper.listByParentId(NODE_ID)).thenReturn(List.of());
        Map<String, Object> edited = Map.of("extraNodes", List.of(Map.of("title", "补充用例")));

        adopter.adopt(context(Constants.AiArtifactAction.ADOPTED_EDITED, edited, "child"));

        verify(testCaseNodeMapper).insert(nodeCaptor.capture());
        assertEquals(NODE_ID, nodeCaptor.getValue().getParentId());
    }

    @Test
    void adopt_siblingPositionOnRootCaseFallsBackToChild() {
        TestCaseNode rootCase = node(List.of());
        rootCase.setParentId(null);
        stubTarget(List.of(), rootCase);
        assignInsertedIds();
        when(testCaseNodeMapper.listByParentId(NODE_ID)).thenReturn(List.of());
        Map<String, Object> edited = Map.of("extraNodes", List.of(Map.of("title", "补充用例")));

        adopter.adopt(context(Constants.AiArtifactAction.ADOPTED_EDITED, edited, null));

        verify(testCaseNodeMapper).insert(nodeCaptor.capture());
        // 根下用例无父级可挂，补充节点回退为其子节点，避免脱离文档树
        assertEquals(NODE_ID, nodeCaptor.getValue().getParentId());
    }

    @Test
    void adopt_malformedStepsInEditedContentThrowsInputInvalid() {
        stubTarget(List.of());
        Map<String, Object> edited = Map.of("fields", Map.of("steps", "不是列表"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED_EDITED, edited, null)));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
        verify(testCaseNodeService, never()).updateCaseFields(any(), any(), any(), any());
    }

    // ---------- fixtures ----------

    private void stubTarget(List<TestCaseNode> children) {
        stubTarget(children, node(List.of()));
    }

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

    /** 模拟 MyBatis-Plus 主键回填，便于断言新建节点 ID */
    private void assignInsertedIds() {
        lenient().doAnswer(invocation -> {
            invocation.getArgument(0, TestCaseNode.class).setId(UUID.randomUUID());
            return 1;
        }).when(testCaseNodeMapper).insert(any(TestCaseNode.class));
    }

    private static TraceEdge derivationEdge() {
        TraceEdge edge = new TraceEdge();
        edge.setEdgeType(Constants.TraceEdgeType.DERIVATION);
        edge.setSourceType(Constants.TraceNodeType.REQUIREMENT);
        edge.setSourceId(REQUIREMENT_ID);
        return edge;
    }

    private TestCaseNode node(List<String> tags) {
        TestCaseNode node = new TestCaseNode();
        node.setId(NODE_ID);
        node.setDocumentId(DOCUMENT_ID);
        node.setParentId(PARENT_ID);
        node.setType(Constants.NodeType.CASE);
        node.setTitle("验证码 60 秒重发限制");
        node.setPriority("P1");
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

    private AdoptContext context(String action, Map<String, Object> edited, String extraNodePosition) {
        LoginUser loginUser = mock(LoginUser.class);
        lenient().when(loginUser.getPermissions()).thenReturn(List.of("case:edit"));
        AiTask task = new AiTask();
        task.setType("case_complete");
        return new AdoptContext(task, artifact(), action, edited, null, null, null, null, null,
                DOCUMENT_ID, extraNodePosition, null, null, PROJECT_ID, OPERATOR_ID, loginUser);
    }

    private Map<String, Object> artifact() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("precondition", Map.of("existing", "手机号已输入",
                "suggested", "手机号已输入并获取过一次验证码"));
        fields.put("steps", Map.of("existing", List.of("点击获取验证码"),
                "suggested", List.of("点击获取验证码", "60 秒内再次点击")));
        fields.put("expected", Map.of("existing", List.of(), "suggested", List.of("发送成功提示")));
        fields.put("tags", Map.of("existing", List.of("登录"), "suggested", List.of("登录", "验证码")));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("nodeId", NODE_ID.toString());
        content.put("fields", fields);
        content.put("extraNodes", new ArrayList<>());
        return Map.of("key", "node-" + NODE_ID, "kind", Constants.AiArtifactKind.CASE_SUGGESTION, "content", content);
    }
}
