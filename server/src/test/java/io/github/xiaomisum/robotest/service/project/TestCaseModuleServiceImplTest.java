package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.dto.request.TestCaseModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestCaseModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.TestCaseNode;
import io.github.xiaomisum.robotest.repository.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.TestCaseNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseModuleServiceImplTest {

    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;

    @InjectMocks
    private TestCaseModuleServiceImpl moduleService;

    private String projectId;

    @BeforeEach
    void setUp() {
        projectId = "proj-1";
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

        when(testCaseModuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(root));

        List<TestCaseModuleTreeRespDTO> result = moduleService.getModuleTree(projectId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Root", result.get(0).getName());
    }

    @Test
    void getModuleTree_empty() {
        when(testCaseModuleMapper.selectList(any(LambdaQueryWrapper.class)))
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

        when(testCaseModuleMapper.selectOne(any(LambdaQueryWrapper.class)))
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

        when(testCaseModuleMapper.selectOne(any(LambdaQueryWrapper.class)))
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
        when(testCaseModuleMapper.selectOne(any(LambdaQueryWrapper.class)))
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
        module.setId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        module.setProjectId(projectId);
        module.setParentId(null);
        module.setName("Old Name");

        when(testCaseModuleMapper.selectById("mod-1")).thenReturn(module);
        when(testCaseModuleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setName("New Name");

        TestCaseModuleTreeRespDTO result = moduleService.updateModule("mod-1", reqDTO);

        assertNotNull(result);
        assertEquals("New Name", result.getName());
        verify(testCaseModuleMapper).updateById(any(TestCaseModule.class));
    }

    @Test
    void updateModule_notFound_throws() {
        when(testCaseModuleMapper.selectById("mod-1")).thenReturn(null);

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setName("New Name");

        assertThrows(ServiceException.class,
                () -> moduleService.updateModule("mod-1", reqDTO));
    }

    @Test
    void updateModule_duplicateName_throws() {
        TestCaseModule module = new TestCaseModule();
        module.setId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        module.setProjectId(projectId);
        module.setParentId(null);
        module.setName("Old Name");

        when(testCaseModuleMapper.selectById("mod-1")).thenReturn(module);

        TestCaseModule duplicate = new TestCaseModule();
        duplicate.setId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
        when(testCaseModuleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(duplicate);

        TestCaseModuleUpdateReqDTO reqDTO = new TestCaseModuleUpdateReqDTO();
        reqDTO.setName("Duplicate");

        assertThrows(ServiceException.class,
                () -> moduleService.updateModule("mod-1", reqDTO));
    }

    @Test
    void deleteModule_notFound_throws() {
        when(testCaseModuleMapper.selectById("mod-1")).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> moduleService.deleteModule("mod-1"));
    }

    @Test
    void deleteModule_directoryWithChildren_throws() {
        TestCaseModule module = new TestCaseModule();
        module.setId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        module.setType("directory");

        when(testCaseModuleMapper.selectById("mod-1")).thenReturn(module);
        when(testCaseModuleMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(2L);

        assertThrows(ServiceException.class,
                () -> moduleService.deleteModule("mod-1"));
    }

    @Test
    void deleteModule_directoryEmpty_success() {
        TestCaseModule module = new TestCaseModule();
        module.setId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        module.setType("directory");

        when(testCaseModuleMapper.selectById("mod-1")).thenReturn(module);
        when(testCaseModuleMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);

        moduleService.deleteModule("mod-1");

        verify(testCaseModuleMapper).deleteById("mod-1");
    }

    @Test
    void deleteModule_document_deletesNodes() {
        TestCaseModule module = new TestCaseModule();
        module.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        module.setType("document");

        when(testCaseModuleMapper.selectById("doc-1")).thenReturn(module);

        moduleService.deleteModule("doc-1");

        verify(testCaseNodeMapper).delete(any(LambdaQueryWrapper.class));
        verify(testCaseModuleMapper).deleteById("doc-1");
    }
}
