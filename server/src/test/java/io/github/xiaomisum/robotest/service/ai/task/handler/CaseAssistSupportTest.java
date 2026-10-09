package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport.completeCaseContext;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport.displayPriority;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport.documentContext;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport.existingFields;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport.priorityCaseContext;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport.requireDocumentId;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport.requireNodeIds;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport.requirementContext;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport.scopeRequirementIds;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport.sourceRefsOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 辅助功能共用支撑单测（C8）：输入解析错误码矩阵、选中范围与上游需求回溯校验、提示词上下文装配。
 */
@ExtendWith(MockitoExtension.class)
class CaseAssistSupportTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();

    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private TraceEdgeMapper traceEdgeMapper;

    @InjectMocks
    private CaseAssistSupport support;

    // ---------- 输入解析 ----------

    @Test
    void requireDocumentId_missingOrMalformed_throws115() {
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> requireDocumentId(Map.of())).getCode());
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class,
                        () -> requireDocumentId(Map.of("documentId", "not-a-uuid"))).getCode());
        assertEquals(DOCUMENT_ID, requireDocumentId(Map.of("documentId", DOCUMENT_ID.toString())));
    }

    @Test
    void requireNodeIds_missingEmptyOversize_throws302() {
        assertEquals(ErrorCodeConstants.ASSISTED_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> requireNodeIds(Map.of())).getCode());
        assertEquals(ErrorCodeConstants.ASSISTED_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> requireNodeIds(Map.of("nodeIds", List.of()))).getCode());

        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooMany.add(UUID.randomUUID());
        }
        assertEquals(ErrorCodeConstants.ASSISTED_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> requireNodeIds(Map.of("nodeIds", tooMany))).getCode());
    }

    @Test
    void requireNodeIds_malformedForm_throws115() {
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class,
                        () -> requireNodeIds(Map.of("nodeIds", CASE_ID.toString()))).getCode());
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class,
                        () -> requireNodeIds(Map.of("nodeIds", List.of("nope")))).getCode());
    }

    @Test
    void requireNodeIds_dedupesPreservingOrder() {
        List<UUID> parsed = requireNodeIds(Map.of("nodeIds",
                List.of(CASE_ID.toString(), CASE_ID.toString(), UUID.randomUUID().toString())));

        assertEquals(2, parsed.size());
        assertEquals(CASE_ID, parsed.get(0));
    }

    @Test
    void scopeRequirementIds_absentReturnsEmpty_malformedThrows115() {
        assertTrue(scopeRequirementIds(Map.of()).isEmpty());
        assertTrue(scopeRequirementIds(Map.of("scope", Map.of())).isEmpty());
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class,
                        () -> scopeRequirementIds(Map.of("scope", "bad"))).getCode());
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class,
                        () -> scopeRequirementIds(Map.of("scope", Map.of("requirementIds", "bad")))).getCode());
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class,
                        () -> scopeRequirementIds(Map.of("scope",
                                Map.of("requirementIds", List.of("bad"))))).getCode());

        assertEquals(List.of(REQUIREMENT_ID), scopeRequirementIds(Map.of("scope",
                Map.of("requirementIds", List.of(REQUIREMENT_ID.toString())))));
    }

    @Test
    void displayPriority_mapsP0ToP2AndKeepsUnknownNull() {
        assertEquals("high", displayPriority("P0"));
        assertEquals("medium", displayPriority("P1"));
        assertEquals("low", displayPriority("P2"));
        assertEquals("low", displayPriority("P3"));
        assertNull(displayPriority(null));
        assertNull(displayPriority("P9"));
    }

    // ---------- 归属校验与需求回溯 ----------

    @Test
    void resolve_documentMissingOrForeignProject_throws301() {
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(null);
        assertEquals(ErrorCodeConstants.ASSISTED_NODE_NOT_FOUND.code(),
                assertThrows(ServiceException.class,
                        () -> support.resolve(PROJECT_ID, DOCUMENT_ID, List.of(CASE_ID))).getCode());

        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(documentOf(UUID.randomUUID()));
        assertEquals(ErrorCodeConstants.ASSISTED_NODE_NOT_FOUND.code(),
                assertThrows(ServiceException.class,
                        () -> support.resolve(PROJECT_ID, DOCUMENT_ID, List.of(CASE_ID))).getCode());
    }

    @Test
    void resolve_nodeNotInDocument_throws301() {
        stubDocumentWithNodes();
        when(testCaseNodeMapper.listByDocumentId(DOCUMENT_ID)).thenReturn(List.of(caseNode("登录", null)));

        assertEquals(ErrorCodeConstants.ASSISTED_NODE_NOT_FOUND.code(),
                assertThrows(ServiceException.class,
                        () -> support.resolve(PROJECT_ID, DOCUMENT_ID, List.of(CASE_ID))).getCode());
    }

    @Test
    void resolve_nonCaseNode_throws307() {
        TestCaseNode normal = caseNode("登录", "normal");
        normal.setId(CASE_ID);
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(documentOf(PROJECT_ID));
        when(testCaseNodeMapper.listByDocumentId(DOCUMENT_ID)).thenReturn(List.of(normal));

        assertEquals(ErrorCodeConstants.ASSISTED_NODE_TYPE_UNSUPPORTED.code(),
                assertThrows(ServiceException.class,
                        () -> support.resolve(PROJECT_ID, DOCUMENT_ID, List.of(CASE_ID))).getCode());
    }

    @Test
    void resolve_returnsSelectedNodesInInputOrder() {
        TestCaseNode first = caseNode("登录", null);
        first.setId(CASE_ID);
        TestCaseNode second = caseNode("注册", null);
        second.setId(UUID.randomUUID());
        stubDocumentWithNodes();
        when(testCaseNodeMapper.listByDocumentId(DOCUMENT_ID)).thenReturn(List.of(second, first));

        CaseAssistSupport.ResolvedCases resolved = support.resolve(PROJECT_ID, DOCUMENT_ID,
                List.of(first.getId(), second.getId()));

        assertEquals(2, resolved.nodes().size());
        assertEquals(first.getId(), resolved.nodes().get(0).getId());
        assertEquals(second.getId(), resolved.nodes().get(1).getId());
        assertEquals(DOCUMENT_ID, resolved.document().getId());
        assertTrue(resolved.childrenByParent().isEmpty());
    }

    @Test
    void upstreamRequirements_explicitScopeForeignProject_throws115() {
        Requirement foreign = requirement();
        foreign.setProjectId(UUID.randomUUID());
        when(requirementMapper.listByIds(any())).thenReturn(List.of(foreign));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> support.upstreamRequirements(PROJECT_ID,
                        List.of(CASE_ID), List.of(REQUIREMENT_ID))).getCode());
    }

    @Test
    void upstreamRequirements_derivationEdgesResolveRequirements() {
        TraceEdge edge = new TraceEdge();
        edge.setEdgeType("derivation");
        edge.setSourceType("requirement");
        edge.setSourceId(REQUIREMENT_ID);
        when(traceEdgeMapper.listActiveByTarget(any(), any())).thenReturn(List.of(edge));
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement()));

        List<Requirement> found = support.upstreamRequirements(PROJECT_ID, List.of(CASE_ID), List.of());

        assertEquals(1, found.size());
        assertEquals(REQUIREMENT_ID, found.get(0).getId());
    }

    @Test
    void upstreamRequirements_noEdgesReturnsEmpty() {
        when(traceEdgeMapper.listActiveByTarget(any(), any())).thenReturn(List.of());

        assertTrue(support.upstreamRequirements(PROJECT_ID, List.of(CASE_ID), List.of()).isEmpty());
    }

    // ---------- 提示词上下文 ----------

    @Test
    void existingFields_groupsChildrenByTypeAndSortOrder() {
        TestCaseNode node = caseNode("验证码重发", null);
        node.setId(CASE_ID);
        node.setTags(List.of("登录"));
        TestCaseNode stepB = attrNode(Constants.NodeType.STEP, "60 秒内再次点击", 2);
        TestCaseNode stepA = attrNode(Constants.NodeType.STEP, "点击获取验证码", 1);
        TestCaseNode expected = attrNode(Constants.NodeType.EXPECTED, "发送成功提示", 1);
        TestCaseNode precondition = attrNode(Constants.NodeType.PRECONDITION, "手机号已输入", 1);

        CaseAssistSupport.ResolvedCases resolved = new CaseAssistSupport.ResolvedCases(
                documentOf(PROJECT_ID), List.of(node), Map.of(CASE_ID, node),
                Map.of(CASE_ID, List.of(stepB, stepA, expected, precondition)));

        CaseAssistSupport.ExistingFields fields = existingFields(node, resolved);
        assertEquals("手机号已输入", fields.precondition());
        assertEquals(List.of("点击获取验证码", "60 秒内再次点击"), fields.steps());
        assertEquals(List.of("发送成功提示"), fields.expected());
        assertEquals(List.of("登录"), fields.tags());
    }

    @Test
    void contexts_containNodeRequirementAndEmptyMarkers() {
        TestCaseNode node = caseNode("验证码重发", null);
        node.setId(CASE_ID);
        node.setPriority("P0");
        CaseAssistSupport.ResolvedCases resolved = new CaseAssistSupport.ResolvedCases(
                documentOf(PROJECT_ID), List.of(node), Map.of(CASE_ID, node), Map.of());

        assertTrue(completeCaseContext(resolved).contains("步骤：（空）"));
        assertTrue(completeCaseContext(resolved).contains("优先级 high"));
        assertTrue(priorityCaseContext(resolved).contains("当前级别 high"));
        assertTrue(priorityCaseContext(resolved).contains("（文档根）"));
        assertTrue(documentContext(resolved).contains(node.getTitle()));

        assertTrue(requirementContext(List.of()).contains("（无上游需求）"));
        String context = requirementContext(List.of(requirement()));
        assertTrue(context.contains("REQ-1"));
        assertTrue(context.contains("摘要："));
    }

    @Test
    void sourceRefsOf_filtersOutsideScopeAndCapsQuote() {
        Map<String, Object> suggestion = Map.of("sourceRefs", List.of(
                Map.of("requirementId", REQUIREMENT_ID.toString(), "quote", "x".repeat(400)),
                Map.of("requirementId", UUID.randomUUID().toString(), "quote", "范围外")));
        Map<UUID, Requirement> scope = Map.of(REQUIREMENT_ID, requirement());

        List<Map<String, Object>> refs = sourceRefsOf(suggestion, scope, true);

        assertEquals(1, refs.size());
        assertEquals("requirement", refs.get(0).get("type"));
        assertEquals("REQ-1", refs.get(0).get("title"));
        assertEquals(301, String.valueOf(refs.get(0).get("quote")).length());
        assertTrue(sourceRefsOf(suggestion, scope, false).stream().noneMatch(ref -> ref.containsKey("quote")));
        assertTrue(sourceRefsOf(null, scope, true).isEmpty());
    }

    // ---------- 辅助 ----------

    private void stubDocumentWithNodes() {
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(documentOf(PROJECT_ID));
    }

    private TestCaseDocument documentOf(UUID projectId) {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(projectId);
        document.setName("登录用例");
        return document;
    }

    private TestCaseNode caseNode(String title, String type) {
        TestCaseNode node = new TestCaseNode();
        node.setId(UUID.randomUUID());
        node.setTitle(title);
        node.setType(type == null ? "case" : type);
        return node;
    }

    private TestCaseNode attrNode(String type, String title, int sortOrder) {
        TestCaseNode node = caseNode(title, type);
        node.setParentId(CASE_ID);
        node.setSortOrder(sortOrder);
        return node;
    }

    private Requirement requirement() {
        Requirement requirement = new Requirement();
        requirement.setId(REQUIREMENT_ID);
        requirement.setProjectId(PROJECT_ID);
        requirement.setCode("REQ-1");
        requirement.setTitle("用户登录");
        requirement.setDescription("支持账号密码登录与验证码登录，验证码 60 秒内不可重发");
        return requirement;
    }
}
