package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseModuleServiceImplTest {

    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;

    @InjectMocks
    private TestCaseModuleServiceImpl moduleService;

    private UUID projectId;
    private UUID moduleId1;
    private UUID moduleId2;
    private UUID documentId1;

    @BeforeEach
    void setUp() {
        projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        moduleId1 = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        moduleId2 = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        documentId1 = UUID.fromString("00000000-0000-0000-0000-000000000005");
    }

    @Test
    void getModuleTree_withModules() {
        TestCaseModule root = new TestCaseModule();
        root.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        root.setProjectId(projectId);
        root.setParentId(null);
        root.setType("directory");
        root.setName("Root");
        root.setSortOrder(0);

        when(testCaseModuleMapper.listByProjectId(projectId))
                .thenReturn(List.of(root));

        List<TestCaseModuleTreeRespDTO> result = moduleService.getModuleTree(projectId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Root", result.get(0).getName());
    }

    @Test
    void getModuleTree_empty() {
        when(testCaseModuleMapper.listByProjectId(projectId))
                .thenReturn(Collections.emptyList());

        List<TestCaseModuleTreeRespDTO> result = moduleService.getModuleTree(projectId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void createModule_directory() {
        TestCaseModuleCreateReqDTO reqDTO = new TestCaseModuleCreateReqDTO();
        reqDTO.setType("directory");
        reqDTO.setName("New Dir");

        when(testCaseModuleMapper.findByNameAndParent(projectId, null, "New Dir"))
                .thenReturn(null);
        doAnswer(inv -> {
            ((TestCaseModule) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(testCaseModuleMapper).insert(any(TestCaseModule.class));

        TestCaseModuleTreeRespDTO result = moduleService.createModule(projectId, reqDTO);

        assertNotNull(result);
        assertEquals("New Dir", result.getName());
        verify(testCaseModuleMapper).insert(any(TestCaseModule.class));
        verify(testCaseNodeMapper, never()).insert(any(TestCaseNode.class));
    }

    @Test
    void createModule_document_createsRootNode() {
        TestCaseModuleCreateReqDTO reqDTO = new TestCaseModuleCreateReqDTO();
        reqDTO.setType("document");
        reqDTO.setName("New Doc");

        when(testCaseModuleMapper.findByNameAndParent(projectId, null, "New Doc"))
                .thenReturn(null);
        doAnswer(inv -> {
            ((TestCaseModule) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(testCaseModuleMapper).insert(any(TestCaseModule.class));
        doAnswer(inv -> {
            ((TestCaseNode) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(testCaseNodeMapper).insert(any(TestCaseNode.class));

        TestCaseModuleTreeRespDTO result = moduleService.createModule(projectId, reqDTO);

        assertNotNull(result);
        verify(testCaseModuleMapper).insert(any(TestCaseModule.class));
        verify(testCaseNodeMapper).insert(any(TestCaseNode.class));
    }

    @Test
    void createModule_invalidType_throws() {
        TestCaseModuleCreateReqDTO reqDTO = new TestCaseModuleCreateReqDTO();
        reqDTO.setType("invalid");
        reqDTO.setName("Bad");

        assertThrows(ServiceException.class,
                () -> moduleService.createModule(projectId, reqDTO));
    }

    @Test
    void createModule_duplicateName_throws() {
        TestCaseModuleCreateReqDTO reqDTO = new TestCaseModuleCreateReqDTO();
        reqDTO.setType("directory");
        reqDTO.setName("Existing");

        TestCaseModule existing = new TestCaseModule();
        existing.setId(UUID.fromString("00000000-0000-0000-0000-00000000000c"));
        when(testCaseModuleMapper.findByNameAndParent(projectId, null, "Existing"))
                .thenReturn(existing);

        assertThrows(ServiceException.class,
                () -> moduleService.createModule(projectId, reqDTO));
    }

    @Test
    void createModule_parentNotFound_throws() {
        TestCaseModuleCreateReqDTO reqDTO = new TestCaseModuleCreateReqDTO();
        reqDTO.setType("directory");
        reqDTO.setName("Child");
        reqDTO.setParentId(UUID.fromString("00000000-0000-0000-0000-000000000099"));

        when(testCaseModuleMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000099"))).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> moduleService.createModule(projectId, reqDTO));
    }

    @Test
    void updateModule_success() {
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId1);
        module.setProjectId(projectId);
        module.setParentId(null);
        module.setName("Old Name");

        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(module);
        when(testCaseModuleMapper.findByNameAndParentExcludingId(projectId, null, "New Name", moduleId1))
                .thenReturn(null);

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setName("New Name");

        TestCaseModuleTreeRespDTO result = moduleService.updateModule(moduleId1, reqDTO);

        assertNotNull(result);
        assertEquals("New Name", result.getName());
        // 部分更新改走 Mapper default 方法，仅落库本次变更字段
        verify(testCaseModuleMapper, never()).updateById(any(TestCaseModule.class));
        verify(testCaseModuleMapper).updateName(moduleId1, "New Name");
    }

    @Test
    void updateModule_notFound_throws() {
        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(null);

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setName("New Name");

        assertThrows(ServiceException.class,
                () -> moduleService.updateModule(moduleId1, reqDTO));
    }

    @Test
    void updateModule_duplicateName_throws() {
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId1);
        module.setProjectId(projectId);
        module.setParentId(null);
        module.setName("Old Name");

        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(module);

        TestCaseModule duplicate = new TestCaseModule();
        duplicate.setId(moduleId2);
        when(testCaseModuleMapper.findByNameAndParentExcludingId(projectId, null, "Duplicate", moduleId1))
                .thenReturn(duplicate);

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setName("Duplicate");

        assertThrows(ServiceException.class,
                () -> moduleService.updateModule(moduleId1, reqDTO));
    }

    @Test
    void updateModule_move_intoDirectory_resequencesSiblings() {
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId1);
        module.setProjectId(projectId);
        module.setParentId(null);
        module.setType("document");
        module.setName("Doc");
        module.setSortOrder(0);

        TestCaseModule targetDir = new TestCaseModule();
        targetDir.setId(moduleId2);
        targetDir.setProjectId(projectId);
        targetDir.setParentId(null);
        targetDir.setType("directory");
        targetDir.setName("Dir");

        TestCaseModule existingChild = new TestCaseModule();
        existingChild.setId(documentId1);
        existingChild.setProjectId(projectId);
        existingChild.setParentId(moduleId2);
        existingChild.setType("document");
        existingChild.setName("Other");
        existingChild.setSortOrder(0);

        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(module);
        when(testCaseModuleMapper.selectById(moduleId2)).thenReturn(targetDir);
        when(testCaseModuleMapper.findByNameExcludingId(projectId, moduleId2, "Doc", moduleId1)).thenReturn(null);
        when(testCaseModuleMapper.findSiblingsByParent(projectId, moduleId2, moduleId1))
                .thenReturn(new ArrayList<>(List.of(existingChild)));

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setParentId(moduleId2);
        reqDTO.setTargetIndex(0);

        moduleService.updateModule(moduleId1, reqDTO);

        assertEquals(moduleId2, module.getParentId());
        assertEquals(0, module.getSortOrder());
        assertEquals(1, existingChild.getSortOrder());
        // 被挤开的兄弟节点回写新序号，被移动节点回写父级与序号
        verify(testCaseModuleMapper).updateSortOrder(documentId1, 1);
        verify(testCaseModuleMapper).updateParentAndOrder(moduleId1, moduleId2, 0);
    }

    @Test
    void updateModule_move_toRoot() {
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId1);
        module.setProjectId(projectId);
        module.setParentId(moduleId2);
        module.setType("document");
        module.setName("Doc");
        module.setSortOrder(0);

        TestCaseModule rootSibling = new TestCaseModule();
        rootSibling.setId(documentId1);
        rootSibling.setProjectId(projectId);
        rootSibling.setParentId(null);
        rootSibling.setType("directory");
        rootSibling.setName("Root Dir");
        rootSibling.setSortOrder(0);

        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(module);
        when(testCaseModuleMapper.findByNameExcludingId(projectId, null, "Doc", moduleId1)).thenReturn(null);
        when(testCaseModuleMapper.findSiblingsByParent(projectId, null, moduleId1))
                .thenReturn(new ArrayList<>(List.of(rootSibling)));

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setParentId(null);
        reqDTO.setTargetIndex(1);

        moduleService.updateModule(moduleId1, reqDTO);

        assertNull(module.getParentId());
        assertEquals(1, module.getSortOrder());
        assertEquals(0, rootSibling.getSortOrder());
    }

    @Test
    void updateModule_move_intoOwnDescendant_throws() {
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId1);
        module.setProjectId(projectId);
        module.setParentId(null);
        module.setType("directory");
        module.setName("Parent Dir");

        TestCaseModule childDir = new TestCaseModule();
        childDir.setId(moduleId2);
        childDir.setProjectId(projectId);
        childDir.setParentId(moduleId1);
        childDir.setType("directory");
        childDir.setName("Child Dir");

        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(module);
        when(testCaseModuleMapper.selectById(moduleId2)).thenReturn(childDir);

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setParentId(moduleId2);
        reqDTO.setTargetIndex(0);

        assertThrows(ServiceException.class,
                () -> moduleService.updateModule(moduleId1, reqDTO));
    }

    @Test
    void updateModule_move_targetIsDocument_throws() {
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId1);
        module.setProjectId(projectId);
        module.setType("document");
        module.setName("Doc");

        TestCaseModule targetDoc = new TestCaseModule();
        targetDoc.setId(moduleId2);
        targetDoc.setProjectId(projectId);
        targetDoc.setType("document");
        targetDoc.setName("Target Doc");

        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(module);
        when(testCaseModuleMapper.selectById(moduleId2)).thenReturn(targetDoc);

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setParentId(moduleId2);
        reqDTO.setTargetIndex(0);

        assertThrows(ServiceException.class,
                () -> moduleService.updateModule(moduleId1, reqDTO));
    }

    @Test
    void updateModule_move_targetParentNotFound_throws() {
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId1);
        module.setProjectId(projectId);
        module.setType("document");
        module.setName("Doc");

        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(module);
        when(testCaseModuleMapper.selectById(moduleId2)).thenReturn(null);

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setParentId(moduleId2);
        reqDTO.setTargetIndex(0);

        assertThrows(ServiceException.class,
                () -> moduleService.updateModule(moduleId1, reqDTO));
    }

    @Test
    void updateModule_move_duplicateNameInTarget_throws() {
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId1);
        module.setProjectId(projectId);
        module.setParentId(null);
        module.setType("document");
        module.setName("Doc");

        TestCaseModule targetDir = new TestCaseModule();
        targetDir.setId(moduleId2);
        targetDir.setProjectId(projectId);
        targetDir.setParentId(null);
        targetDir.setType("directory");
        targetDir.setName("Dir");

        TestCaseModule duplicate = new TestCaseModule();
        duplicate.setId(documentId1);

        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(module);
        when(testCaseModuleMapper.selectById(moduleId2)).thenReturn(targetDir);
        when(testCaseModuleMapper.findByNameExcludingId(projectId, moduleId2, "Doc", moduleId1)).thenReturn(duplicate);

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setParentId(moduleId2);
        reqDTO.setTargetIndex(0);

        assertThrows(ServiceException.class,
                () -> moduleService.updateModule(moduleId1, reqDTO));
    }

    @Test
    void deleteModule_notFound_throws() {
        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> moduleService.deleteModule(moduleId1));
    }

    @Test
    void deleteModule_directoryWithChildren_throws() {
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId1);
        module.setType("directory");

        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(module);
        when(testCaseModuleMapper.countByParentId(moduleId1)).thenReturn(2L);

        assertThrows(ServiceException.class,
                () -> moduleService.deleteModule(moduleId1));
    }

    @Test
    void deleteModule_directoryEmpty_success() {
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId1);
        module.setType("directory");

        when(testCaseModuleMapper.selectById(moduleId1)).thenReturn(module);
        when(testCaseModuleMapper.countByParentId(moduleId1)).thenReturn(0L);

        moduleService.deleteModule(moduleId1);

        verify(testCaseModuleMapper).deleteById(moduleId1);
    }

    @Test
    void deleteModule_document_deletesNodes() {
        TestCaseModule module = new TestCaseModule();
        module.setId(documentId1);
        module.setType("document");

        when(testCaseModuleMapper.selectById(documentId1)).thenReturn(module);

        moduleService.deleteModule(documentId1);

        verify(testCaseNodeMapper).deleteByDocumentId(documentId1);
        verify(testCaseModuleMapper).deleteById(documentId1);
    }
}
