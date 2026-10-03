package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementSplitHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();

    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private RequirementSplitHandler handler;

    private Requirement item(String status) {
        Requirement item = new Requirement();
        item.setId(REQUIREMENT_ID);
        item.setProjectId(PROJECT_ID);
        item.setCode("REQ-007");
        item.setTitle("登录验证码");
        item.setDescription("输入验证码后方可登录");
        item.setStatus(status);
        return item;
    }

    private void stubContext() {
        when(context.getInput()).thenReturn(Map.of("requirementId", REQUIREMENT_ID.toString()));
        when(context.getProjectId()).thenReturn(PROJECT_ID);
    }

    // ---------- SPI 契约 ----------

    @Test
    void type_isRequirementSplit() {
        assertEquals("requirement_split", handler.type());
        assertTrue(handler.defaultPrompt().contains("{{requirementTitle}}"));
    }

    @Test
    void validateInput_missingRequirementId_throwsInputInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of()));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_unparsableUuid_throwsInputInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("requirementId", "not-a-uuid")));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void checkPermission_withoutEditAuthority_throwsNoPermission() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("ai:task"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.checkPermission(user));
        assertEquals(ErrorCodeConstants.REQUIREMENT_NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void checkPermission_withEditAuthority_passes() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("requirement:edit"));

        handler.checkPermission(user); // 不抛即通过
    }

    // ---------- 执行 ----------

    @Test
    void execute_requirementNotFound_throwsNotFound() {
        when(context.getInput()).thenReturn(Map.of("requirementId", REQUIREMENT_ID.toString()));
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void execute_archivedItem_throwsSplitInputInvalid() {
        stubContext();
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(item("archived"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.REQUIREMENT_SPLIT_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void execute_sanitizesArtifactsAndReportsTokens() {
        stubContext();
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(item("confirmed"));
        ProjectModule module = new ProjectModule();
        module.setId(MODULE_ID);
        module.setProjectId(PROJECT_ID);
        module.setName("登录模块");
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module));
        when(context.prompt(any(), any())).thenReturn("rendered prompt");
        String modelOutput = """
                ```json
                {"artifacts":[
                  {"content":{"title":"验证码错误提示","description":"d1","moduleId":"%s","priority":"high","extra":1}},
                  {"content":{"title":"   ","description":"空白标题应被丢弃"}},
                  {"content":{"title":"无模块建议","moduleId":"%s","priority":"urgent"}},
                  {"content":{"title":"  补录描述  ","description":"  保留正文  "}}
                ]}
                ```
                """.formatted(MODULE_ID, UUID.randomUUID());
        when(context.chat(any(), eq("rendered prompt"))).thenReturn(new AiChatReply(modelOutput, 120, 340));

        TaskResult result = handler.execute(context);

        assertEquals(120, result.tokensIn());
        assertEquals(340, result.tokensOut());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        // 空白标题被丢弃 → 3 条产物，key 按序重编
        assertEquals(3, artifacts.size());
        assertEquals("item-1", artifacts.get(0).get("key"));
        assertEquals("requirement_suggestion", artifacts.get(0).get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> firstContent = (Map<String, Object>) artifacts.get(0).get("content");
        assertEquals(MODULE_ID.toString(), firstContent.get("moduleId"));
        assertEquals("high", firstContent.get("priority"));

        // 非法模块与非法优先级置 null，空白标题之外的标题去空白
        @SuppressWarnings("unchecked")
        Map<String, Object> secondContent = (Map<String, Object>) artifacts.get(1).get("content");
        assertNull(secondContent.get("moduleId"));
        assertNull(secondContent.get("priority"));
        @SuppressWarnings("unchecked")
        Map<String, Object> thirdContent = (Map<String, Object>) artifacts.get(2).get("content");
        assertEquals("补录描述", thirdContent.get("title"));

        // 提示词变量渲染：模块清单与需求字段注入
        ArgumentCaptor<Map<String, String>> varCaptor = ArgumentCaptor.forClass(Map.class);
        verify(context).prompt(any(), varCaptor.capture());
        assertEquals("REQ-007", varCaptor.getValue().get("requirementCode"));
        assertTrue(varCaptor.getValue().get("moduleOptions").contains(MODULE_ID.toString()));
        verify(context).report(50, "模型生成拆分建议");
    }

    @Test
    void execute_flatArtifactStructure_tolerated() {
        stubContext();
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(item("confirmed"));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(context.prompt(any(), any())).thenReturn("p");
        // 模型未按 content 包裹 → 顶层字段兜底
        when(context.chat(any(), any())).thenReturn(
                new AiChatReply("{\"artifacts\":[{\"title\":\"顶层标题\",\"description\":\"d\"}]}", 10, 20));

        TaskResult result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(1, artifacts.size());
        assertEquals("顶层标题", artifacts.get(0).get("title"));
    }

    @Test
    void execute_unparsableOutput_throwsModelCallFailed() {
        stubContext();
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(item("confirmed"));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(context.prompt(any(), any())).thenReturn("p");
        when(context.chat(any(), any())).thenReturn(new AiChatReply("这不是 JSON", 10, 20));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), exception.getCode());
    }

    @Test
    void execute_jsonWithoutArtifacts_returnsEmptySuccess() {
        stubContext();
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(item("confirmed"));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(context.prompt(any(), any())).thenReturn("p");
        when(context.chat(any(), any())).thenReturn(new AiChatReply("{}", 10, 20));

        TaskResult result = handler.execute(context);

        assertTrue(((List<?>) result.result().get("artifacts")).isEmpty());
    }

    @Test
    void execute_emptyModuleList_rendersHint() {
        stubContext();
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(item("confirmed"));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(context.prompt(any(), any())).thenReturn("p");
        when(context.chat(any(), any())).thenReturn(new AiChatReply("{}", 10, 20));

        handler.execute(context);

        ArgumentCaptor<Map<String, String>> varCaptor = ArgumentCaptor.forClass(Map.class);
        verify(context).prompt(any(), varCaptor.capture());
        assertTrue(varCaptor.getValue().get("moduleOptions").contains("暂无模块"));
    }
}
