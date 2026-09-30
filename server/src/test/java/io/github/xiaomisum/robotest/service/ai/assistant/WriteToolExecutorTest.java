package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.ProjectModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.domain.bug.BugService;
import io.github.xiaomisum.robotest.service.domain.plan.TestPlanService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.ProjectModuleService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseDocumentService;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanDetailRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WriteToolExecutorTest {

    @Mock
    private BugService bugService;
    @Mock
    private TestPlanService testPlanService;
    @Mock
    private TestCaseDocumentService testCaseDocumentService;
    @Mock
    private ProjectModuleService projectModuleService;
    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;

    @InjectMocks
    private WriteToolExecutor executor;

    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final AiToolContext context =
            new AiToolContext(userId, workspaceId, null);

    // ======================== create_bug ========================

    @Test
    void createBug_success_returnsResultWithRoutePath() {
        UUID bugId = UUID.randomUUID();
        when(bugService.createBug(eq(projectId), eq(userId), any(BugCreateReqDTO.class)))
                .thenReturn(bugId.toString());

        String result = executor.execute(context, "create_bug", Map.of(
                "projectId", projectId.toString(),
                "title", "登录超时",
                "severity", "fatal",
                "priority", "high"));

        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(bugId.toString(), parsed.get("id"));
        assertEquals("登录超时", parsed.get("title"));
        assertEquals("/workspace/projects/bugs/" + bugId, parsed.get("routePath"));
    }

    @Test
    void createBug_missingProjectId_returnsError() {
        String result = executor.execute(context, "create_bug", Map.of(
                "title", "测试缺陷"));

        assertTrue(result.contains("projectId 必填"));
    }

    // ======================== create_plan_draft ========================

    @Test
    void createPlanDraft_success_returnsResultWithRoutePath() {
        UUID planId = UUID.randomUUID();
        TestPlanDetailRespDTO resp = new TestPlanDetailRespDTO();
        resp.setId(planId);
        resp.setName("回归计划");
        when(testPlanService.createPlan(eq(projectId), eq(userId), any(TestPlanCreateReqDTO.class)))
                .thenReturn(resp);

        String result = executor.execute(context, "create_plan_draft", Map.of(
                "projectId", projectId.toString(),
                "name", "回归计划"));

        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(planId.toString(), parsed.get("id"));
        assertEquals("回归计划", parsed.get("name"));
        assertEquals("new", parsed.get("status"));
    }

    // ======================== create_document ========================

    @Test
    void createDocument_success_withoutCaseNodes() {
        UUID docId = UUID.randomUUID();
        TestCaseDocumentRespDTO resp = new TestCaseDocumentRespDTO();
        resp.setId(docId);
        resp.setName("测试文档");
        resp.setProjectId(projectId);
        when(testCaseDocumentService.createTestCase(eq(projectId), eq(userId), any(TestCaseDocumentCreateReqDTO.class)))
                .thenReturn(resp);

        String result = executor.execute(context, "create_document", Map.of(
                "projectId", projectId.toString(),
                "documentName", "测试文档"));

        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(docId.toString(), parsed.get("documentId"));
        assertEquals("测试文档", parsed.get("documentName"));
        assertEquals(0, parsed.get("createdNodes"));
    }

    @Test
    void createDocument_success_withCaseNodes() {
        UUID docId = UUID.randomUUID();
        UUID rootNodeId = UUID.randomUUID();
        TestCaseDocumentRespDTO resp = new TestCaseDocumentRespDTO();
        resp.setId(docId);
        resp.setName("测试文档");
        resp.setProjectId(projectId);

        TestCaseNode rootNode = new TestCaseNode();
        rootNode.setId(rootNodeId);
        rootNode.setParentId(null);

        when(testCaseDocumentService.createTestCase(eq(projectId), eq(userId), any(TestCaseDocumentCreateReqDTO.class)))
                .thenReturn(resp);
        when(testCaseNodeMapper.listByDocumentId(docId)).thenReturn(List.of(rootNode));

        String result = executor.execute(context, "create_document", Map.of(
                "projectId", projectId.toString(),
                "documentName", "测试文档",
                "caseNodes", List.of(
                        Map.of("title", "测试用例1", "priority", "P0"),
                        Map.of("title", "测试用例2", "priority", "P1"))));

        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(docId.toString(), parsed.get("documentId"));
        assertEquals(2, parsed.get("createdNodes"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestCaseNode>> captor = ArgumentCaptor.forClass(List.class);
        verify(testCaseNodeMapper).insertBatch(captor.capture());
        List<TestCaseNode> inserted = captor.getValue();
        assertEquals(2, inserted.size());
        assertEquals("测试用例1", inserted.get(0).getTitle());
        assertEquals("P0", inserted.get(0).getPriority());
        assertEquals(Constants.NodeType.CASE, inserted.get(0).getType());
        assertTrue(inserted.get(0).getAiGenerated());
        assertEquals(rootNodeId, inserted.get(0).getParentId());
        assertEquals(1, inserted.get(0).getVersion());
    }

    @Test
    void createDocument_missingDocumentName_returnsError() {
        String result = executor.execute(context, "create_document", Map.of(
                "projectId", projectId.toString()));

        assertTrue(result.contains("documentName 必填"));
    }

    @Test
    void createDocument_moduleNameNotFound_returnsError() {
        when(projectModuleMapper.listByProjectId(projectId)).thenReturn(List.of());

        String result = executor.execute(context, "create_document", Map.of(
                "projectId", projectId.toString(),
                "documentName", "测试文档",
                "moduleName", "不存在的模块"));

        assertTrue(result.contains("未找到"));
        assertTrue(result.contains("不存在的模块"));
    }

    @Test
    void createDocument_withModuleName_matchesModule() {
        UUID moduleId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        ProjectModule module = new ProjectModule();
        module.setId(moduleId);
        module.setName("用户管理");
        module.setProjectId(projectId);

        TestCaseDocumentRespDTO resp = new TestCaseDocumentRespDTO();
        resp.setId(docId);
        resp.setName("测试文档");
        resp.setModuleId(moduleId);
        resp.setProjectId(projectId);

        when(projectModuleMapper.listByProjectId(projectId)).thenReturn(List.of(module));
        when(testCaseDocumentService.createTestCase(eq(projectId), eq(userId), any(TestCaseDocumentCreateReqDTO.class)))
                .thenReturn(resp);

        String result = executor.execute(context, "create_document", Map.of(
                "projectId", projectId.toString(),
                "documentName", "测试文档",
                "moduleName", "用户管理"));

        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(docId.toString(), parsed.get("documentId"));

        ArgumentCaptor<TestCaseDocumentCreateReqDTO> captor =
                ArgumentCaptor.forClass(TestCaseDocumentCreateReqDTO.class);
        verify(testCaseDocumentService).createTestCase(eq(projectId), eq(userId), captor.capture());
        assertEquals(moduleId, captor.getValue().getModuleId());
    }

    // ======================== create_module ========================

    @Test
    void createModule_success_topLevel() {
        UUID moduleId = UUID.randomUUID();
        ProjectModuleTreeRespDTO resp = new ProjectModuleTreeRespDTO();
        resp.setId(moduleId);
        resp.setName("新模块");
        when(projectModuleService.createModule(eq(projectId), eq(userId), any(ProjectModuleCreateReqDTO.class)))
                .thenReturn(resp);

        String result = executor.execute(context, "create_module", Map.of(
                "projectId", projectId.toString(),
                "moduleName", "新模块"));

        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(moduleId.toString(), parsed.get("moduleId"));
        assertEquals("新模块", parsed.get("moduleName"));
    }

    @Test
    void createModule_success_withParentModule() {
        UUID parentModuleId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        ProjectModule parent = new ProjectModule();
        parent.setId(parentModuleId);
        parent.setName("父模块");
        parent.setProjectId(projectId);

        ProjectModuleTreeRespDTO resp = new ProjectModuleTreeRespDTO();
        resp.setId(moduleId);
        resp.setName("子模块");

        when(projectModuleMapper.listByProjectId(projectId)).thenReturn(List.of(parent));
        when(projectModuleService.createModule(eq(projectId), eq(userId), any(ProjectModuleCreateReqDTO.class)))
                .thenReturn(resp);

        String result = executor.execute(context, "create_module", Map.of(
                "projectId", projectId.toString(),
                "moduleName", "子模块",
                "parentModuleName", "父模块"));

        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(moduleId.toString(), parsed.get("moduleId"));

        ArgumentCaptor<ProjectModuleCreateReqDTO> captor =
                ArgumentCaptor.forClass(ProjectModuleCreateReqDTO.class);
        verify(projectModuleService).createModule(eq(projectId), eq(userId), captor.capture());
        assertEquals(parentModuleId, captor.getValue().getParentId());
    }

    @Test
    void createModule_parentModuleNotFound_returnsError() {
        when(projectModuleMapper.listByProjectId(projectId)).thenReturn(List.of());

        String result = executor.execute(context, "create_module", Map.of(
                "projectId", projectId.toString(),
                "moduleName", "新模块",
                "parentModuleName", "不存在的父模块"));

        assertTrue(result.contains("未找到"));
        assertTrue(result.contains("不存在的父模块"));
    }

    @Test
    void createModule_missingModuleName_returnsError() {
        String result = executor.execute(context, "create_module", Map.of(
                "projectId", projectId.toString()));

        assertTrue(result.contains("moduleName 必填"));
    }

    // ======================== unknown tool ========================

    @Test
    void execute_unknownTool_returnsError() {
        String result = executor.execute(context, "nonexistent_tool", Map.of());
        assertTrue(result.contains("未知写工具"));
        assertTrue(result.contains("nonexistent_tool"));
    }

    // ======================== projectId from context fallback ========================

    @Test
    void createBug_projectIdFromPageContext() {
        UUID bugId = UUID.randomUUID();
        AiToolContext ctxWithProject = new AiToolContext(userId, workspaceId,
                Map.of("projectId", projectId.toString()));
        when(bugService.createBug(eq(projectId), eq(userId), any(BugCreateReqDTO.class)))
                .thenReturn(bugId.toString());

        String result = executor.execute(ctxWithProject, "create_bug", Map.of(
                "title", "上下文缺陷",
                "severity", "serious",
                "priority", "medium"));

        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(bugId.toString(), parsed.get("id"));
        assertFalse(parsed.containsKey("error"));
    }
}
