package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantPromptSupport;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantService;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantStreamBridge;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantToolLoop;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.domain.file.FileResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ErrorCode;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 助手解析任务单测（详设 3.5 / 4.1）：输入归属校验、循环终态三路落盘与事件、
 * 会话私有项目写入、悬空补偿、失败先落 error 再抛出与 vision 能力派生（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantParseHandlerTest {

    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID FILE_ID = UUID.randomUUID();
    private static final int PARSE_FAILED = ErrorCodeConstants.ASSISTANT_PARSE_FAILED.code();

    @Mock
    private AiAssistantMessageMapper messageMapper;
    @Mock
    private AiAssistantConversationMapper conversationMapper;
    @Mock
    private AssistantStreamBridge streamBridge;
    @Mock
    private AssistantPromptSupport promptSupport;
    @Mock
    private AssistantService assistantService;
    @Mock
    private AssistantToolLoop toolLoop;
    @Mock
    private FileResourceService fileResourceService;
    @Mock
    private AiModelClient modelClient;
    @Mock
    private AiPromptService promptService;
    @Mock
    private TaskExecutionContext context;
    @Mock
    private AssistantStreamBridge.StreamChannel channel;

    @InjectMocks
    private AssistantParseHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(streamBridge.get(MESSAGE_ID)).thenReturn(channel);
        lenient().when(context.getInput()).thenReturn(input());
        lenient().when(context.getWorkspaceId()).thenReturn(WORKSPACE_ID);
        lenient().when(context.getUserId()).thenReturn(USER_ID);
        lenient().when(context.getModel()).thenReturn(new AiModelConfig());
        lenient().when(context.prompt(anyString(), any())).thenReturn("rendered");
        lenient().when(context.getModelClient()).thenReturn(modelClient);
        lenient().when(context.getPromptService()).thenReturn(promptService);
        lenient().when(messageMapper.selectById(MESSAGE_ID)).thenReturn(assistantMessage());
        lenient().when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation());
        lenient().when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(history());
        lenient().when(promptSupport.contextText(any(), any())).thenReturn("会话项目状态与页面上下文");
    }

    // ---------- 登记与输入校验 ----------

    @Test
    void type_andDefaultPrompt_declareAgentScene() {
        assertEquals("assistant_parse", handler.type());
        String prompt = handler.defaultPrompt();
        assertTrue(prompt.contains("{{history}}"));
        assertTrue(prompt.contains("{{context}}"));
        assertTrue(prompt.contains("ask_user"));
    }

    @Test
    void validateInput_rejectsMissingOrMalformedMessageId() {
        assertInputInvalid(() -> handler.validateInput(null));
        assertInputInvalid(() -> handler.validateInput(Map.of("messageId", "not-a-uuid")));
    }

    @Test
    void validateInput_context_rejectsForeignOwnership() {
        assertInputInvalid(() -> handler.validateInput(input(),
                new TaskSubmitContext(null, WORKSPACE_ID, null, null)));
        assertInputInvalid(() -> handler.validateInput(input(),
                new TaskSubmitContext(null, WORKSPACE_ID, UUID.randomUUID(), null)));
    }

    @Test
    void validateInput_context_acceptsOwnAssistantMessage() {
        lenient().when(messageMapper.selectById(MESSAGE_ID)).thenReturn(assistantMessage());
        lenient().when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversationWithUser());

        handler.validateInput(input(), new TaskSubmitContext(null, WORKSPACE_ID, USER_ID, null));
    }

    // ---------- 终态三路 ----------

    @Test
    void execute_clarify_persistsQuestionWithOptionsAndEmits() {
        when(toolLoop.run(any(), eq(modelClient))).thenReturn(
                AssistantToolLoop.LoopOutcome.clarify("请问在哪个项目下创建？",
                        List.of("当前项目：商城", "会话项目：门店"),
                        List.of(Map.of("id", "tc-1", "name", "ask_user", "arguments", "{}")), 12, 3));

        TaskResult result = handler.execute(context);

        assertEquals("clarify", result.result().get("kind"));
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        AiAssistantMessage carrier = captor.getValue();
        assertEquals(Constants.AiAssistantMessageStatus.DONE, carrier.getStatus());
        assertTrue(carrier.getContent().contains("请问在哪个项目下创建？"));
        assertTrue(carrier.getContent().contains("- 当前项目：商城"));
        assertEquals(1, carrier.getToolCalls().size());
        verify(channel).send(eq("clarify"), anyMap());
        verify(channel).send(eq("done"), anyMap());
        verify(channel).close();
    }

    @Test
    void execute_intent_persistsPreviewAndRemembersProject() {
        Map<String, Object> intent = intent();
        when(toolLoop.run(any(), eq(modelClient))).thenReturn(
                AssistantToolLoop.LoopOutcome.intent(intent, List.of(), 30, 9));

        TaskResult result = handler.execute(context);

        assertEquals("intent", result.result().get("kind"));
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(intent, captor.getValue().getIntent());
        assertEquals("已解析为以下变更，确认后执行。", captor.getValue().getContent());
        // 会话私有项目写入 context_snapshot（详设 4.4）
        ArgumentCaptor<AiAssistantConversation> conversationCaptor =
                ArgumentCaptor.forClass(AiAssistantConversation.class);
        verify(conversationMapper).updateById(conversationCaptor.capture());
        assertEquals(PROJECT_ID.toString(),
                conversationCaptor.getValue().getContextSnapshot().get("projectId"));
        verify(channel).send(eq("preview"), anyMap());
        verify(channel).send(eq("done"), anyMap());
    }

    @Test
    void execute_answer_replaysDeltaWithCitations() {
        List<Map<String, Object>> citations = List.of(Map.of(
                "type", "requirement", "id", UUID.randomUUID().toString(), "title", "REQ-1", "quote", "…"));
        when(toolLoop.run(any(), eq(modelClient))).thenReturn(
                AssistantToolLoop.LoopOutcome.answer("执行进度 80%。", citations, List.of(), 40, 12));

        TaskResult result = handler.execute(context);

        assertEquals("answer", result.result().get("kind"));
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals("执行进度 80%。", captor.getValue().getContent());
        assertEquals(1, captor.getValue().getCitations().size());
        // citations 事件载荷为引用数组（List），非 Map
        verify(channel).send(eq("citations"), any());
        verify(channel).send(eq("delta"), anyMap());
        verify(channel).send(eq("done"), anyMap());
    }

    @Test
    void execute_answerWithoutCitations_skipsCitationsEvent() {
        when(toolLoop.run(any(), eq(modelClient))).thenReturn(
                AssistantToolLoop.LoopOutcome.answer("固定文案", List.of(), List.of(), 0, 0));

        handler.execute(context);

        verify(channel, never()).send(eq("citations"), any());
        verify(channel).send(eq("delta"), anyMap());
    }

    // ---------- 失败路径 ----------

    @Test
    void execute_loopParseFailed_failsMessageAndEmitsError() {
        when(toolLoop.run(any(), eq(modelClient)))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> handler.execute(context));

        assertEquals(PARSE_FAILED, error.getCode());
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, captor.getValue().getStatus());
        verify(channel).send(eq("error"), anyMap());
        verify(channel).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_loopRuntimeException_mapsTo18117() {
        when(messageMapper.selectById(MESSAGE_ID)).thenReturn(assistantMessage());
        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(history());
        when(toolLoop.run(any(), eq(modelClient))).thenThrow(new IllegalStateException("boom"));

        // 原始异常上抛由任务引擎落 failed；18117 经 error 事件与消息 error 状态透出（详设 3.5）
        assertThrows(IllegalStateException.class, () -> handler.execute(context));

        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(channel).send(eq("error"), data.capture());
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                ((Map<?, ?>) data.getValue()).get("code"));
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, captor.getValue().getStatus());
    }

    @Test
    void execute_missingMessage_closesChannelAndReturnsMissing() {
        when(messageMapper.selectById(MESSAGE_ID)).thenReturn(null);

        TaskResult result = handler.execute(context);

        assertEquals("missing", result.result().get("kind"));
        verify(channel).close();
        verifyNoLoop();
    }

    @Test
    void execute_missingImageFile_failsBeforeModelCall() {
        AiAssistantMessage trigger = trigger();
        trigger.setImages(List.of(Map.of("fileId", "not-a-uuid", "name", "a.png")));
        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(historyWith(trigger));

        ServiceException error = assertThrows(ServiceException.class,
                () -> handler.execute(context));

        assertEquals(ErrorCodeConstants.ASSISTANT_IMAGE_INVALID.code(), error.getCode());
        verifyNoLoop();
    }

    @Test
    void execute_historyWithoutUserMessage_throwsInputInvalid() {
        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID))
                .thenReturn(List.of(assistantMessage()));

        assertInputInvalid(() -> handler.execute(context));
        verifyNoLoop();
    }

    // ---------- 悬空补偿与能力派生 ----------

    @Test
    void execute_compensatesDanglingToolCallsOfUnfinishedMessages() {
        AiAssistantMessage dangling = assistantMessage();
        dangling.setStatus(Constants.AiAssistantMessageStatus.STREAMING);
        dangling.setToolCalls(List.of(Map.of("id", "tc-9", "name", "query_projects")));
        AiAssistantMessage done = assistantMessage();
        done.setId(UUID.randomUUID());
        done.setStatus(Constants.AiAssistantMessageStatus.DONE);
        done.setToolCalls(List.of(Map.of("id", "tc-done", "name", "create_case")));
        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(
                historyWith(assistantMessage(), dangling, done));
        when(toolLoop.run(any(), eq(modelClient))).thenReturn(
                AssistantToolLoop.LoopOutcome.answer("ok", List.of(), List.of(), 1, 1));

        handler.execute(context);

        verify(assistantService).appendToolMessage(eq(CONVERSATION_ID), eq("tc-9"),
                contains("操作已超时未执行"));
        // done 终态消息按设计不补（其 tool_calls 为审计轨迹，无回填义务）
        verify(assistantService, never()).appendToolMessage(any(), eq("tc-done"), anyString());
    }

    @Test
    void requiredCapability_derivesVisionFromTriggerImageOnly() {
        AiAssistantMessage trigger = trigger();
        trigger.setImages(List.of(Map.of("fileId", FILE_ID.toString(), "name", "a.png")));
        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(historyWith(trigger));

        assertEquals(AiSettingsReader.CAPABILITY_VISION, handler.requiredCapability(input()));

        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(history());
        assertNull(handler.requiredCapability(input()));
    }

    // ---------- 辅助 ----------

    /** 循环入参透传断言（workspace / 会话 / 附图 / 通道），在澄清用例中校验一次即可覆盖全部字段 */
    @Test
    void execute_loopRequestCarriesVerifiedContext() {
        when(toolLoop.run(any(), eq(modelClient))).thenReturn(
                AssistantToolLoop.LoopOutcome.clarify("q", List.of(), List.of(), 0, 0));

        handler.execute(context);

        ArgumentCaptor<AssistantToolLoop.LoopRequest> captor =
                ArgumentCaptor.forClass(AssistantToolLoop.LoopRequest.class);
        verify(toolLoop).run(captor.capture(), eq(modelClient));
        AssistantToolLoop.LoopRequest request = captor.getValue();
        assertEquals(USER_ID, request.userId());
        assertEquals(WORKSPACE_ID, request.workspaceId());
        assertEquals(CONVERSATION_ID, request.conversationId());
        assertEquals(channel, request.channel());
        assertNotNull(request.systemPrompt());
        assertTrue(request.userPrompt().equals("rendered"));
    }

    private void verifyNoLoop() {
        verify(toolLoop, never()).run(any(), any());
    }

    private static Map<String, Object> input() {
        return Map.of("messageId", MESSAGE_ID.toString());
    }

    private static AiAssistantMessage assistantMessage() {
        AiAssistantMessage message = new AiAssistantMessage();
        message.setId(MESSAGE_ID);
        message.setConversationId(CONVERSATION_ID);
        message.setRole(Constants.AiAssistantMessageRole.ASSISTANT);
        message.setStatus(Constants.AiAssistantMessageStatus.STREAMING);
        return message;
    }

    private static AiAssistantConversation conversation() {
        AiAssistantConversation conversation = new AiAssistantConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(USER_ID);
        conversation.setWorkspaceId(WORKSPACE_ID);
        return conversation;
    }

    private static AiAssistantConversation conversationWithUser() {
        return conversation();
    }

    private static AiAssistantMessage trigger() {
        AiAssistantMessage message = new AiAssistantMessage();
        message.setId(UUID.randomUUID());
        message.setConversationId(CONVERSATION_ID);
        message.setRole(Constants.AiAssistantMessageRole.USER);
        message.setContent("帮我建一个登录用例");
        return message;
    }

    private static List<AiAssistantMessage> history() {
        return historyWith(trigger());
    }

    private static List<AiAssistantMessage> historyWith(AiAssistantMessage... extras) {
        java.util.ArrayList<AiAssistantMessage> list = new java.util.ArrayList<>();
        list.add(trigger());
        for (AiAssistantMessage extra : extras) {
            list.add(extra);
        }
        return list;
    }

    private static Map<String, Object> intent() {
        Map<String, Object> scope = new java.util.LinkedHashMap<>();
        scope.put("workspaceId", WORKSPACE_ID.toString());
        scope.put("projectId", PROJECT_ID.toString());
        scope.put("projectName", "商城项目");
        Map<String, Object> intent = new java.util.LinkedHashMap<>();
        intent.put("kind", "create_case");
        intent.put("targetTitle", "登录用例");
        intent.put("projectId", PROJECT_ID);
        intent.put("scope", scope);
        return intent;
    }

    private static void assertCode(ErrorCode expected, Runnable invocation) {
        ServiceException error = assertThrows(ServiceException.class, invocation::run);
        assertEquals(expected.code(), error.getCode());
    }

    private static void assertInputInvalid(Runnable invocation) {
        assertCode(ErrorCodeConstants.AI_TASK_INPUT_INVALID, invocation);
    }
}
