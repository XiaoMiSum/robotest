package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseNodeUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseCaseListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocumentLayout;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentLayoutMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseNodeServiceImplTest {

    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestCaseDocumentLayoutMapper testCaseDocumentLayoutMapper;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private TestCaseNodeServiceImpl nodeService;

    private UUID documentId;
    private UUID caseId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        documentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        caseId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        userId = UUID.fromString("00000000-0000-0000-0000-000000000005");
    }

    @Test
    void getDocumentNodes_success() {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        TestCaseModule doc = new TestCaseModule();
        doc.setId(documentId);
        doc.setProjectId(projectId);
        doc.setType("document");

        when(testCaseModuleMapper.selectById(documentId)).thenReturn(doc);

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

        TestCaseDocumentLayout layout = new TestCaseDocumentLayout();
        layout.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        layout.setDocumentId(documentId);
        LinkedHashMap<String, Object> layoutMap = new LinkedHashMap<>();
        layoutMap.put("x", 0);
        layoutMap.put("y", 0);
        layout.setLayoutJson(layoutMap);
        when(testCaseDocumentLayoutMapper.findByDocumentId(documentId))
                .thenReturn(layout);

        TestCaseDocumentNodesRespDTO result = nodeService.getDocumentNodes(documentId, userId);

        assertNotNull(result);
        assertNotNull(result.getNode());
        assertEquals("Root", result.getNode().getTitle());
        assertEquals(layoutMap, result.getLayout());
        verify(projectAccessGuard).requireProjectMember(projectId, userId);
    }

    @Test
    void getDocumentNodes_noLayout() {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        TestCaseModule doc = new TestCaseModule();
        doc.setId(documentId);
        doc.setProjectId(projectId);
        doc.setType("document");

        when(testCaseModuleMapper.selectById(documentId)).thenReturn(doc);
        when(testCaseNodeMapper.listByDocumentId(documentId))
                .thenReturn(Collections.emptyList());
        when(testCaseDocumentLayoutMapper.findByDocumentId(documentId))
                .thenReturn(null);

        TestCaseDocumentNodesRespDTO result = nodeService.getDocumentNodes(documentId, userId);

        assertNotNull(result);
        assertNull(result.getLayout());
        verify(projectAccessGuard).requireProjectMember(projectId, userId);
    }

    @Test
    void getDocumentNodes_notFound_throws() {
        when(testCaseModuleMapper.selectById(documentId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> nodeService.getDocumentNodes(documentId, userId));
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    @Test
    void getDocumentNodes_notDocumentType_throws() {
        TestCaseModule module = new TestCaseModule();
        module.setId(documentId);
        module.setType("directory");

        when(testCaseModuleMapper.selectById(documentId)).thenReturn(module);

        assertThrows(ServiceException.class,
                () -> nodeService.getDocumentNodes(documentId, userId));
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    @Test
    void getCaseDetail_success() {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        TestCaseModule doc = new TestCaseModule();
        doc.setId(documentId);
        doc.setProjectId(projectId);
        doc.setType("document");
        when(testCaseModuleMapper.selectById(documentId)).thenReturn(doc);

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

        TestCaseNodeTreeRespDTO result = nodeService.getCaseDetail(caseId, userId);

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
                () -> nodeService.getCaseDetail(caseId, userId));
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    // ========== getCaseList ==========

    @Test
    void getCaseList_success() {
        UUID projId = UUID.fromString("00000000-0000-0000-0000-000000000008");

        TestCaseModule doc = new TestCaseModule();
        doc.setId(documentId);
        doc.setName("Doc 1");
        when(testCaseModuleMapper.findDocumentModulesByProjectId(projId))
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
        when(testCaseModuleMapper.findDocumentModulesByProjectId(projId))
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
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        TestCaseModule doc = new TestCaseModule();
        doc.setId(documentId);
        doc.setProjectId(projectId);
        doc.setType("document");
        when(testCaseModuleMapper.selectById(documentId)).thenReturn(doc);

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

        nodeService.updateCaseNode(caseId, userId, reqDTO);

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
                () -> nodeService.updateCaseNode(caseId, userId, reqDTO));
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
                () -> nodeService.updateCaseNode(caseId, userId, reqDTO));
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }
}
