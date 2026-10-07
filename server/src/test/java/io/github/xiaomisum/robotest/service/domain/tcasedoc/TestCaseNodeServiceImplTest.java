package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.convert.TestCaseNodeConvertMapper;
import io.github.xiaomisum.robotest.model.convert.TestCaseNodeConvertMapperImpl;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseNodeUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseCaseListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseNodeServiceImplTest {

    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private VectorIndexService vectorIndexService;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Spy
    private TestCaseNodeConvertMapper testCaseNodeConvertMapper = new TestCaseNodeConvertMapperImpl();

    @InjectMocks
    private TestCaseNodeServiceImpl nodeService;

    private UUID documentId;
    private UUID caseId;
    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        documentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        caseId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        userId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        projectId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        // 纯单测无 MP 主键填充：插入桩补写 id，保证 createCase 能按 id 流转
        lenient().doAnswer(invocation -> {
            TestCaseNode inserted = invocation.getArgument(0);
            if (inserted.getId() == null) {
                inserted.setId(UUID.randomUUID());
            }
            return 1;
        }).when(testCaseNodeMapper).insert(any(TestCaseNode.class));
    }

    @Test
    void getDocumentNodes_success() {
        LinkedHashMap<String, Object> layoutMap = new LinkedHashMap<>();
        layoutMap.put("x", 0);
        layoutMap.put("y", 0);

        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setProjectId(projectId);

        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(doc);
        when(testCaseDocumentMapper.getLayout(documentId)).thenReturn(layoutMap);

        TestCaseNode root = new TestCaseNode();
        root.setId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        root.setDocumentId(documentId);
        root.setParentId(null);
        root.setType("normal");
        root.setTitle("Root");
        root.setSortOrder(0);
        root.setVersion(1);

        when(testCaseNodeMapper.listByDocumentId(documentId))
                .thenReturn(List.of(root));

        TestCaseDocumentNodesRespDTO result = nodeService.getDocumentNodes(projectId, documentId, userId);

        assertNotNull(result);
        assertNotNull(result.getNode());
        assertEquals("Root", result.getNode().getTitle());
        assertEquals(layoutMap, result.getLayout());
        verify(projectAccessGuard).requireProjectMember(projectId, userId);
    }

    @Test
    void getDocumentNodes_noLayout() {
        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setProjectId(projectId);

        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(doc);
        when(testCaseDocumentMapper.getLayout(documentId)).thenReturn(null);
        when(testCaseNodeMapper.listByDocumentId(documentId))
                .thenReturn(Collections.emptyList());

        TestCaseDocumentNodesRespDTO result = nodeService.getDocumentNodes(projectId, documentId, userId);

        assertNotNull(result);
        assertNull(result.getLayout());
        verify(projectAccessGuard).requireProjectMember(projectId, userId);
    }

    @Test
    void getDocumentNodes_notFound_throws() {
        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> nodeService.getDocumentNodes(projectId, documentId, userId));
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    @Test
    void getDocumentNodes_crossProject_throws() {
        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setProjectId(UUID.randomUUID());
        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(doc);

        // 归属活动项目校验（SEC-014）：跨项目按不存在处理，不泄露文档存在性
        ServiceException exception = assertThrows(ServiceException.class,
                () -> nodeService.getDocumentNodes(projectId, documentId, userId));
        assertEquals(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND.code(), exception.getCode());
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    @Test
    void getCaseDetail_crossProject_throws() {
        TestCaseNode node = new TestCaseNode();
        node.setId(caseId);
        node.setDocumentId(documentId);
        when(testCaseNodeMapper.selectById(caseId)).thenReturn(node);
        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setProjectId(UUID.randomUUID());
        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(doc);

        // 归属活动项目校验（SEC-014）：跨项目按用例节点不存在处理，不泄露用例存在性
        ServiceException exception = assertThrows(ServiceException.class,
                () -> nodeService.getCaseDetail(projectId, caseId, userId));
        assertEquals(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND.code(), exception.getCode());
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    @Test
    void getDocumentNodes_notDocumentType_throws() {
        // TestCaseDocument 无 type 字段（恒为文档），该场景等价于文档不存在
        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> nodeService.getDocumentNodes(projectId, documentId, userId));
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    @Test
    void getCaseDetail_success() {
        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setProjectId(projectId);
        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(doc);

        TestCaseNode node = new TestCaseNode();
        node.setId(caseId);
        node.setDocumentId(documentId);
        node.setType("case");
        node.setTitle("Test Case");
        node.setPriority("high");
        node.setSortOrder(0);
        node.setVersion(1);

        // 用例的子孙节点（前置/步骤/预期）应随明细一并返回
        TestCaseNode step = new TestCaseNode();
        step.setId(UUID.fromString("00000000-0000-0000-0000-000000000021"));
        step.setDocumentId(documentId);
        step.setParentId(caseId);
        step.setType("step");
        step.setTitle("Step 1");
        step.setSortOrder(0);
        step.setVersion(1);

        TestCaseNode expected = new TestCaseNode();
        expected.setId(UUID.fromString("00000000-0000-0000-0000-000000000022"));
        expected.setDocumentId(documentId);
        expected.setParentId(step.getId());
        expected.setType("expected");
        expected.setTitle("Expected 1");
        expected.setSortOrder(0);
        expected.setVersion(1);

        when(testCaseNodeMapper.selectById(caseId)).thenReturn(node);
        when(testCaseNodeMapper.listByDocumentId(documentId)).thenReturn(List.of(node, step, expected));

        TestCaseNodeTreeRespDTO result = nodeService.getCaseDetail(projectId, caseId, userId);

        assertNotNull(result);
        assertEquals("Test Case", result.getTitle());
        assertEquals("high", result.getPriority());
        assertEquals("case", result.getType());
        assertEquals(documentId, result.getDocumentId());
        assertEquals(1, result.getChildren().size());
        assertEquals("Step 1", result.getChildren().get(0).getTitle());
        assertEquals("Expected 1", result.getChildren().get(0).getChildren().get(0).getTitle());
        verify(projectAccessGuard).requireProjectMember(projectId, userId);
    }

    @Test
    void getCaseDetail_notFound_throws() {
        when(testCaseNodeMapper.selectById(caseId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> nodeService.getCaseDetail(projectId, caseId, userId));
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    // ========== getCaseList ==========

    @Test
    void getCaseList_success() {
        UUID projId = UUID.fromString("00000000-0000-0000-0000-000000000008");

        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setName("Doc 1");
        when(testCaseDocumentMapper.listByProjectId(projId))
                .thenReturn(List.of(doc));

        TestCaseNode node = new TestCaseNode();
        node.setId(caseId);
        node.setDocumentId(documentId);
        node.setType("case");
        node.setTitle("Test Case");
        node.setPriority("high");
        node.setSortOrder(0);
        node.setVersion(1);

        PageResult<TestCaseNode> page = new PageResult<>(List.of(node), 1L);
        doReturn(page).when(testCaseNodeMapper).findCasePage(
                any(PageParam.class), anyList(), isNull(), isNull());

        PageResult<TestCaseCaseListRespDTO> result = nodeService.getCaseList(
                projId, userId, null, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals("Test Case", result.getList().get(0).getTitle());
        assertEquals("Doc 1", result.getList().get(0).getDocumentName());
        verify(projectAccessGuard).requireProjectMember(projId, userId);
    }

    @Test
    void getCaseList_noDocuments() {
        UUID projId = UUID.fromString("00000000-0000-0000-0000-000000000008");
        when(testCaseDocumentMapper.listByProjectId(projId))
                .thenReturn(Collections.emptyList());

        PageResult<TestCaseCaseListRespDTO> result = nodeService.getCaseList(
                projId, userId, null, null, 1, 10);

        assertNotNull(result);
        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        verify(projectAccessGuard).requireProjectMember(projId, userId);
    }

    // ========== updateCaseNode ==========

    @Test
    void updateCaseNode_success() {
        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setProjectId(projectId);
        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(doc);

        TestCaseNode node = new TestCaseNode();
        node.setId(caseId);
        node.setDocumentId(documentId);
        node.setType("case");
        node.setTitle("Old Title");
        node.setPriority("low");

        when(testCaseNodeMapper.selectById(caseId)).thenReturn(node);

        TestCaseNodeUpdateReqDTO reqDTO = new TestCaseNodeUpdateReqDTO();
        reqDTO.setTitle("New Title");
        reqDTO.setPriority("high");

        nodeService.updateCaseNode(projectId, caseId, userId, reqDTO);

        // 更新载体仅携带 id + 本次传入字段，不再回写查询实体
        ArgumentCaptor<TestCaseNode> captor = ArgumentCaptor.forClass(TestCaseNode.class);
        verify(testCaseNodeMapper).updateById(captor.capture());
        assertEquals(caseId, captor.getValue().getId());
        assertEquals("New Title", captor.getValue().getTitle());
        assertEquals("high", captor.getValue().getPriority());
        verify(projectAccessGuard).requireProjectMember(projectId, userId);
    }

    @Test
    void updateCaseNode_notFound_throws() {
        when(testCaseNodeMapper.selectById(caseId)).thenReturn(null);

        TestCaseNodeUpdateReqDTO reqDTO = new TestCaseNodeUpdateReqDTO();
        reqDTO.setTitle("New Title");

        assertThrows(ServiceException.class,
                () -> nodeService.updateCaseNode(projectId, caseId, userId, reqDTO));
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    @Test
    void updateCaseNode_notCaseType_throws() {
        TestCaseNode node = new TestCaseNode();
        node.setId(caseId);
        node.setType("normal");
        node.setTitle("Folder");

        when(testCaseNodeMapper.selectById(caseId)).thenReturn(node);

        TestCaseNodeUpdateReqDTO reqDTO = new TestCaseNodeUpdateReqDTO();
        reqDTO.setTitle("New Title");

        assertThrows(ServiceException.class,
                () -> nodeService.updateCaseNode(projectId, caseId, userId, reqDTO));
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    // ========== AI 助手确认执行（createCase / updateCaseFields / tagCase） ==========

    @Test
    void createCase_defaultsFirstDocumentAndRootNode() {
        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setProjectId(projectId);
        when(testCaseDocumentMapper.listByProjectId(projectId)).thenReturn(List.of(doc));
        TestCaseNode root = new TestCaseNode();
        root.setId(UUID.randomUUID());
        root.setParentId(null);
        when(testCaseNodeMapper.listByDocumentId(documentId)).thenReturn(List.of(root));
        when(testCaseNodeMapper.listByParentId(root.getId())).thenReturn(List.of());

        Map<String, Object> fields = Map.of("title", "登录成功用例",
                "steps", List.of("输入用户名", "点击登录"));

        UUID created = nodeService.createCase(projectId, userId, null, null, fields);

        assertNotNull(created);
        ArgumentCaptor<TestCaseNode> captor = ArgumentCaptor.forClass(TestCaseNode.class);
        // case 节点 + 2 个步骤属性子节点
        verify(testCaseNodeMapper, times(3)).insert(captor.capture());
        List<TestCaseNode> inserted = captor.getAllValues();
        TestCaseNode caseNode = inserted.get(0);
        assertEquals(created, caseNode.getId());
        assertEquals("case", caseNode.getType());
        assertEquals("登录成功用例", caseNode.getTitle());
        assertEquals("P1", caseNode.getPriority());
        assertEquals(0, caseNode.getSortOrder());
        assertEquals(1, caseNode.getVersion());
        assertEquals(Boolean.TRUE, caseNode.getAiGenerated());
        assertEquals(root.getId(), caseNode.getParentId());
        assertEquals("step", inserted.get(1).getType());
        assertEquals("输入用户名", inserted.get(1).getTitle());
        assertEquals("step", inserted.get(2).getType());
        assertEquals("点击登录", inserted.get(2).getTitle());
        verify(vectorIndexService).upsertTestCase(documentId, userId);
        verify(projectAccessGuard).requireProjectMember(projectId, userId);
    }

    @Test
    void createCase_emptyTitle_throws1001() {
        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setProjectId(projectId);
        when(testCaseDocumentMapper.listByProjectId(projectId)).thenReturn(List.of(doc));
        TestCaseNode root = new TestCaseNode();
        root.setId(UUID.randomUUID());
        when(testCaseNodeMapper.listByDocumentId(documentId)).thenReturn(List.of(root));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> nodeService.createCase(projectId, userId, null, null,
                        Map.of("priority", "P0")));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
        verify(testCaseNodeMapper, never()).insert(any(TestCaseNode.class));
    }

    @Test
    void createCase_normalizesPriorityAndRejectsForeignParent() {
        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setProjectId(projectId);
        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(doc);
        UUID parentId = UUID.randomUUID();
        TestCaseNode parent = new TestCaseNode();
        parent.setId(parentId);
        parent.setDocumentId(documentId);
        when(testCaseNodeMapper.selectById(parentId)).thenReturn(parent);
        when(testCaseNodeMapper.listByParentId(parentId)).thenReturn(List.of());

        nodeService.createCase(projectId, userId, documentId, parentId,
                Map.of("title", "A", "priority", "low"));
        nodeService.createCase(projectId, userId, documentId, parentId,
                Map.of("title", "B", "priority", "urgent"));

        ArgumentCaptor<TestCaseNode> captor = ArgumentCaptor.forClass(TestCaseNode.class);
        verify(testCaseNodeMapper, times(2)).insert(captor.capture());
        assertEquals("P2", captor.getAllValues().get(0).getPriority());
        // 非法取值回退缺省 P1
        assertEquals("P1", captor.getAllValues().get(1).getPriority());

        // 父节点属其它文档 → 用例节点不存在
        parent.setDocumentId(UUID.randomUUID());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> nodeService.createCase(projectId, userId, documentId, parentId,
                        Map.of("title", "C")));
        assertEquals(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void updateCaseFields_replaceChildren_resequencesAndReindexes() {
        stubCaseNode();
        UUID oldStep1 = UUID.randomUUID();
        UUID oldStep2 = UUID.randomUUID();
        UUID precondId = UUID.randomUUID();
        UUID newStepId = UUID.randomUUID();
        // 第 1 次取子节点：applyAttribute 计算同类型范围；第 2 次：重排
        when(testCaseNodeMapper.listByParentId(caseId))
                .thenReturn(List.of(child(oldStep1, "step", 0), child(oldStep2, "step", 1)))
                .thenReturn(List.of(child(newStepId, "step", 5), child(precondId, "precondition", 9)));

        List<Map<String, Object>> changes = List.of(
                Map.of("field", "steps", "op", "replace", "value", List.of("新步骤一", "新步骤二")));

        nodeService.updateCaseFields(projectId, userId, caseId, changes);

        verify(testCaseNodeMapper).deleteByNodeIds(List.of(oldStep1, oldStep2));
        ArgumentCaptor<TestCaseNode> captor = ArgumentCaptor.forClass(TestCaseNode.class);
        verify(testCaseNodeMapper, times(2)).insert(captor.capture());
        assertEquals("新步骤一", captor.getAllValues().get(0).getTitle());
        assertEquals(0, captor.getAllValues().get(0).getSortOrder());
        assertEquals("新步骤二", captor.getAllValues().get(1).getTitle());
        assertEquals(1, captor.getAllValues().get(1).getSortOrder());
        // 分组稳定重排：precondition 在 step 之前，序号连续化
        verify(testCaseNodeMapper).updateSortOrder(precondId, 0);
        verify(testCaseNodeMapper).updateSortOrder(newStepId, 1);
        verify(vectorIndexService).upsertTestCase(documentId, userId);
        // 未触及 title / priority 时不写载体（C11）
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
    }

    @Test
    void updateCaseFields_titleAndPriority_updatesCarrierAndReindexes() {
        stubCaseNode();
        List<Map<String, Object>> changes = List.of(
                Map.of("field", "title", "op", "replace", "value", "新标题"),
                Map.of("field", "priority", "op", "add", "value", "medium"));

        nodeService.updateCaseFields(projectId, userId, caseId, changes);

        ArgumentCaptor<TestCaseNode> captor = ArgumentCaptor.forClass(TestCaseNode.class);
        verify(testCaseNodeMapper).updateById(captor.capture());
        assertEquals(caseId, captor.getValue().getId());
        assertEquals("新标题", captor.getValue().getTitle());
        assertEquals("P1", captor.getValue().getPriority());
        verify(vectorIndexService).upsertTestCase(documentId, userId);
    }

    @Test
    void updateCaseFields_outOfWhitelist_ignored() {
        stubCaseNode();
        List<Map<String, Object>> changes = List.of(
                Map.of("field", "type", "op", "replace", "value", "step"),
                Map.of("field", "unknown", "op", "add", "value", "x"));

        nodeService.updateCaseFields(projectId, userId, caseId, changes);

        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
        verify(testCaseNodeMapper, never()).insert(any(TestCaseNode.class));
        verify(vectorIndexService, never()).upsertTestCase(any(), any());
    }

    @Test
    void updateCaseFields_nonCaseNode_throws11022() {
        TestCaseNode node = new TestCaseNode();
        node.setId(caseId);
        node.setDocumentId(documentId);
        node.setType("step");
        when(testCaseNodeMapper.selectById(caseId)).thenReturn(node);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> nodeService.updateCaseFields(projectId, userId, caseId,
                        List.of(Map.of("field", "title", "op", "replace", "value", "x"))));

        assertEquals(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND.code(), exception.getCode());
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
    }

    @Test
    void tagCase_appliesValidMarks() {
        stubCaseNode();
        List<Map<String, Object>> changes = List.of(
                Map.of("field", "priority", "op", "add", "value", "high"),
                Map.of("field", "type", "op", "add", "value", "step"));

        nodeService.tagCase(projectId, userId, caseId, changes);

        ArgumentCaptor<TestCaseNode> captor = ArgumentCaptor.forClass(TestCaseNode.class);
        verify(testCaseNodeMapper).updateById(captor.capture());
        assertEquals("P0", captor.getValue().getPriority());
        assertEquals("step", captor.getValue().getType());
    }

    @Test
    void tagCase_allMarksIgnored_skipsUpdate() {
        stubCaseNode();
        List<Map<String, Object>> changes = List.of(
                Map.of("field", "priority", "op", "add", "value", "urgent"),
                Map.of("field", "type", "op", "add", "value", "normal"));

        nodeService.tagCase(projectId, userId, caseId, changes);

        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
    }

    @Test
    void tagCase_nodeMissing_throws11022() {
        when(testCaseNodeMapper.selectById(caseId)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> nodeService.tagCase(projectId, userId, caseId,
                        List.of(Map.of("field", "priority", "op", "add", "value", "P0"))));

        assertEquals(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND.code(), exception.getCode());
        verify(testCaseNodeMapper, never()).updateById(any(TestCaseNode.class));
    }

    /** 助手执行守卫桩：case 节点存在且文档归属当前项目 */
    private void stubCaseNode() {
        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(documentId);
        doc.setProjectId(projectId);
        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(doc);
        TestCaseNode node = new TestCaseNode();
        node.setId(caseId);
        node.setDocumentId(documentId);
        node.setType("case");
        node.setTitle("旧标题");
        when(testCaseNodeMapper.selectById(caseId)).thenReturn(node);
    }

    private static TestCaseNode child(UUID id, String type, Integer sortOrder) {
        TestCaseNode node = new TestCaseNode();
        node.setId(id);
        node.setType(type);
        node.setSortOrder(sortOrder);
        return node;
    }
}
