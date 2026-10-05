package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiUsageLog;
import io.github.xiaomisum.robotest.repository.ai.AiUsageLogMapper;
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
import org.springframework.test.util.ReflectionTestUtils;
import xyz.migoo.framework.common.exception.ServiceException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
}
