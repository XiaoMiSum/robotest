package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiUsageLog;
import io.github.xiaomisum.robotest.repository.ai.AiUsageLogMapper;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantToolDefinition;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.ToolCategory;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.ToolSchemas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import xyz.migoo.framework.common.exception.ServiceException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 模型客户端门面（WP-4.6 Spring AI 内核）：断言按行配置映射、Prompt 构造、
 * 记账字段与 1000018117 错误码映射（详设 4.2 / 4.3）。
 */
@ExtendWith(MockitoExtension.class)
class AiModelClientTest {

    private static final byte[] SECRET_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String BASE64_KEY = Base64.getEncoder().encodeToString(SECRET_KEY);

    @Mock
    private AiUsageLogMapper usageLogMapper;
    @Mock
    private AiSettingsReader settingsReader;
    @Mock
    private ChatModel chatModel;

    private AiModelClient client;
    private AiModelConfig model;

    @BeforeEach
    void setUp() {
        client = new AiModelClient(BASE64_KEY);
        ReflectionTestUtils.setField(client, "usageLogMapper", usageLogMapper);
        ReflectionTestUtils.setField(client, "settingsReader", settingsReader);
        model = new AiModelConfig();
        model.setId(UUID.randomUUID());
        model.setBaseUrl("https://api.deepseek.com/v1");
        model.setModelName("deepseek-chat");
        model.setApiKeyEncrypted(SecretCryptoUtil.encrypt(SECRET_KEY, "sk-test"));
        model.setInputPrice(new BigDecimal("0.5"));
        model.setOutputPrice(new BigDecimal("2.0"));
    }

