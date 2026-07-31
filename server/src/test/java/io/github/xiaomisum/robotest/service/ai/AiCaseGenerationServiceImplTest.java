package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCaseGenerateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiNodeTreeDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCaseGenerationServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID ROOT_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private AiOutputValidator outputValidator;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;

    @Captor
    private ArgumentCaptor<String> businessDataCaptor;
    @Captor
    private ArgumentCaptor<Function<String, Object>> doneAssemblerCaptor;

    @InjectMocks
    private AiCaseGenerationServiceImpl service;

    private AiCaseGenerateReqDTO req() {
        AiCaseGenerateReqDTO dto = new AiCaseGenerateReqDTO();
        dto.setDocumentId(DOC_ID);
        dto.setTargetNodeId(TARGET_ID);
        dto.setRequirementText("用户可以通过邮箱登录系统");
        return dto;
    }

    private TestCaseModule document(UUID projectId, String type) {
        TestCaseModule module = new TestCaseModule();
        module.setId(DOC_ID);
        module.setProjectId(projectId);
        module.setType(type);
        module.setName("登录用例文档");
        return module;
    }

    private TestCaseNode node(UUID id, UUID parentId, String title, int sortOrder) {
        TestCaseNode node = new TestCaseNode();
        node.setId(id);
        node.setDocumentId(DOC_ID);
        node.setParentId(parentId);
        node.setType(Constants.NodeType.NORMAL);
        node.setTitle(title);
        node.setSortOrder(sortOrder);
        return node;
    }

    @Test
    void documentNotFound_throws() {
        when(testCaseModuleMapper.selectById(DOC_ID)).thenReturn(null);
        assertThrows(ServiceException.class,
                () -> service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req()));
    }

    @Test
    void documentInOtherProject_throws() {
        when(testCaseModuleMapper.selectById(DOC_ID))
                .thenReturn(document(UUID.randomUUID(), Constants.ModuleType.DOCUMENT));
        assertThrows(ServiceException.class,
                () -> service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req()));
    }

    @Test
    void moduleIsDirectory_throws() {
        when(testCaseModuleMapper.selectById(DOC_ID))
                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DIRECTORY));
        assertThrows(ServiceException.class,
                () -> service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req()));
    }

    @Test
    void targetNodeNotInDocument_throws() {
        when(testCaseModuleMapper.selectById(DOC_ID))
                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
        when(testCaseNodeMapper.listByDocumentId(DOC_ID))
                .thenReturn(List.of(node(ROOT_ID, null, "根", 0)));
        assertThrows(ServiceException.class,
                () -> service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req()));
    }

    @Test
    void happyPath_buildsContextAndTrimsChildrenTo50() {
        when(testCaseModuleMapper.selectById(DOC_ID))
                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
        List<TestCaseNode> nodes = new ArrayList<>();
        nodes.add(node(ROOT_ID, null, "登录模块", 0));
        nodes.add(node(TARGET_ID, ROOT_ID, "密码登录", 0));
        for (int i = 1; i <= 60; i++) {
            nodes.add(node(UUID.randomUUID(), TARGET_ID, "既有子节点" + i, i));
        }
        when(testCaseNodeMapper.listByDocumentId(DOC_ID)).thenReturn(nodes);
        when(aiGatewayService.stream(any(), eq(AiFunctionType.CASE_GENERATION), any(),
                businessDataCaptor.capture(), any(), any(), any())).thenReturn(new SseEmitter());

        service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req());

        String businessData = businessDataCaptor.getValue();
        assertTrue(businessData.contains("用户可以通过邮箱登录系统"));
        assertTrue(businessData.contains("登录模块 > 密码登录"));
        // 同级参照裁剪至前 50 条（4.7）
        assertTrue(businessData.contains("既有子节点50"));
        assertFalse(businessData.contains("既有子节点51"));
    }

    @Test
    void doneAssembler_wrapsNodesAndWarnings() {
        when(testCaseModuleMapper.selectById(DOC_ID))
                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
        when(testCaseNodeMapper.listByDocumentId(DOC_ID))
                .thenReturn(List.of(node(TARGET_ID, null, "根", 0)));
        when(aiGatewayService.stream(any(), any(), any(), any(), any(), any(),
                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());

        AiNodeTreeDTO caseNode = new AiNodeTreeDTO();
        caseNode.setType(Constants.NodeType.CASE);
        caseNode.setTitle("超".repeat(250));
        caseNode.setPriority("P1");
        AiNodeTreeDTO.Payload payload = new AiNodeTreeDTO.Payload();
        payload.setNodes(List.of(caseNode));
        when(outputValidator.parseAndValidate(eq("raw"), eq(AiNodeTreeDTO.Payload.class), any()))
                .thenReturn(payload);

        service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req());
        Object doneData = doneAssemblerCaptor.getValue().apply("raw");

        assertTrue(doneData instanceof Map<?, ?>);
        Map<?, ?> map = (Map<?, ?>) doneData;
        assertEquals(payload.getNodes(), map.get("nodes"));
        // 截断规整计入 warnings，不触发校验失败
        assertEquals(1, ((List<?>) map.get("warnings")).size());
        verify(outputValidator).parseAndValidate(eq("raw"), eq(AiNodeTreeDTO.Payload.class), any());
    }
}
