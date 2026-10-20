package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.bug.BugAttachmentMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatMedia;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugClassifyHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final UUID BUG_ID = UUID.randomUUID();

    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private BugAttachmentMapper bugAttachmentMapper;
    @Mock
    private BugAttachmentService bugAttachmentService;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private BugClassifyHandler handler;

    private ProjectModule module() {
        ProjectModule module = new ProjectModule();
        module.setId(MODULE_ID);
        module.setName("登录模块");
        return module;
    }

    // ---------- SPI 契约 ----------

    @Test
    void type_isBugClassify() {
        assertEquals("bug_classify", handler.type());
        assertTrue(handler.defaultPrompt().contains("{{bugContext}}"));
    }

    @Test
    void validateInput_emptyInput_throwsInputInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of()));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_batchOverLimit_throwsInputInvalid() {
        List<String> ids = java.util.stream.IntStream.range(0, 501)
                .mapToObj(i -> UUID.randomUUID().toString()).toList();
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("bugIds", ids)));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    // ---------- 执行（批量） ----------

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
    void execute_batchDropsInvalidEnumAndOutOfScopeBug() {
        Bug target = new Bug();
        target.setId(BUG_ID);
        target.setProjectId(PROJECT_ID);
        target.setTitle("登录按钮无反应");
        when(context.getInput()).thenReturn(Map.of("bugIds", List.of(BUG_ID.toString())));
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(context.prompt(any(), any())).thenReturn("prompt");
        when(bugMapper.listByIds(anyCollection())).thenReturn(List.of(target));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        when(context.chat(any(), any())).thenReturn(new AiChatReply("""
                {"items":[
                  {"bugId":"%s","suggestions":{"bugType":{"value":"not_a_type","reason":"无效类型"}}},
                  {"bugId":"%s","suggestions":{"bugType":{"value":"code_error","reason":"越界缺陷"}}}]}
                """.formatted(BUG_ID, UUID.randomUUID()), 10, 5));

        var result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(1, artifacts.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) artifacts.get(0).get("content");
        @SuppressWarnings("unchecked")
        Map<String, Object> suggestions = (Map<String, Object>) content.get("suggestions");
        // 非法枚举剔除后为空 map（清洗优先于报错），越界 bugId 不产产物
        assertTrue(suggestions.isEmpty());
    }

    // ---------- 截图入模（详设 3.6） ----------

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

        // 批量附件默认空列表 → 无视觉诉求
        assertNull(handler.requiredCapability(Map.of("bugIds", List.of(BUG_ID.toString())),
                new TaskSubmitContext(PROJECT_ID, WORKSPACE_ID, USER_ID, null)));
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
