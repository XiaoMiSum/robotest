package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugClassifyHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();

    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private BugClassifyHandler handler;

    private void stubContext() {
        lenient().when(context.getInput()).thenReturn(Map.of("draft", Map.of(
                "title", "登录页点击登录无响应",
                "steps", "1. 打开登录页\n2. 点击登录按钮")));
        lenient().when(context.getProjectId()).thenReturn(PROJECT_ID);
        lenient().when(context.getWorkspaceId()).thenReturn(WORKSPACE_ID);
        lenient().when(context.getUserId()).thenReturn(USER_ID);
        lenient().when(context.prompt(any(), any())).thenReturn("prompt");
    }

    private ProjectModule module() {
        ProjectModule module = new ProjectModule();
        module.setId(MODULE_ID);
        module.setName("登录模块");
        return module;
    }

    private void stubMembers() {
        WorkspaceUser row = new WorkspaceUser();
        row.setUserId(MEMBER_ID);
        when(workspaceUserMapper.listByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(row));
        SysUser user = new SysUser();
        user.setId(MEMBER_ID);
        user.setName("李四");
        when(sysUserMapper.listByIds(anyCollection())).thenReturn(List.of(user));
    }

    // ---------- SPI 契约 ----------

    @Test
    void type_isBugClassify() {
        assertEquals("bug_classify", handler.type());
        assertTrue(handler.defaultPrompt().contains("{{bugContext}}"));
    }

    @Test
    void validateInput_missingDraftTitle_throwsInputInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("draft", Map.of("steps", "x"))));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_overlongTitle_throwsInputInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("draft", Map.of("title", "标".repeat(301)))));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void checkPermission_withoutBugView_throwsNoPermission() {
        LoginUser user = org.mockito.Mockito.mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("ai:task"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.checkPermission(user));
        assertEquals(ErrorCodeConstants.NO_PERMISSION.code(), exception.getCode());
    }

    // ---------- 执行 ----------

    @Test
    void execute_sanitizesSuggestionsAndFiltersCandidates() {
        when(context.chat(any(), any())).thenReturn(new AiChatReply("""
                {"suggestions":{
                  "bugType":{"value":"code_error","reason":"功能失效"},
                  "severity":{"value":"blocker","reason":"非法枚举应剔除"},
                  "priority":{"value":"high","reason":"阻断主流程"},
                  "moduleId":{"value":"%s","reason":"登录相关"},
                  "keywords":{"value":["登录","无响应"],"reason":""}},
                 "assigneeCandidates":[
                   {"userId":"%s","reason":"同模块历史处理人"},
                   {"userId":"%s","reason":"非成员应剔除"}]}
                """.formatted(MODULE_ID, MEMBER_ID, UUID.randomUUID()), 10, 5));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        stubMembers();
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of());
        stubContext();

        var result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(1, artifacts.size());
        Map<String, Object> artifact = artifacts.get(0);
        assertEquals("draft", artifact.get("key"));
        assertEquals("classify_suggestion", artifact.get("kind"));
        assertEquals("not_applicable", artifact.get("confirmStatus"));

        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) artifact.get("content");
        @SuppressWarnings("unchecked")
        Map<String, Object> suggestions = (Map<String, Object>) content.get("suggestions");
        // 非法枚举被剔除，合法值保留
        assertNull(suggestions.get("severity"));
        assertEquals("code_error", ((Map<?, ?>) suggestions.get("bugType")).get("value"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) content.get("assigneeCandidates");
        assertEquals(1, candidates.size());
        assertEquals(MEMBER_ID.toString(), candidates.get(0).get("userId"));
        assertEquals("李四", candidates.get(0).get("name"));
        assertEquals(Boolean.TRUE, candidates.get(0).get("memberValid"));
    }

    @Test
    void execute_dropsInvalidEnumAndKeepsValidModule() {
        when(context.chat(any(), any())).thenReturn(new AiChatReply("""
                {"suggestions":{
                  "bugType":{"value":"not_a_type","reason":"无效类型"},
                  "severity":{"value":"serious","reason":"主流程受损"},
                  "moduleId":{"value":"%s","reason":"登录模块"}},
                 "assigneeCandidates":[]}
                """.formatted(MODULE_ID), 10, 5));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        stubMembers();
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of());
        stubContext();

        var result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) artifacts.get(0).get("content");
        @SuppressWarnings("unchecked")
        Map<String, Object> suggestions = (Map<String, Object>) content.get("suggestions");
        assertNull(suggestions.get("bugType"));
        assertEquals("serious", ((Map<?, ?>) suggestions.get("severity")).get("value"));
        assertEquals(MODULE_ID.toString(), ((Map<?, ?>) suggestions.get("moduleId")).get("value"));
    }

    @Test
    void execute_vectorUnavailable_degradesToEmptyRefs() {
        when(context.chat(any(), any())).thenReturn(new AiChatReply(
                "{\"suggestions\":{},\"assigneeCandidates\":[]}", 10, 5));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        stubMembers();
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE));
        stubContext();

        var result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) artifacts.get(0).get("content");
        assertTrue(((List<?>) content.get("sourceRefs")).isEmpty());
    }
}
