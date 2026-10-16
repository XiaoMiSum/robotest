package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import io.github.xiaomisum.robotest.model.entity.file.FileResource;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.bug.BugAttachmentMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.file.FileResourceMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatMedia;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import io.github.xiaomisum.robotest.service.domain.file.FileContent;
import io.github.xiaomisum.robotest.service.domain.file.FileResourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugClassifyHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID BUG_ID = UUID.randomUUID();

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
    private BugAttachmentMapper bugAttachmentMapper;
    @Mock
    private BugAttachmentService bugAttachmentService;
    @Mock
    private FileResourceMapper fileResourceMapper;
    @Mock
    private FileResourceService fileResourceService;
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
    void execute_batchMode_emitsPendingArtifactsPerBug() {
        Bug target = new Bug();
        target.setId(BUG_ID);
        target.setProjectId(PROJECT_ID);
        target.setTitle("登录按钮无反应");
        target.setReproSteps("1. 打开登录页");
        target.setCreatedAt(java.time.LocalDateTime.now());
        when(context.getInput()).thenReturn(Map.of("bugIds", List.of(BUG_ID.toString())));
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(context.prompt(any(), any())).thenReturn("prompt");
        when(bugMapper.listByIds(anyCollection())).thenReturn(List.of(target));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        when(context.chat(any(), any())).thenReturn(new AiChatReply("""
                {"items":[{"bugId":"%s","suggestions":{
                  "bugType":{"value":"code_error","reason":"功能失效"},
                  "severity":{"value":"fatal","reason":"阻断"}}}]}
                """.formatted(BUG_ID), 10, 5));

        var result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(1, artifacts.size());
        Map<String, Object> artifact = artifacts.get(0);
        assertEquals("bug-" + BUG_ID, artifact.get("key"));
        assertEquals("pending", artifact.get("confirmStatus"));
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) artifact.get("content");
        assertEquals(BUG_ID.toString(), content.get("bugId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> suggestions = (Map<String, Object>) content.get("suggestions");
        assertEquals("code_error", ((Map<?, ?>) suggestions.get("bugType")).get("value"));
    }

    @Test
    void validateInput_batchOverLimit_throwsInputInvalid() {
        List<String> ids = java.util.stream.IntStream.range(0, 501)
                .mapToObj(i -> UUID.randomUUID().toString()).toList();
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("bugIds", ids)));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
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

    // ---------- 截图入模（详设 3.5 / 3.6） ----------

    @Test
    void validateInput_draftImagesOverLimit_throwsInputInvalid() {
        List<String> fileIds = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> UUID.randomUUID().toString()).toList();

        ServiceException exception = assertThrows(ServiceException.class, () -> handler.validateInput(
                Map.of("draft", Map.of("title", "登录无响应", "fileIds", fileIds))));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_draftNonImageFile_throwsInputInvalid() {
        UUID fileId = UUID.randomUUID();
        FileResource file = new FileResource();
        file.setFileName("复现说明.txt");
        file.setFileSize(1024L);
        when(fileResourceMapper.selectById(fileId)).thenReturn(file);

        ServiceException exception = assertThrows(ServiceException.class, () -> handler.validateInput(
                Map.of("draft", Map.of("title", "登录无响应", "fileIds", List.of(fileId.toString())))));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_draftOversizedImage_throwsInputInvalid() {
        UUID fileId = UUID.randomUUID();
        FileResource file = new FileResource();
        file.setFileName("shot.png");
        file.setFileSize(11L * 1024 * 1024);
        when(fileResourceMapper.selectById(fileId)).thenReturn(file);

        ServiceException exception = assertThrows(ServiceException.class, () -> handler.validateInput(
                Map.of("draft", Map.of("title", "登录无响应", "fileIds", List.of(fileId.toString())))));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_draftValidImage_passes() {
        UUID fileId = UUID.randomUUID();
        FileResource file = new FileResource();
        file.setFileName("shot.png");
        file.setFileSize(2L * 1024 * 1024);
        when(fileResourceMapper.selectById(fileId)).thenReturn(file);

        handler.validateInput(Map.of("draft", Map.of("title", "登录无响应",
                "fileIds", List.of(fileId.toString()))));

        verify(fileResourceMapper).selectById(fileId);
    }

    @Test
    void requiredCapability_draftWithImages_requiresVision() {
        String capability = handler.requiredCapability(Map.of("draft", Map.of(
                        "title", "登录无响应", "fileIds", List.of(UUID.randomUUID().toString()))),
                new TaskSubmitContext(PROJECT_ID, WORKSPACE_ID, USER_ID, null));

        assertEquals(AiSettingsReader.CAPABILITY_VISION, capability);
    }

    @Test
    void requiredCapability_batchWithImageAttachment_requiresVision() {
        Bug bug = new Bug();
        bug.setId(BUG_ID);
        bug.setProjectId(PROJECT_ID);
        when(bugMapper.listByIds(anyCollection())).thenReturn(List.of(bug));
        when(bugAttachmentMapper.listByBugIds(anyList()))
                .thenReturn(List.of(attachment(BUG_ID, "shot.png", "image/png")));

        String capability = handler.requiredCapability(Map.of("bugIds", List.of(BUG_ID.toString())),
                new TaskSubmitContext(PROJECT_ID, WORKSPACE_ID, USER_ID, null));

        assertEquals(AiSettingsReader.CAPABILITY_VISION, capability);
    }

    @Test
    void requiredCapability_textOnly_returnsNull() {
        Bug bug = new Bug();
        bug.setId(BUG_ID);
        bug.setProjectId(PROJECT_ID);
        when(bugMapper.listByIds(anyCollection())).thenReturn(List.of(bug));

        // 批量附件默认空列表 → 无视觉诉求；纯文本草稿同理
        assertNull(handler.requiredCapability(Map.of("bugIds", List.of(BUG_ID.toString())),
                new TaskSubmitContext(PROJECT_ID, WORKSPACE_ID, USER_ID, null)));
        assertNull(handler.requiredCapability(Map.of("draft", Map.of("title", "纯文本草稿")),
                new TaskSubmitContext(PROJECT_ID, WORKSPACE_ID, USER_ID, null)));
    }

    @Test
    void execute_draftWithImages_passesMediaInDeclaredOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(fileResourceService.readBytes(second))
                .thenReturn(new FileContent(new byte[]{2}, null, "b.jpg"));
        when(fileResourceService.readBytes(first))
                .thenReturn(new FileContent(new byte[]{1}, "image/png", "a.png"));
        when(context.getInput()).thenReturn(Map.of("draft", Map.of(
                "title", "登录页点击登录无响应",
                "steps", "1. 打开登录页",
                "fileIds", List.of(second.toString(), first.toString()))));
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(context.getWorkspaceId()).thenReturn(WORKSPACE_ID);
        when(context.getUserId()).thenReturn(USER_ID);
        when(context.prompt(any(), any())).thenReturn("prompt");
        when(context.chat(any(), any(), anyList()))
                .thenReturn(new AiChatReply("{\"suggestions\":{},\"assigneeCandidates\":[]}", 10, 5));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        stubMembers();
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of());

        handler.execute(context);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMedia>> captor = ArgumentCaptor.forClass(List.class);
        verify(context).chat(any(), any(), captor.capture());
        List<AiChatMedia> images = captor.getValue();
        assertEquals(2, images.size());
        // 按 fileIds 声明顺序直传；contentType 非 image/* 时按扩展名回落 MIME
        assertArrayEquals(new byte[]{2}, images.get(0).data());
        assertEquals("image/jpeg", images.get(0).mimeType());
        assertArrayEquals(new byte[]{1}, images.get(1).data());
        assertEquals("image/png", images.get(1).mimeType());
    }

    @Test
    void execute_batchAttachments_cappedPerBugAndTotal() {
        List<Bug> bugs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Bug bug = new Bug();
            bug.setId(UUID.randomUUID());
            bug.setProjectId(PROJECT_ID);
            bug.setTitle("缺陷 " + i);
            bugs.add(bug);
        }
        when(context.getInput()).thenReturn(Map.of("bugIds",
                bugs.stream().map(bug -> bug.getId().toString()).toList()));
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(context.prompt(any(), any())).thenReturn("prompt");
        when(bugMapper.listByIds(anyCollection())).thenReturn(bugs);
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        when(context.chat(any(), any(), anyList()))
                .thenReturn(new AiChatReply("{\"items\":[]}", 10, 5));
        // 每缺陷 3 张图像候选 + 1 张非图像：单缺陷取前 2 张、全批 8 张按缺陷序截断
        List<BugAttachment> attachments = new ArrayList<>();
        for (Bug bug : bugs) {
            attachments.add(attachment(bug.getId(), "a.png", "image/png"));
            attachments.add(attachment(bug.getId(), "b.jpg", null));
            attachments.add(attachment(bug.getId(), "c.png", "image/png"));
            attachments.add(attachment(bug.getId(), "d.txt", "text/plain"));
        }
        when(bugAttachmentMapper.listByBugIds(anyList())).thenReturn(attachments);
        when(bugAttachmentService.readBytes(any())).thenReturn(new byte[]{1, 2, 3});

        handler.execute(context);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMedia>> captor = ArgumentCaptor.forClass(List.class);
        verify(context).chat(any(), any(), captor.capture());
        assertEquals(8, captor.getValue().size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> varCaptor = ArgumentCaptor.forClass(Map.class);
        verify(context).prompt(any(), varCaptor.capture());
        String bugContext = varCaptor.getValue().get("bugContext");
        // 前 4 条缺陷各带 2 张（第 1～8 张），第 5 条超出配额仅文本
        assertTrue(bugContext.contains("截图 2 张（随附图片第 1～2 张）"));
        assertTrue(bugContext.contains("截图 2 张（随附图片第 7～8 张）"));
        assertFalse(bugContext.contains("第 9～10 张"));
    }

    private static BugAttachment attachment(UUID bugId, String fileName, String contentType) {
        BugAttachment attachment = new BugAttachment();
        attachment.setBugId(bugId);
        attachment.setFileName(fileName);
        attachment.setContentType(contentType);
        return attachment;
    }
}
