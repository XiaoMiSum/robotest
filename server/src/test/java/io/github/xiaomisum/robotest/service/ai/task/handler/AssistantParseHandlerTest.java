package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantPromptSupport;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantStreamBridge;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiChatRequest;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ErrorCode;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 助手解析任务单测（详设 3.5 流式链路 / 4.1 解析流程）：输入归属校验、三类分流落盘与事件、
 * 意图引用越权拒绝、检索零命中 / 不可用降级、失败先落 error 再抛出（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantParseHandlerTest {

    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();

    @Mock
    private AiAssistantMessageMapper messageMapper;
    @Mock
    private AiAssistantConversationMapper conversationMapper;
    @Mock
    private AssistantRefChecker refChecker;
    @Mock
    private AssistantStreamBridge streamBridge;
    @Mock
    private AssistantPromptSupport promptSupport;
    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private AiModelClient modelClient;
    @Mock
    private AiPromptService promptService;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private AssistantParseHandler handler;

    /** 真实通道（mock 桥只负责投递）：驱动事件写入与关闭语义 */
    private AssistantStreamBridge realBridge;
    private AssistantStreamBridge.StreamChannel channel;

    @BeforeEach
    void setUp() {
        realBridge = new AssistantStreamBridge();
        ReflectionTestUtils.setField(realBridge, "messageMapper", messageMapper);
        channel = realBridge.attach(MESSAGE_ID, new SseEmitter());

        lenient().when(streamBridge.get(MESSAGE_ID)).thenReturn(channel);
        lenient().when(context.getInput()).thenReturn(input());
        lenient().when(context.getWorkspaceId()).thenReturn(WORKSPACE_ID);
        lenient().when(context.getUserId()).thenReturn(USER_ID);
        lenient().when(context.getProjectId()).thenReturn(null);
        lenient().when(context.getModel()).thenReturn(new AiModelConfig());
        lenient().when(context.prompt(anyString(), any())).thenReturn("rendered");
        lenient().when(context.getModelClient()).thenReturn(modelClient);
        lenient().when(context.getPromptService()).thenReturn(promptService);
        lenient().when(promptService.render(any(), any(), any())).thenReturn("rendered-answer");
        lenient().when(messageMapper.selectById(MESSAGE_ID)).thenReturn(assistantMessage());
        lenient().when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation());
        lenient().when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(history());
        lenient().when(promptSupport.contextText(any(), any(), any())).thenReturn("项目上下文");
        lenient().when(promptSupport.caseOptions(any(), any())).thenReturn("用例候选");
        lenient().when(promptSupport.reviewOptions(any())).thenReturn("评审候选");
        lenient().when(promptSupport.planOptions(any())).thenReturn("计划候选");
        lenient().when(promptSupport.workspaceProjectIds(any())).thenReturn(List.of(PROJECT_ID));
        lenient().when(promptSupport.projectName(any())).thenReturn("商城项目");
        lenient().when(refChecker.requireActiveWorkspaceCases(any(), any())).thenReturn(Set.of());
    }

    // ---------- 登记与输入校验 ----------

    @Test
    void type_andDefaultPrompt_declareRegisteredScene() {
        assertEquals("assistant_parse", handler.type());
        String prompt = handler.defaultPrompt();
        assertTrue(prompt.contains("{{history}}"));
        assertTrue(prompt.contains("{{context}}"));
        assertTrue(prompt.contains("{{caseOptions}}"));
        assertTrue(prompt.contains("{{reviewOptions}}"));
        assertTrue(prompt.contains("{{planOptions}}"));
    }

    @Test
    void validateInput_rejectsMissingOrMalformedMessageId() {
        assertInputInvalid(() -> handler.validateInput(Map.of()));
        assertInputInvalid(() -> handler.validateInput(Map.of("messageId", "not-a-uuid")));
    }

    @Test
    void validateInput_context_rejectsNullUserOrForeignOrNonAssistant() {
        assertInputInvalid(() -> handler.validateInput(input(), null));
        assertInputInvalid(() -> handler.validateInput(input(),
                new TaskSubmitContext(null, WORKSPACE_ID, null, null)));

        AiAssistantMessage foreignUserMessage = assistantMessage();
        foreignUserMessage.setRole(Constants.AiAssistantMessageRole.USER);
        when(messageMapper.selectById(MESSAGE_ID)).thenReturn(foreignUserMessage);
        assertInputInvalid(() -> handler.validateInput(input(), submitContext()));

        AiAssistantMessage foreignConversation = assistantMessage();
        when(messageMapper.selectById(MESSAGE_ID)).thenReturn(foreignConversation);
        AiAssistantConversation other = conversation();
        other.setUserId(OTHER_ID);
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(other);
        assertInputInvalid(() -> handler.validateInput(input(), submitContext()));
    }

    @Test
    void validateInput_context_acceptsOwnAssistantMessage() {
        assertDoesNotThrow(() -> handler.validateInput(input(), submitContext()));
    }

    // ---------- 执行：收尾与分流 ----------

    @Test
    void execute_missingMessage_closesChannelAndReturnsMissing() {
        when(messageMapper.selectById(MESSAGE_ID)).thenReturn(null);

        TaskResult result = handler.execute(context);

        assertEquals("missing", result.result().get("kind"));
        assertTrue(channel.isClosed());
        verify(messageMapper, never()).updateById(any(AiAssistantMessage.class));
    }

    @Test
    void execute_clarify_persistsQuestionWithOptionsAndEmits() {
        judge("{\"kind\":\"clarify\",\"question\":\"要创建哪个模块的用例？\","
                + "\"options\":[\"登录模块\",\"订单模块\"]}");

        TaskResult result = handler.execute(context);

        assertEquals("clarify", result.result().get("kind"));
        assertEquals(7, result.tokensIn());
        assertEquals(3, result.tokensOut());
        AiAssistantMessage carrier = capturedUpdate();
        assertEquals(MESSAGE_ID, carrier.getId());
        assertEquals(Constants.AiAssistantMessageStatus.DONE, carrier.getStatus());
        assertTrue(carrier.getContent().contains("要创建哪个模块的用例？"));
        assertTrue(carrier.getContent().contains("- 登录模块"));
        assertNull(carrier.getIntent());
        assertNull(carrier.getCitations());
        assertTrue(channel.isClosed());
        verify(promptSupport).contextText(eq(WORKSPACE_ID), any(), any());
        assertJudgeRequest();
    }

    @Test
    void execute_clarify_withoutChannel_persistsOnly() {
        when(streamBridge.get(MESSAGE_ID)).thenReturn(null);
        judge("{\"kind\":\"clarify\",\"question\":\"要查哪个计划的进度？\"}");

        TaskResult result = handler.execute(context);

        assertEquals("clarify", result.result().get("kind"));
        assertEquals("要查哪个计划的进度？", capturedUpdate().getContent());
    }

    @Test
    void execute_clarify_missingQuestion_throwsParseFailed() {
        judge("{\"kind\":\"clarify\"}");

        assertCode(ErrorCodeConstants.ASSISTANT_PARSE_FAILED, () -> handler.execute(context));
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, capturedUpdate().getStatus());
    }

    // ---------- 执行：intent ----------

    @Test
    @SuppressWarnings("unchecked")
    void execute_intent_freezesScopeAndPersistsPreview() {
        judge("{\"kind\":\"intent\",\"summary\":\"将创建 3 条登录用例\",\"intent\":{"
                + "\"kind\":\"create_case\",\"targetTitle\":\"登录用例\",\"projectId\":\"" + PROJECT_ID + "\","
                + "\"createCount\":3,\"changes\":[{\"field\":\"title\",\"op\":\"add\","
                + "\"value\":\"验证码正确可登录\"}],"
                + "\"params\":{\"caseIds\":[\"" + CASE_ID + "\"],\"note\":\"按 PRD\"}}}");
        when(refChecker.requireActiveWorkspaceCases(eq(WORKSPACE_ID), any())).thenReturn(Set.of(PROJECT_ID));

        TaskResult result = handler.execute(context);

        assertEquals("intent", result.result().get("kind"));
        Map<String, Object> artifact = (Map<String, Object>) artifacts(result).get(0);
        assertEquals("intent-preview", artifact.get("key"));
        assertEquals("intent_preview", artifact.get("kind"));

        AiAssistantMessage carrier = capturedUpdate();
        assertEquals("将创建 3 条登录用例", carrier.getContent());
        assertEquals(Constants.AiAssistantMessageStatus.DONE, carrier.getStatus());
        assertNull(carrier.getCitations());
        Map<String, Object> intent = carrier.getIntent();
        // 动作与目标类型由服务端固化（不信赖模型 targetType）
        assertEquals("create_case", intent.get("kind"));
        assertEquals(Constants.TraceNodeType.TEST_CASE, intent.get("targetType"));
        assertEquals(PROJECT_ID, intent.get("projectId"));
        assertEquals(3, intent.get("createCount"));
        assertNotNull(intent.get("expiresAt"));
        Map<String, Object> scope = (Map<String, Object>) intent.get("scope");
        assertEquals(WORKSPACE_ID, scope.get("workspaceId"));
        assertEquals(PROJECT_ID, scope.get("projectId"));
        assertEquals("商城项目", scope.get("projectName"));
        List<Map<String, Object>> changes = (List<Map<String, Object>>) intent.get("changes");
        assertEquals("add", changes.get(0).get("op"));
        Map<String, Object> params = (Map<String, Object>) intent.get("params");
        assertEquals(List.of(CASE_ID), params.get("caseIds"));
        assertEquals("按 PRD", params.get("note"));

        verify(refChecker).requireActiveWorkspaceProject(WORKSPACE_ID, PROJECT_ID);
        verify(refChecker).requireActiveWorkspaceCases(eq(WORKSPACE_ID), any());
        assertTrue(channel.isClosed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_intent_adjustReview_resolvesScopeFromTarget() {
        judge("{\"kind\":\"intent\",\"summary\":\"调整评审范围\",\"intent\":"
                + "{\"kind\":\"adjust_review\",\"targetId\":\"" + TARGET_ID + "\","
                + "\"changes\":[{\"field\":\"status\",\"op\":\"replace\",\"value\":\"rejected\"}]}}");
        when(refChecker.projectIdOf(Constants.TraceNodeType.TEST_REVIEW, TARGET_ID)).thenReturn(PROJECT_ID);
        when(refChecker.titleOf(Constants.TraceNodeType.TEST_REVIEW, TARGET_ID)).thenReturn("登录回归评审");

        handler.execute(context);

        verify(refChecker).requireActiveWorkspaceRef(WORKSPACE_ID,
                Constants.TraceNodeType.TEST_REVIEW, TARGET_ID);
        Map<String, Object> intent = capturedUpdate().getIntent();
        assertEquals(Constants.TraceNodeType.TEST_REVIEW, intent.get("targetType"));
        assertEquals("登录回归评审", intent.get("targetTitle"));
        assertEquals(PROJECT_ID, intent.get("projectId"));
        assertEquals(PROJECT_ID, ((Map<String, Object>) intent.get("scope")).get("projectId"));
    }

    @Test
    void execute_intent_rejectsCrossWorkspaceRef() {
        judge("{\"kind\":\"intent\",\"summary\":\"调整他人评审\",\"intent\":"
                + "{\"kind\":\"adjust_review\",\"targetId\":\"" + TARGET_ID + "\","
                + "\"targetTitle\":\"越权评审\",\"changes\":[]}}");
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE))
                .when(refChecker).requireActiveWorkspaceRef(eq(WORKSPACE_ID), any(), eq(TARGET_ID));

        assertCode(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE, () -> handler.execute(context));

        AiAssistantMessage carrier = capturedUpdate();
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, carrier.getStatus());
        assertTrue(channel.isClosed());
    }

    @Test
    void execute_intent_withoutScopeTarget_throwsParseFailed() {
        judge("{\"kind\":\"intent\",\"summary\":\"查看进度\",\"intent\":{\"kind\":\"view_plan_progress\"}}");

        assertCode(ErrorCodeConstants.ASSISTANT_PARSE_FAILED, () -> handler.execute(context));
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, capturedUpdate().getStatus());
    }

    @Test
    void execute_intent_malformedTargetId_throwsParseFailed() {
        judge("{\"kind\":\"intent\",\"intent\":{\"kind\":\"adjust_review\","
                + "\"targetId\":\"not-a-uuid\",\"targetTitle\":\"x\"}}");

        assertCode(ErrorCodeConstants.ASSISTANT_PARSE_FAILED, () -> handler.execute(context));
        verify(refChecker, never()).requireActiveWorkspaceRef(any(), any(), any());
    }

    // ---------- 执行：answer ----------

    @Test
    @SuppressWarnings("unchecked")
    void execute_answer_streamsDeltasAndPersistsCitations() {
        judge("{\"kind\":\"answer\",\"query\":\"登录用例覆盖情况\"}");
        AiVectorSearchHitRespDTO hit = hit(Constants.TraceNodeType.TEST_CASE, CASE_ID,
                "验证码正确可登录：输入验证码后进入系统");
        AiVectorSearchHitRespDTO duplicate = hit(Constants.TraceNodeType.TEST_CASE, CASE_ID, "重复分块");
        when(vectorSearchService.search(eq("登录用例覆盖情况"), eq(List.of(PROJECT_ID)), isNull(),
                eq(8), eq(USER_ID))).thenReturn(List.of(hit, duplicate));
        when(refChecker.titleOf(Constants.TraceNodeType.TEST_CASE, CASE_ID)).thenReturn("验证码正确可登录");
        when(modelClient.streamChat(any(AiChatRequest.class), any())).thenAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(1);
            onDelta.accept("根据");
            onDelta.accept("检索来源");
            return new AiChatReply("根据检索来源", 12, 9);
        });

        TaskResult result = handler.execute(context);

        assertEquals("answer", result.result().get("kind"));
        assertEquals(7 + 12, result.tokensIn());
        assertEquals(3 + 9, result.tokensOut());
        Map<String, Object> artifact = (Map<String, Object>) artifacts(result).get(0);
        assertEquals("answer", artifact.get("kind"));
        assertEquals("根据检索来源", artifact.get("content"));

        AiAssistantMessage carrier = capturedUpdate();
        assertEquals("根据检索来源", carrier.getContent());
        assertEquals(Constants.AiAssistantMessageStatus.DONE, carrier.getStatus());
        // 同 (type,id) 去重保距序
        assertEquals(1, carrier.getCitations().size());
        Map<String, Object> citation = carrier.getCitations().get(0);
        assertEquals(Constants.TraceNodeType.TEST_CASE, citation.get("type"));
        assertEquals(CASE_ID, citation.get("id"));
        assertEquals("验证码正确可登录", citation.get("title"));
        assertNotNull(citation.get("quote"));
        assertTrue(channel.isClosed());

        // 两段调用均不带 taskId（总册 4.3），场景分别为解析与只读问答
        ArgumentCaptor<AiChatRequest> judgeCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(modelClient).chat(judgeCaptor.capture());
        assertNull(judgeCaptor.getValue().taskId());
        assertEquals("assistant_parse", judgeCaptor.getValue().promptScene());
        ArgumentCaptor<AiChatRequest> answerCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(modelClient).streamChat(answerCaptor.capture(), any());
        assertNull(answerCaptor.getValue().taskId());
        assertEquals("assistant_answer", answerCaptor.getValue().promptScene());
    }

    @Test
    void execute_answer_zeroHits_persistsFixedTextWithoutModel() {
        judge("{\"kind\":\"answer\",\"query\":\"不存在的记录\"}");
        when(vectorSearchService.search(anyString(), any(), any(), eq(8), eq(USER_ID))).thenReturn(List.of());

        TaskResult result = handler.execute(context);

        assertEquals("answer", result.result().get("kind"));
        assertEquals(0, result.result().get("retrieved"));
        AiAssistantMessage carrier = capturedUpdate();
        assertTrue(carrier.getContent().contains("未检索到相关内容"));
        assertEquals(Constants.AiAssistantMessageStatus.DONE, carrier.getStatus());
        assertEquals(List.of(), carrier.getCitations());
        // 零命中不调生成模型，不编造数据（总册 4.5）
        verify(modelClient, never()).streamChat(any(), any());
        assertTrue(channel.isClosed());
    }

    @Test
    void execute_answer_searchUnavailable_degradesWith258Text() {
        judge("{\"kind\":\"answer\",\"query\":\"登录用例覆盖情况\"}");
        when(vectorSearchService.search(anyString(), any(), any(), eq(8), eq(USER_ID)))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED));

        TaskResult result = handler.execute(context);

        assertEquals("answer", result.result().get("kind"));
        AiAssistantMessage carrier = capturedUpdate();
        assertTrue(carrier.getContent().contains("1000018258"));
        assertEquals(Constants.AiAssistantMessageStatus.DONE, carrier.getStatus());
        assertEquals(List.of(), carrier.getCitations());
        verify(modelClient, never()).streamChat(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_answer_streamFailure_persistsPartialWithErrorCode() {
        judge("{\"kind\":\"answer\",\"query\":\"登录用例覆盖情况\"}");
        when(vectorSearchService.search(anyString(), any(), any(), eq(8), eq(USER_ID)))
                .thenReturn(List.of(hit(Constants.TraceNodeType.TEST_CASE, CASE_ID, "分块内容")));
        when(refChecker.titleOf(Constants.TraceNodeType.TEST_CASE, CASE_ID)).thenReturn("验证码正确可登录");
        when(modelClient.streamChat(any(AiChatRequest.class), any())).thenAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(1);
            onDelta.accept("部分回答");
            throw new IllegalStateException("connection reset");
        });

        assertThrows(IllegalStateException.class, () -> handler.execute(context));

        AiAssistantMessage carrier = capturedUpdate();
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, carrier.getStatus());
        // 已流出正文与引用随错误一并保留，供重载补齐
        assertEquals("部分回答", carrier.getContent());
        assertEquals(1, carrier.getCitations().size());
        assertTrue(channel.isClosed());
    }

    @Test
    void execute_historyWithoutUserMessage_throwsInputInvalid() {
        AiAssistantMessage placeholder = new AiAssistantMessage();
        placeholder.setId(MESSAGE_ID);
        placeholder.setRole(Constants.AiAssistantMessageRole.ASSISTANT);
        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(List.of(placeholder));

        assertCode(ErrorCodeConstants.AI_TASK_INPUT_INVALID, () -> handler.execute(context));
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, capturedUpdate().getStatus());
    }

    @Test
    void execute_unknownKind_throwsParseFailed() {
        judge("{\"kind\":\"do_everything\"}");

        assertCode(ErrorCodeConstants.ASSISTANT_PARSE_FAILED, () -> handler.execute(context));
        verify(modelClient, never()).streamChat(any(), any());
    }

    // ---------- 辅助 ----------

    private void judge(String json) {
        when(modelClient.chat(any(AiChatRequest.class))).thenReturn(new AiChatReply(json, 7, 3));
    }

    private void assertJudgeRequest() {
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(modelClient).chat(captor.capture());
        assertNull(captor.getValue().taskId());
        assertEquals("assistant_parse", captor.getValue().promptScene());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> artifacts(TaskResult result) {
        return (List<Map<String, Object>>) result.result().get("artifacts");
    }

    private AiAssistantMessage capturedUpdate() {
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        return captor.getValue();
    }

    private void assertCode(ErrorCode expected, Runnable invocation) {
        ServiceException exception = assertThrows(ServiceException.class, invocation::run);
        assertEquals(expected.code(), exception.getCode());
    }

    private void assertInputInvalid(Runnable invocation) {
        assertCode(ErrorCodeConstants.AI_TASK_INPUT_INVALID, invocation);
    }

    private static Map<String, Object> input() {
        return Map.of("messageId", MESSAGE_ID.toString());
    }

    private static TaskSubmitContext submitContext() {
        return new TaskSubmitContext(null, WORKSPACE_ID, USER_ID, null);
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
        conversation.setStatus(Constants.Status.ACTIVE);
        return conversation;
    }

    private static List<AiAssistantMessage> history() {
        AiAssistantMessage user = new AiAssistantMessage();
        user.setId(UUID.randomUUID());
        user.setRole(Constants.AiAssistantMessageRole.USER);
        user.setContent("帮我在商城项目里新建登录用例");
        user.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        AiAssistantMessage placeholder = assistantMessage();
        placeholder.setCreatedAt(LocalDateTime.now());
        return List.of(placeholder, user);
    }

    private static AiVectorSearchHitRespDTO hit(String type, UUID id, String content) {
        AiVectorSearchHitRespDTO hit = new AiVectorSearchHitRespDTO();
        hit.setProjectId(PROJECT_ID);
        hit.setEntityType(type);
        hit.setEntityId(id);
        hit.setContent(content);
        return hit;
    }
}
