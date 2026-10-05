package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.vector.AiEmbeddingGate;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 生成链六阶段处理器单测（C8）：提交校验三类错误码与快照回写、四阶段模型调用装配、
 * 来源过滤与查重标注。
 */
@ExtendWith(MockitoExtension.class)
class TestDesignGenerationHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();
    private static final UUID TARGET_MODULE_ID = UUID.randomUUID();

    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private AiTaskMapper aiTaskMapper;
    @Mock
    private AiEmbeddingGate embeddingGate;
    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private AiPromptService promptService;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private TestDesignGenerationHandler handler;

    // ---------- SPI 契约 ----------

    @Test
    void type_isTestDesignGeneration() {
        assertEquals("test_design_generation", handler.type());
        assertTrue(handler.defaultPrompt().contains("六阶段"));
    }

    @Test
    void checkPermission_withoutViewAuthority_throwsNoPermission() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("case:view"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.checkPermission(user));
        assertEquals(ErrorCodeConstants.REQUIREMENT_NO_PERMISSION.code(), exception.getCode());
    }

    // ---------- 输入校验（类型 / 参数） ----------

    @Test
    void validateInput_missingRequirementIds_throws205() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of()));
        assertEquals(ErrorCodeConstants.GENERATION_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_badGranularity_throws205() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("requirementIds", List.of(REQUIREMENT_ID.toString()),
                        "granularity", "huge")));
        assertEquals(ErrorCodeConstants.GENERATION_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_attachWithoutModule_throws205() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("requirementIds", List.of(REQUIREMENT_ID.toString()),
                        "placement", "attach")));
        assertEquals(ErrorCodeConstants.GENERATION_INPUT_INVALID.code(), exception.getCode());
    }

    // ---------- 输入校验（项目范围 / 快照回写） ----------

    @Test
    void validateInput_withoutProjectContext_throws205() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(baseInput(), new TaskSubmitContext(null, null, OPERATOR_ID, null)));
        assertEquals(ErrorCodeConstants.GENERATION_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_requirementNotFound_throws201() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(baseInput(), submitContext()));
        assertEquals(ErrorCodeConstants.GENERATION_REQUIREMENT_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void validateInput_requirementNotConfirmed_throws202() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement("draft")));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(baseInput(), submitContext()));
        assertEquals(ErrorCodeConstants.GENERATION_REQUIREMENT_NOT_CONFIRMED.code(), exception.getCode());
    }

    @Test
    void validateInput_targetModuleInvalid_throws203() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement("confirmed")));
        when(projectModuleMapper.selectById(TARGET_MODULE_ID)).thenReturn(null);

        Map<String, Object> input = baseInput();
        input.put("targetModuleId", TARGET_MODULE_ID.toString());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(input, submitContext()));
        assertEquals(ErrorCodeConstants.GENERATION_TARGET_MODULE_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_twinRunningTask_throws209() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement("confirmed")));
        AiTask running = new AiTask();
        running.setInput(Map.of("requirementIds", List.of(REQUIREMENT_ID.toString())));
        when(aiTaskMapper.listInProgressByType(any(), anyString())).thenReturn(List.of(running));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(baseInput(), submitContext()));
        assertEquals(ErrorCodeConstants.GENERATION_DUPLICATE_TASK.code(), exception.getCode());
    }

    @Test
    void validateInput_vectorNotReady_throws119() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement("confirmed")));
        when(aiTaskMapper.listInProgressByType(any(), anyString())).thenReturn(List.of());
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED))
                .when(embeddingGate).requireReady();

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(baseInput(), submitContext()));
        assertEquals(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED.code(), exception.getCode());
    }

    @Test
    void validateInput_writesSnapshotAndDefaults() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement("confirmed")));
        when(aiTaskMapper.listInProgressByType(any(), anyString())).thenReturn(List.of());
        when(embeddingGate.requireReady()).thenReturn(null);

        Map<String, Object> input = baseInput();
        handler.validateInput(input, submitContext());

        assertEquals("new_top_level", input.get("placement"));
        assertEquals("standard", input.get("granularity"));
        assertTrue(input.get("requirementSnapshots") instanceof List<?>);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snapshots = (List<Map<String, Object>>) input.get("requirementSnapshots");
        assertEquals(1, snapshots.size());
        assertEquals(REQUIREMENT_ID.toString(), snapshots.get(0).get("id"));
        assertEquals("confirmed", snapshots.get(0).get("status"));
    }

    // ---------- 执行：四阶段装配 ----------

    @Test
    @SuppressWarnings("unchecked")
    void execute_buildsThreeTierArtifacts() {
        stubExecutionBase();
        when(testCaseDocumentMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(document()));
        when(testCaseNodeMapper.listCasesByDocumentIds(any())).thenReturn(List.of(
                caseNode("正确密码登录")));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module("登录")));

        when(context.chat(anyString(), anyString()))
                .thenReturn(new AiChatReply(modulesJson(), 10, 5))
                .thenReturn(new AiChatReply(documentsJson(), 20, 6))
                .thenReturn(new AiChatReply(nodesJson(), 30, 7))
                .thenReturn(new AiChatReply(attributesJson(), 40, 8));

        TaskResult result = handler.execute(context);

        assertEquals(100, result.tokensIn());
        assertEquals(26, result.tokensOut());
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        // 来源越界的节点被过滤，其余按 模块 → 文档 → 节点 三层扁平展开
        assertEquals(4, artifacts.size());

        Map<String, Object> module = artifacts.get(0);
        assertEquals("module-1", module.get("key"));
        assertEquals("module_suggestion", module.get("kind"));
        // 同名既有模块 → 疑似重复标注
        assertNotNull(moduleContent(module).get("suspectedDuplicateOf"));

        Map<String, Object> document = artifacts.get(1);
        assertEquals("doc-1", document.get("key"));
        assertEquals("module-1", document.get("parentKey"));

        Map<String, Object> group = artifacts.get(2);
        assertEquals("node-1", group.get("key"));
        assertEquals("doc-1", group.get("parentKey"));
        assertNull(moduleContent(group).get("parentRef"));
        assertEquals(false, moduleContent(group).get("isTestCase"));

        Map<String, Object> testCase = artifacts.get(3);
        assertEquals("case-1", testCase.get("key"));
        assertEquals("node-1", moduleContent(testCase).get("parentRef"));
        assertNotNull(moduleContent(testCase).get("suspectedDuplicateOf"));
        Map<String, Object> attributes = (Map<String, Object>) moduleContent(testCase).get("attributes");
        // 步骤 2 条、预期 1 条 → 按短边截齐
        assertEquals("high", attributes.get("priority"));
        assertEquals(List.of("s1"), attributes.get("steps"));
        assertEquals(List.of("e1"), attributes.get("expected"));
    }

    @Test
    void execute_emptyModules_throws117() {
        stubExecutionBase();
        when(context.chat(anyString(), anyString()))
                .thenReturn(new AiChatReply("{\"artifacts\":[]}", 1, 1));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), exception.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_invalidModuleAndDocumentSources_droppedNodesSurvive() {
        stubExecutionBase();
        String alien = UUID.randomUUID().toString();
        when(testCaseDocumentMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(testCaseNodeMapper.listCasesByDocumentIds(any())).thenReturn(List.of());
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(context.chat(anyString(), anyString()))
                .thenReturn(new AiChatReply(withSource(alien), 1, 1))
                .thenReturn(new AiChatReply(withDocSource(alien), 1, 1))
                .thenReturn(new AiChatReply(nodesJson(), 1, 1))
                .thenReturn(new AiChatReply(attributesJson(), 1, 1));

        TaskResult result = handler.execute(context);

        // 模块 / 文档来源越界被移除，来源有效的节点产物保留（3.4「来源缺失不入待确认态」）
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(2, artifacts.size());
        assertEquals("node-1", artifacts.get(0).get("key"));
        assertEquals("case-1", artifacts.get(1).get("key"));
    }

    // ---------- 辅助 ----------

    private void stubExecutionBase() {
        when(context.getInput()).thenReturn(baseInput());
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(context.getPromptService()).thenReturn(promptService);
        when(promptService.render(anyString(), anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vectorSearchService.search(any(), any(), anyString(), anyInt(), any()))
                .thenReturn(new ArrayList<>());
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement("confirmed")));
    }

    private Map<String, Object> baseInput() {
        return new java.util.LinkedHashMap<>(Map.of(
                "requirementIds", List.of(REQUIREMENT_ID.toString())));
    }

    private TaskSubmitContext submitContext() {
        return new TaskSubmitContext(PROJECT_ID, UUID.randomUUID(), OPERATOR_ID, null);
    }

    private Requirement requirement(String status) {
        Requirement requirement = new Requirement();
        requirement.setId(REQUIREMENT_ID);
        requirement.setProjectId(PROJECT_ID);
        requirement.setCode("REQ-1");
        requirement.setTitle("用户登录");
        requirement.setDescription("支持账号密码登录");
        requirement.setStatus(status);
        return requirement;
    }

    private ProjectModule module(String name) {
        ProjectModule module = new ProjectModule();
        module.setId(UUID.randomUUID());
        module.setProjectId(PROJECT_ID);
        module.setName(name);
        return module;
    }

    private TestCaseDocument document() {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(UUID.randomUUID());
        document.setProjectId(PROJECT_ID);
        document.setName("登录用例");
        return document;
    }

    private TestCaseNode caseNode(String title) {
        TestCaseNode node = new TestCaseNode();
        node.setId(UUID.randomUUID());
        node.setTitle(title);
        return node;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> moduleContent(Map<String, Object> artifact) {
        return (Map<String, Object>) artifact.get("content");
    }

    private static String withSource(String requirementId) {
        return "{\"artifacts\":[{\"key\":\"module-1\",\"content\":{\"name\":\"登录\","
                + "\"sourceRefs\":[{\"requirementId\":\"" + requirementId + "\",\"quote\":\"原文\"}]}}]}";
    }

    private static String withDocSource(String requirementId) {
        return "{\"artifacts\":[{\"key\":\"d1\",\"parentKey\":\"module-1\",\"content\":{\"name\":\"登录用例\","
                + "\"sourceRefs\":[{\"requirementId\":\"" + requirementId + "\",\"quote\":\"原文\"}]}}]}";
    }

    private static String modulesJson() {
        return "{\"artifacts\":[{\"key\":\"module-1\",\"content\":{\"name\":\"登录\","
                + "\"description\":\"登录域\",\"sourceRefs\":[{\"requirementId\":\""
                + REQUIREMENT_ID + "\",\"quote\":\"登录\"}]}}]}";
    }

    private static String documentsJson() {
        return "{\"artifacts\":[{\"key\":\"doc-1\",\"parentKey\":\"module-1\","
                + "\"content\":{\"name\":\"登录用例\",\"sourceRefs\":[{\"requirementId\":\""
                + REQUIREMENT_ID + "\",\"quote\":\"登录\"}]}}]}";
    }

    private static String nodesJson() {
        return "{\"artifacts\":["
                + "{\"ref\":\"n1\",\"parentKey\":\"doc-1\",\"parentRef\":null,"
                + "\"content\":{\"title\":\"登录流程\",\"isTestCase\":false,"
                + "\"sourceRef\":{\"requirementId\":\"" + REQUIREMENT_ID + "\",\"quote\":\"登录\"}}},"
                + "{\"ref\":\"n2\",\"parentKey\":\"doc-1\",\"parentRef\":\"n1\","
                + "\"content\":{\"title\":\"正确密码登录\",\"isTestCase\":true,"
                + "\"sourceRef\":{\"requirementId\":\"" + REQUIREMENT_ID + "\",\"quote\":\"登录\"}}},"
                + "{\"ref\":\"n3\",\"parentKey\":\"doc-1\",\"parentRef\":\"n1\","
                + "\"content\":{\"title\":\"越界节点\",\"isTestCase\":true,"
                + "\"sourceRef\":{\"requirementId\":\"" + UUID.randomUUID() + "\",\"quote\":\"越界\"}}}]}";
    }

    private static String attributesJson() {
        return "{\"artifacts\":[{\"ref\":\"case-1\",\"content\":{\"priority\":\"high\","
                + "\"precondition\":\"已打开登录页\",\"steps\":[\"s1\",\"s2\"],"
                + "\"expected\":[\"e1\"],\"tags\":[\"smoke\",\"p0\"]}}]}";
    }
}