    private AiChatRequest request() {
        return new AiChatRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "test",
                model, "你是测试助手", "生成用例");
    }

    @Test
    void buildOptions_mapsRowConfigAndTaskRetries() {
        OpenAiChatOptions options = AiModelClient.buildOptions(model, "sk-test", 2);

        assertEquals("https://api.deepseek.com/v1", options.getBaseUrl());
        assertEquals("deepseek-chat", options.getModel());
        assertEquals("sk-test", options.getApiKey());
        assertEquals(2, options.getMaxRetries());
        assertEquals(Duration.ofSeconds(120), options.getTimeout());
    }

    @Test
    void buildChatModel_consumesTaskMaxRetriesFromSettings() {
        when(settingsReader.settings())
                .thenReturn(new AiSettingsReader.AiSettings(true, model.getId(), 120, 3));

        assertNotNull(client.buildChatModel(request(), "sk-test"));
    }

    @Test
    void buildPrompt_withMedia_buildsMultimodalUserMessage() {
        AiChatRequest request = new AiChatRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "test",
                model, "你是测试助手", "识别图片中的需求", new AiChatMedia("image/png", new byte[]{1, 2, 3}));

        Prompt prompt = AiModelClient.buildPrompt(request);

        assertEquals(2, prompt.getInstructions().size());
        UserMessage user = prompt.getUserMessage();
        assertEquals(1, user.getMedia().size());
        assertEquals("image/png", user.getMedia().get(0).getMimeType().toString());
        assertArrayEquals(new byte[]{1, 2, 3}, user.getMedia().get(0).getDataAsByteArray());
    }

    @Test
    void buildPrompt_withoutMedia_plainTextUserMessage() {
        Prompt prompt = AiModelClient.buildPrompt(request());

        UserMessage user = prompt.getUserMessage();
        assertEquals("生成用例", user.getText());
        assertTrue(user.getMedia().isEmpty());
    }

    @Test
    void chat_success_returnsReplyAndWritesSingleUsageRow() {
        AiModelClient spy = Mockito.spy(client);
        doReturn(chatModel).when(spy).buildChatModel(any(), any());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("生成完毕"))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(1000, 200)).build()));

        AiChatReply reply = spy.chat(request());

        assertEquals("生成完毕", reply.content());
        assertEquals(1000, reply.tokensIn());
        assertEquals(200, reply.tokensOut());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        List<Message> messages = promptCaptor.getValue().getInstructions();
        assertEquals(2, messages.size());
        assertEquals("你是测试助手", messages.get(0).getText());
        assertEquals("生成用例", messages.get(1).getText());

        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        AiUsageLog usage = captor.getValue();
        assertEquals("chat", usage.getCallType());
        assertEquals("success", usage.getStatus());
        assertEquals(1000, usage.getPromptTokens().intValue());
        assertEquals(200, usage.getCompletionTokens().intValue());
        assertEquals(1200, usage.getTotalTokens().intValue());
        assertNull(usage.getErrorCode());
        assertNotNull(usage.getLatencyMs());
        // 成本 = 1000/1e6 × 0.5 + 200/1e6 × 2.0 = 0.0009（4.3 计价公式）
        assertEquals(0, usage.getCost().compareTo(new BigDecimal("0.0009")));
    }

    @Test
    void chat_withMedia_recordsVisionCallType() {
        AiModelClient spy = Mockito.spy(client);
        doReturn(chatModel).when(spy).buildChatModel(any(), any());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("识别完成"))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(500, 50)).build()));
        AiChatRequest multimodal = new AiChatRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "requirement_import", model, "你是需求分析助手", "识别图片", new AiChatMedia("image/png",
                        new byte[]{1, 2, 3}));

        spy.chat(multimodal);

        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("vision", captor.getValue().getCallType());
        assertEquals("success", captor.getValue().getStatus());
    }

    @Test
    void chat_callFailure_mapsTo18117AndWritesFailedUsageRow() {
        AiModelClient spy = Mockito.spy(client);
        doReturn(chatModel).when(spy).buildChatModel(any(), any());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("rate limited"));

        ServiceException e = assertThrows(ServiceException.class, () -> spy.chat(request()));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), captor.getValue().getErrorCode());
        assertEquals(0, captor.getValue().getPromptTokens().intValue());
    }

    @Test
    void chat_malformedResponse_mapsTo18117AndWritesFailedUsageRow() {
        AiModelClient spy = Mockito.spy(client);
        doReturn(chatModel).when(spy).buildChatModel(any(), any());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(), ChatResponseMetadata.builder().build()));

        ServiceException e = assertThrows(ServiceException.class, () -> spy.chat(request()));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), captor.getValue().getErrorCode());
    }

    @Test
    void chat_undecryptableKey_throwsWithoutCallOrUsageRow() {
        model.setApiKeyEncrypted("not-a-cipher");

        ServiceException e = assertThrows(ServiceException.class, () -> client.chat(request()));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        verify(usageLogMapper, never()).insert(any(AiUsageLog.class));
        verifyNoInteractions(settingsReader);
    }

    // ---------- 流式（助手详设 3.5 delta 通道） ----------

    @Test
    void streamChat_success_accumulatesDeltasWritesUsageAndStreamsOnDelta() {
        AiModelClient spy = Mockito.spy(client);
        doReturn(chatModel).when(spy).buildChatModel(any(), any());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                chunk("根据"), chunk("检索来源")));
        List<String> deltas = new ArrayList<>();

        AiChatReply reply = spy.streamChat(request(), deltas::add);

        // 聚合正文 + 逐段回调 + 账单 token（以响应为准）
        assertEquals("根据检索来源", reply.content());
        assertEquals(List.of("根据", "检索来源"), deltas);
        assertEquals(600, reply.tokensIn());
        assertEquals(30, reply.tokensOut());

        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("success", captor.getValue().getStatus());
        assertEquals(600, captor.getValue().getPromptTokens().intValue());
        assertEquals(30, captor.getValue().getCompletionTokens().intValue());
    }

    @Test
    void streamChat_failure_mapsTo18117AndWritesFailedUsageRow() {
        AiModelClient spy = Mockito.spy(client);
        doReturn(chatModel).when(spy).buildChatModel(any(), any());
        when(chatModel.stream(any(Prompt.class))).thenThrow(new RuntimeException("stream broke"));

        ServiceException e = assertThrows(ServiceException.class,
                () -> spy.streamChat(request(), delta -> { }));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), captor.getValue().getErrorCode());
    }

    @Test
    void streamChat_undecryptableKey_throwsWithoutStreamOrUsageRow() {
        model.setApiKeyEncrypted("not-a-cipher");

        ServiceException e = assertThrows(ServiceException.class,
                () -> client.streamChat(request(), delta -> { }));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        verify(chatModel, never()).stream(any(Prompt.class));
        verify(usageLogMapper, never()).insert(any(AiUsageLog.class));
    }

    // ---------- 带工具声明的单跳（助手详设 4.1 工具调用循环） ----------

    @Test
    void buildOptions_withTools_declaresCallbackSchemasAndNeverExecutes() {
        OpenAiChatOptions options = AiModelClient.buildOptions(model, "sk-test", 2,
                List.of(queryProjectsDef()));

        assertEquals(1, options.getToolCallbacks().size());
        ToolCallback callback = options.getToolCallbacks().get(0);
        assertEquals("query_projects", callback.getToolDefinition().name());
        assertTrue(callback.getToolDefinition().inputSchema().contains("keyword"));
        // 声明型回调无执行体：触达即缺陷（执行权在服务端循环）
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> callback.call("{}"));
        assertTrue(error.getMessage().contains("query_projects"));
    }

    @Test
    void buildOptions_withoutTools_declaresNothing() {
        OpenAiChatOptions options = AiModelClient.buildOptions(model, "sk-test", 2);

        assertTrue(options.getToolCallbacks() == null || options.getToolCallbacks().isEmpty());
    }

    @Test
    void chatWithTools_returnsToolCallsAndScenesByOutcome() {
        AiModelClient spy = Mockito.spy(client);
        doReturn(chatModel).when(spy).buildChatModel(any(), any(), any());
        AssistantMessage output = AssistantMessage.builder()
                .content("先查一下项目")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "tc-1", "function", "query_projects", "{\"keyword\":\"商城\"}")))
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(output)),
                ChatResponseMetadata.builder().usage(new DefaultUsage(800, 40)).build()));

        AiChatReply reply = spy.chatWithTools(request(), List.of(queryProjectsDef()));

        assertEquals("先查一下项目", reply.content());
        assertEquals(1, reply.toolCalls().size());
        AiToolCall call = reply.toolCalls().get(0);
        assertEquals("tc-1", call.id());
        assertEquals("query_projects", call.name());
        assertEquals("{\"keyword\":\"商城\"}", call.arguments());
        // 有工具调用 → 归因请求场景（assistant_parse 侧）
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("test", captor.getValue().getPromptScene());
        assertEquals(800, captor.getValue().getPromptTokens().intValue());
    }

    @Test
    void chatWithTools_textOnlyHop_scenesAsAssistantAnswer() {
        AiModelClient spy = Mockito.spy(client);
        doReturn(chatModel).when(spy).buildChatModel(any(), any(), any());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("最终回答"))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, 10)).build()));

        AiChatReply reply = spy.chatWithTools(request(), List.of(queryProjectsDef()));

        assertTrue(reply.toolCalls().isEmpty());
        assertEquals("最终回答", reply.content());
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("assistant_answer", captor.getValue().getPromptScene());
    }

    @Test
    void chatWithTools_failure_maps18117AndWritesFailedUsageRow() {
        AiModelClient spy = Mockito.spy(client);
        doReturn(chatModel).when(spy).buildChatModel(any(), any(), any());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

        ServiceException e = assertThrows(ServiceException.class,
                () -> spy.chatWithTools(request(), List.of(queryProjectsDef())));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("test", captor.getValue().getPromptScene());
    }

    private static AssistantToolDefinition queryProjectsDef() {
        return new AssistantToolDefinition("query_projects", "查询项目",
                ToolSchemas.object(List.of(ToolSchemas.string("keyword", "关键词")), null),
                ToolCategory.QUERY);
    }

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(600, 30)).build());
    }
}
