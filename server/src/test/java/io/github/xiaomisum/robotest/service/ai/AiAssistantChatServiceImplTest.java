package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantSendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiMessage;
import io.github.xiaomisum.robotest.repository.ai.AiConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiMessageMapper;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ToolCall;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ToolStreamCallbacks;
import io.github.xiaomisum.robotest.service.ai.assistant.AiConfirmTokenService;
import io.github.xiaomisum.robotest.service.ai.assistant.AiTool;
import io.github.xiaomisum.robotest.service.ai.assistant.AiToolDefinition;
import io.github.xiaomisum.robotest.service.ai.assistant.AiToolExecutor;
import io.github.xiaomisum.robotest.service.ai.assistant.ToolRegistry;
import io.github.xiaomisum.robotest.service.ai.assistant.ToolSchema;
import io.github.xiaomisum.robotest.service.ai.assistant.WriteToolExecutor;
import io.github.xiaomisum.robotest.service.ai.provider.OpenAiCompatProvider;
import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedAiConfig;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantChatServiceImplTest {

    @Mock
    private AiSseSupport sseSupport;

    @BeforeEach
    void stubSseSupport() {
        // 虚拟线程内正常路径会 stopPing()，ping 句柄需为可取消 mock 而非 null
        lenient().when(sseSupport.open()).thenReturn(new AiSseSupport.Channel(
                new SseEmitter(), new AtomicBoolean(false), mock(ScheduledFuture.class)));
    }

    @Mock
    private AiConversationMapper conversationMapper;
    @Mock
    private AiMessageMapper messageMapper;
    @Mock
    private AiConversationService conversationService;
    @Mock
    private AiChatModelService aiChatModelService;
    @Mock
    private AiConfigService aiConfigService;
    @Mock
    private AiRateLimiter rateLimiter;
    @Mock
    private AiAuditRecorder auditRecorder;
    @Mock
    private OpenAiCompatProvider provider;
    @Mock
    private PromptAssembler promptAssembler;
    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private AiToolExecutor aiToolExecutor;
    @Mock
    private AiConfirmTokenService confirmTokenService;
    @Mock
    private WriteToolExecutor writeToolExecutor;

    @InjectMocks
    private AiAssistantChatServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();

    // ======================== sendMessage ========================

    @Test
    void sendMessage_pureTextReplyAppendsAssistantMessage() {
        ResolvedChatModel model = stubThreadReachable();
        doAnswer(inv -> {
            ToolStreamCallbacks cb = inv.getArgument(3);
            cb.onToolCalls(List.of());
            cb.onFinish("你好", 10, 5);
            return null;
        }).when(provider).streamWithTools(any(), anyList(), any(), any(), any());

        SseEmitter emitter = service.sendMessage(userId, workspaceId, conversationId, req("你好"));

        assertNotNull(emitter);
        verify(conversationService).appendUserMessage(conversationId, "你好");
        verify(conversationService, timeout(3000)).appendAssistantMessage(
                eq(conversationId), eq("你好"), isNull());
        verify(auditRecorder, timeout(3000)).record(
                any(), eq(AiFunctionType.ASSISTANT_CHAT), eq(model.model()), anyLong(),
                eq(10), eq(5), eq(Constants.AiInvocationStatus.SUCCESS), isNull());
        verify(provider, timeout(3000)).streamWithTools(eq(model), anyList(), any(), any(), any());
    }

    @Test
    void sendMessage_readOnlyToolExecutedThenLoopReplies() {
        ResolvedChatModel model = stubThreadReachable();
        AiTool tool = mockReadOnlyTool("query_bugs");
        when(toolRegistry.get("query_bugs")).thenReturn(tool);
        when(aiToolExecutor.execute(any(), eq("query_bugs"), any())).thenReturn("{\"rows\":[]}");
        AiMessage toolMsg = toolMessage("call_1", "{\"rows\":[]}");
        when(messageMapper.selectByConversationId(conversationId)).thenReturn(List.of(toolMsg));

        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            ToolStreamCallbacks cb = inv.getArgument(3);
            if (calls.incrementAndGet() == 1) {
                cb.onToolCalls(List.of(new ToolCall("call_1", "query_bugs", Map.of())));
                cb.onFinish("", 10, 5);
            } else {
                cb.onToolCalls(List.of());
                cb.onFinish("完成", 3, 2);
            }
            return null;
        }).when(provider).streamWithTools(any(), anyList(), any(), any(), any());

        service.sendMessage(userId, workspaceId, conversationId, req("查一下缺陷"));

        verify(aiToolExecutor, timeout(3000)).execute(any(), eq("query_bugs"), any());
        verify(conversationService, timeout(3000)).appendToolMessage(
                eq(conversationId), eq("call_1"), eq("{\"rows\":[]}"));
        verify(provider, timeout(3000).times(2)).streamWithTools(eq(model), anyList(), any(), any(), any());
        verify(conversationService, timeout(3000)).appendAssistantMessage(
                eq(conversationId), eq("完成"), isNull());
    }

    @Test
    void sendMessage_writeToolIssuesConfirmTokenAndStops() {
        stubThreadReachable();
        AiTool writeTool = mock(AiTool.class);
        when(writeTool.definition()).thenReturn(
                new AiToolDefinition("create_bug", "创建缺陷", ToolSchema.object(List.of(), List.of()),
                        false, null));
        when(toolRegistry.get("create_bug")).thenReturn(writeTool);
        when(confirmTokenService.issue(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("token-abc");
        when(aiConfigService.getIntSetting("assistantConfirmTimeoutSeconds")).thenReturn(300);
        doAnswer(inv -> {
            ToolStreamCallbacks cb = inv.getArgument(3);
            cb.onToolCalls(List.of(new ToolCall("call_2", "create_bug", Map.of("title", "缺陷"))));
            cb.onFinish("", 1, 1);
            return null;
        }).when(provider).streamWithTools(any(), anyList(), any(), any(), any());

        service.sendMessage(userId, workspaceId, conversationId, req("帮我创建缺陷"));

        verify(confirmTokenService, timeout(3000)).issue(
                eq(userId), eq(workspaceId), eq(conversationId), any(),
                eq("call_2"), eq("create_bug"), any());
        verify(aiToolExecutor, never()).execute(any(), any(), any());
        verify(writeToolExecutor, never()).execute(any(), any(), any());
        verify(provider, timeout(3000)).streamWithTools(any(), anyList(), any(), any(), any());
    }

    @Test
    void sendMessage_unknownToolAppendsErrorToolMessage() {
        stubThreadReachable();
        when(toolRegistry.get("nope_tool")).thenReturn(null);

        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            ToolStreamCallbacks cb = inv.getArgument(3);
            if (calls.incrementAndGet() == 1) {
                cb.onToolCalls(List.of(new ToolCall("call_3", "nope_tool", Map.of())));
                cb.onFinish("", 1, 1);
            } else {
                cb.onToolCalls(List.of());
                cb.onFinish("完成", 2, 2);
            }
            return null;
        }).when(provider).streamWithTools(any(), anyList(), any(), any(), any());

        service.sendMessage(userId, workspaceId, conversationId, req("调用不存在的工具"));

        verify(conversationService, timeout(3000)).appendToolMessage(
                eq(conversationId), eq("call_3"), eq("{\"error\":\"未知工具: nope_tool\"}"));
        verify(aiToolExecutor, never()).execute(any(), any(), any());
        verify(provider, timeout(3000).times(2)).streamWithTools(any(), anyList(), any(), any(), any());
    }

    @Test
    void sendMessage_throwsWhenConversationNotOwned() {
        when(conversationMapper.selectOwned(userId, workspaceId, conversationId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> service.sendMessage(userId, workspaceId, conversationId, req("你好")));
        verify(conversationService, never()).appendUserMessage(any(), any());
    }

    @Test
    void sendMessage_aiDisabledSendsErrorAndSkipsStream() {
        when(conversationMapper.selectOwned(userId, workspaceId, conversationId))
                .thenReturn(ownedConversation());
        when(aiConfigService.getResolvedConfig()).thenReturn(resolvedConfig());
        when(aiChatModelService.resolve(isNull())).thenReturn(chatModel());
        AiStatusRespDTO status = new AiStatusRespDTO();
        status.setEnabled(false);
        when(aiConfigService.getStatus()).thenReturn(status);
        when(conversationService.appendAssistantMessage(any(), any(), any())).thenReturn(messageId);

        service.sendMessage(userId, workspaceId, conversationId, req("你好"));

        verify(provider, never()).streamWithTools(any(), anyList(), any(), any(), any());
        verify(conversationService, timeout(3000)).appendAssistantMessage(
                eq(conversationId), eq("已达到工具调用上限，请简化请求后重试。"), isNull());
    }

    @Test
    void sendMessage_serviceExceptionInStreamRecordsAuditFailure() {
        when(conversationMapper.selectOwned(userId, workspaceId, conversationId))
                .thenReturn(ownedConversation());
        when(aiConfigService.getResolvedConfig()).thenReturn(resolvedConfig());
        ResolvedChatModel model = chatModel();
        when(aiChatModelService.resolve(isNull())).thenReturn(model);
        AiStatusRespDTO status = new AiStatusRespDTO();
        status.setEnabled(true);
        when(aiConfigService.getStatus()).thenReturn(status);
        doThrow(new ServiceException(ErrorCodeConstants.AI_CALL_FAILED.code(), "AI 调用失败"))
                .when(provider).streamWithTools(any(), anyList(), any(), any(), any());

        service.sendMessage(userId, workspaceId, conversationId, req("你好"));

        verify(auditRecorder, timeout(3000)).record(
                any(), eq(AiFunctionType.ASSISTANT_CHAT), eq(model.model()), eq(0L),
                isNull(), isNull(), eq(Constants.AiInvocationStatus.FAILED), eq("6002"));
        verify(conversationService, never()).appendAssistantMessage(any(), any(), any());
    }

    // ======================== cancel ========================

    @Test
    void cancel_appendsCancelledToolMessage() {
        AiConfirmTokenService.ConfirmPayload payload = payload("call_9", "create_bug");
        when(confirmTokenService.requireValid("token", userId, workspaceId)).thenReturn(payload);

        service.cancel(userId, workspaceId, "token");

        verify(conversationService).appendToolMessage(
                conversationId, "call_9", "{\"cancelled\":true,\"message\":\"用户已取消该操作\"}");
    }

    @Test
    void cancel_throwsWhenTokenInvalid() {
        when(confirmTokenService.requireValid("bad", userId, workspaceId))
                .thenThrow(new ServiceException(
                        ErrorCodeConstants.AI_ASSISTANT_CONFIRM_TOKEN_INVALID.code(), "令牌失效"));

        assertThrows(ServiceException.class,
                () -> service.cancel(userId, workspaceId, "bad"));
        verify(conversationService, never()).appendToolMessage(any(), any(), any());
    }

    // ======================== approve ========================

    @Test
    void approve_executesWriteToolAndStreamsFinalReply() {
        AiConfirmTokenService.ConfirmPayload payload = payload("call_9", "create_bug");
        when(confirmTokenService.requireValid("token", userId, workspaceId)).thenReturn(payload);
        when(writeToolExecutor.execute(any(), eq("create_bug"), any())).thenReturn("{\"id\":\"bug-1\"}");
        when(conversationMapper.selectById(conversationId)).thenReturn(ownedConversation());
        when(aiConfigService.getResolvedConfig()).thenReturn(resolvedConfig());
        ResolvedChatModel model = chatModel();
        when(aiChatModelService.resolve(isNull())).thenReturn(model);
        when(conversationService.appendAssistantMessage(any(), any(), any())).thenReturn(messageId);
        doAnswer(inv -> {
            ToolStreamCallbacks cb = inv.getArgument(3);
            cb.onToolCalls(List.of());
            cb.onFinish("已创建缺陷", 4, 4);
            return null;
        }).when(provider).streamWithTools(any(), anyList(), any(), any(), any());

        SseEmitter emitter = service.approve(userId, workspaceId, "token");

        assertNotNull(emitter);
        verify(writeToolExecutor).execute(any(), eq("create_bug"), any());
        verify(conversationService).appendToolMessage(conversationId, "call_9", "{\"id\":\"bug-1\"}");
        verify(conversationService, timeout(3000)).appendAssistantMessage(
                eq(conversationId), eq("已创建缺陷"), isNull());
        verify(auditRecorder, timeout(3000)).record(
                any(), eq(AiFunctionType.ASSISTANT_CHAT), eq(model.model()), anyLong(),
                eq(4), eq(4), eq(Constants.AiInvocationStatus.SUCCESS), isNull());
    }

    @Test
    void approve_throwsWhenTokenInvalid() {
        when(confirmTokenService.requireValid("bad", userId, workspaceId))
                .thenThrow(new ServiceException(
                        ErrorCodeConstants.AI_ASSISTANT_CONFIRM_TOKEN_INVALID.code(), "令牌失效"));

        assertThrows(ServiceException.class,
                () -> service.approve(userId, workspaceId, "bad"));
        verify(writeToolExecutor, never()).execute(any(), any(), any());
    }

    // ======================== helpers ========================

    private ResolvedChatModel stubThreadReachable() {
        when(conversationMapper.selectOwned(userId, workspaceId, conversationId))
                .thenReturn(ownedConversation());
        when(aiConfigService.getResolvedConfig()).thenReturn(resolvedConfig());
        ResolvedChatModel model = chatModel();
        when(aiChatModelService.resolve(isNull())).thenReturn(model);
        AiStatusRespDTO status = new AiStatusRespDTO();
        status.setEnabled(true);
        when(aiConfigService.getStatus()).thenReturn(status);
        when(conversationService.appendAssistantMessage(any(), any(), any())).thenReturn(messageId);
        return model;
    }

    private AiConversation ownedConversation() {
        AiConversation conversation = new AiConversation();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setWorkspaceId(workspaceId);
        return conversation;
    }

    private ResolvedChatModel chatModel() {
        return new ResolvedChatModel(UUID.randomUUID(), "gpt", "openai",
                "http://localhost", "key", "gpt-4o", Map.of());
    }

    private ResolvedAiConfig resolvedConfig() {
        return new ResolvedAiConfig("openai", "http://localhost", "key",
                "text-embedding-3-small", 1536, Map.of());
    }

    private AiAssistantSendReqDTO req(String content) {
        AiAssistantSendReqDTO dto = new AiAssistantSendReqDTO();
        dto.setContent(content);
        return dto;
    }

    private AiTool mockReadOnlyTool(String name) {
        AiTool tool = org.mockito.Mockito.mock(AiTool.class);
        when(tool.definition()).thenReturn(
                new AiToolDefinition(name, "只读工具", ToolSchema.object(List.of(), List.of()), true, null));
        return tool;
    }

    private AiMessage toolMessage(String toolCallId, String content) {
        AiMessage msg = new AiMessage();
        msg.setConversationId(conversationId);
        msg.setRole(AiMessage.ROLE_TOOL);
        msg.setToolCallId(toolCallId);
        msg.setContent(content);
        return msg;
    }

    private AiConfirmTokenService.ConfirmPayload payload(String toolCallId, String toolName) {
        return new AiConfirmTokenService.ConfirmPayload(
                userId, workspaceId, conversationId, messageId, toolCallId, toolName, Map.of());
    }
}
