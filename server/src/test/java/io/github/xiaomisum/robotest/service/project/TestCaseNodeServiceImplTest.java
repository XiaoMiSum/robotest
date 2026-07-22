package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.TestCaseDocumentLayout;
import io.github.xiaomisum.robotest.model.entity.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.TestCaseNode;
import io.github.xiaomisum.robotest.repository.TestCaseDocumentLayoutMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCaseNodeServiceImplTest {

    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestCaseDocumentLayoutMapper testCaseDocumentLayoutMapper;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;

    @InjectMocks
    private TestCaseNodeServiceImpl nodeService;

    private String documentId;
    private String caseId;

    @BeforeEach
    void setUp() {
        documentId = "00000000-0000-0000-0000-000000000001";
        caseId = "00000000-0000-0000-0000-000000000002";
    }

    @Test
    void getDocumentNodes_success() {
        TestCaseModule doc = new TestCaseModule();
        doc.setId(UUID.fromString(documentId));
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

        when(testCaseNodeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(root));

        TestCaseDocumentLayout layout = new TestCaseDocumentLayout();
        layout.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        layout.setDocumentId(documentId);
        LinkedHashMap<String, Object> layoutMap = new LinkedHashMap<>();
        layoutMap.put("x", 0);
        layoutMap.put("y", 0);
        layout.setLayoutJson(layoutMap);
        when(testCaseDocumentLayoutMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(layout);

        TestCaseDocumentNodesRespDTO result = nodeService.getDocumentNodes(documentId);

        assertNotNull(result);
        assertNotNull(result.getNode());
        assertEquals("Root", result.getNode().getTitle());
        assertEquals("{x=0, y=0}", result.getLayout());
    }

    @Test
    void getDocumentNodes_noLayout() {
        TestCaseModule doc = new TestCaseModule();
        doc.setId(UUID.fromString(documentId));
        doc.setType("document");

        when(testCaseModuleMapper.selectById(documentId)).thenReturn(doc);
        when(testCaseNodeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(testCaseDocumentLayoutMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);

        TestCaseDocumentNodesRespDTO result = nodeService.getDocumentNodes(documentId);

        assertNotNull(result);
        assertNull(result.getLayout());
    }

    @Test
    void getDocumentNodes_notFound_throws() {
        when(testCaseModuleMapper.selectById(documentId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> nodeService.getDocumentNodes(documentId));
    }

    @Test
    void getDocumentNodes_notDocumentType_throws() {
        TestCaseModule module = new TestCaseModule();
        module.setId(UUID.fromString(documentId));
        module.setType("directory");

        when(testCaseModuleMapper.selectById(documentId)).thenReturn(module);

        assertThrows(ServiceException.class,
                () -> nodeService.getDocumentNodes(documentId));
    }

    @Test
    void getCaseDetail_success() {
        TestCaseNode node = new TestCaseNode();
        node.setId(UUID.fromString(caseId));
        node.setDocumentId(documentId);
        node.setType("case");
        node.setTitle("Test Case");
        node.setPriority("high");
        node.setSortOrder(0);
        node.setVersion(1);

        when(testCaseNodeMapper.selectById(caseId)).thenReturn(node);

        TestCaseNodeTreeRespDTO result = nodeService.getCaseDetail(caseId);

        assertNotNull(result);
        assertEquals("Test Case", result.getTitle());
        assertEquals("high", result.getPriority());
        assertEquals("case", result.getType());
    }

    @Test
    void getCaseDetail_notFound_throws() {
        when(testCaseNodeMapper.selectById(caseId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> nodeService.getCaseDetail(caseId));
    }
}
