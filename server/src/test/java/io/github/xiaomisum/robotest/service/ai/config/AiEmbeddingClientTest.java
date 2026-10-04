package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiUsageLog;
import io.github.xiaomisum.robotest.repository.ai.AiUsageLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.migoo.framework.common.exception.ServiceException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 向量客户端门面（WP-4.6 Spring AI 内核）：断言按行配置映射（maxRetries=0 单次语义）、
 * 严格模式数量 / 维度校验与 embedding 记账口径（model_id NULL、cost 0，详设 4.3）。
 */
@ExtendWith(MockitoExtension.class)
class AiEmbeddingClientTest {

    private static final byte[] SECRET_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String BASE64_KEY = Base64.getEncoder().encodeToString(SECRET_KEY);

    @Mock
    private AiUsageLogMapper usageLogMapper;
    @Mock
    private EmbeddingModel embeddingModel;

    private AiEmbeddingClient client;
    private AiEmbeddingConfig config;

    @BeforeEach
    void setUp() {
        client = new AiEmbeddingClient(BASE64_KEY);
        ReflectionTestUtils.setField(client, "usageLogMapper", usageLogMapper);
        config = new AiEmbeddingConfig();
        config.setBaseUrl("https://api.openai.com/v1");
        config.setEmbeddingModel("text-embedding-3-small");
        config.setApiKeyEncrypted(SecretCryptoUtil.encrypt(SECRET_KEY, "sk-test"));
        config.setDimensions(3);
    }

    private AiEmbeddingClient spyClient() {
        AiEmbeddingClient spy = Mockito.spy(client);
        doReturn(embeddingModel).when(spy).buildEmbeddingModel(any());
        return spy;
    }

    private static EmbeddingResponse responseOf(float[]... vectors) {
        List<Embedding> results = new ArrayList<>();
        for (int i = 0; i < vectors.length; i++) {
            results.add(new Embedding(vectors[i], i));
        }
        return new EmbeddingResponse(results,
                new EmbeddingResponseMetadata("text-embedding-3-small", new DefaultUsage(7, 0)));
    }

    @Test
    void buildOptions_rowConfig_keepsSingleAttemptSemantics() {
        OpenAiEmbeddingOptions options = AiEmbeddingClient.buildOptions(config, "sk-test");

        assertEquals("https://api.openai.com/v1", options.getBaseUrl());
        assertEquals("text-embedding-3-small", options.getModel());
        assertEquals(0, options.getMaxRetries());
        assertEquals(Duration.ofSeconds(60), options.getTimeout());
    }

    @Test
    void embed_success_returnsVectorAndWritesUsageRow() {
        AiEmbeddingClient spy = spyClient();
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(responseOf(new float[]{0.1f, 0.2f, 0.3f}));

        AiEmbeddingClient.EmbeddingReply reply = spy.embed(config, List.of("你好"),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertEquals(3, reply.dimensions());
        assertEquals(7, reply.tokens());
        assertEquals(0.1, reply.embedding().getFirst(), 1e-6);

        ArgumentCaptor<EmbeddingRequest> requestCaptor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingModel).call(requestCaptor.capture());
        assertEquals(List.of("你好"), requestCaptor.getValue().getInstructions());

        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        AiUsageLog usage = captor.getValue();
        assertEquals("embedding", usage.getCallType());
        assertEquals("success", usage.getStatus());
        assertEquals(7, usage.getPromptTokens().intValue());
        assertNull(usage.getModelId());
        assertEquals(0, usage.getCost().compareTo(BigDecimal.ZERO));
    }

    @Test
    void embedBatch_success_preservesInputOrderAndCount() {
        AiEmbeddingClient spy = spyClient();
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(responseOf(
                new float[]{0.1f, 0.2f, 0.3f}, new float[]{0.4f, 0.5f, 0.6f}));

        AiEmbeddingClient.EmbeddingsReply reply = spy.embedBatch(config, List.of("甲", "乙"), null, null, null);

        assertEquals(2, reply.vectors().size());
        assertEquals(0.4, reply.vectors().get(1).getFirst(), 1e-6);
        assertEquals(7, reply.tokens());
    }

    @Test
    void embedBatch_strict_countMismatch_throwsWithFailedUsageRow() {
        AiEmbeddingClient spy = spyClient();
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(responseOf(new float[]{0.1f, 0.2f, 0.3f}));

        ServiceException e = assertThrows(ServiceException.class,
                () -> spy.embedBatch(config, List.of("甲", "乙"), null, null, null));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), captor.getValue().getErrorCode());
    }

    @Test
    void embedBatch_strict_dimensionMismatch_throwsWithFailedUsageRow() {
        AiEmbeddingClient spy = spyClient();
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(responseOf(new float[]{0.1f, 0.2f}));

        ServiceException e = assertThrows(ServiceException.class,
                () -> spy.embedBatch(config, List.of("甲"), null, null, null));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), captor.getValue().getErrorCode());
    }

    @Test
    void embed_callFailure_mapsTo18117AndWritesFailedUsageRow() {
        AiEmbeddingClient spy = spyClient();
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenThrow(new RuntimeException("connect timed out"));

        ServiceException e = assertThrows(ServiceException.class,
                () -> spy.embed(config, List.of("你好"), null, null, null));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), captor.getValue().getErrorCode());
    }

    @Test
    void embed_undecryptableKey_throwsWithoutCallOrUsageRow() {
        config.setApiKeyEncrypted("not-a-cipher");

        ServiceException e = assertThrows(ServiceException.class,
                () -> client.embed(config, List.of("你好"), null, null, null));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        verify(usageLogMapper, never()).insert(any(AiUsageLog.class));
        verifyNoInteractions(embeddingModel);
    }
}
