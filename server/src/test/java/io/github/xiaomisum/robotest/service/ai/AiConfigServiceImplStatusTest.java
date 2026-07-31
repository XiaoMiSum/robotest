package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConfigServiceImplStatusTest {

    private static final String KEY_BASE64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Mock
    private AiConfigMapper aiConfigMapper;
    @Mock
    private AiAnalysisTaskMapper aiAnalysisTaskMapper;

    @InjectMocks
    private AiConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "secretKeyBase64", KEY_BASE64);
    }

    private AiConfig enabledConfig(boolean withEmbedding) {
        AiConfig config = new AiConfig();
        config.setEnabled(true);
        config.setChatProvider("openai");
        config.setChatBaseUrl("https://api.openai.com/v1");
        config.setChatModel("gpt-4o");
        // 用真实密钥加密，getResolvedConfig 解密成功
        byte[] key = AiCryptoUtil.parseKey(KEY_BASE64);
        config.setChatApiKeyCipher(AiCryptoUtil.encrypt(key, "sk-test"));
        if (withEmbedding) {
            config.setEmbeddingBaseUrl("https://api.openai.com/v1");
            config.setEmbeddingModel("text-embedding-3-small");
            config.setEmbeddingDimension(1536);
            config.setEmbeddingApiKeyCipher(AiCryptoUtil.encrypt(key, "sk-embed"));
        }
        return config;
    }

    @Test
    void status_noConfigReturnsDisabled() {
        when(aiConfigMapper.findActive()).thenReturn(null);
        AiStatusRespDTO status = service.getStatus();
        assertFalse(status.getEnabled());
        assertNull(status.getSemanticSearch());
    }

    @Test
    void status_secretKeyMissingReturnsDisabled() {
        ReflectionTestUtils.setField(service, "secretKeyBase64", "");
        AiStatusRespDTO status = service.getStatus();
        assertFalse(status.getEnabled());
    }

    @Test
    void status_disabledFlagReturnsDisabled() {
        AiConfig config = enabledConfig(false);
        config.setEnabled(false);
        when(aiConfigMapper.findActive()).thenReturn(config);
        AiStatusRespDTO status = service.getStatus();
        assertFalse(status.getEnabled());
    }

    @Test
    void status_embeddingNotConfiguredUnavailable() {
        when(aiConfigMapper.findActive()).thenReturn(enabledConfig(false));
        AiStatusRespDTO status = service.getStatus();
        assertTrue(status.getEnabled());
        assertEquals(Constants.AiSemanticSearch.UNAVAILABLE, status.getSemanticSearch());
    }

    @Test
    void status_embeddingConfiguredNoRebuildAvailable() {
        when(aiConfigMapper.findActive()).thenReturn(enabledConfig(true));
        when(aiAnalysisTaskMapper.findLatestByType(Constants.AiTaskType.EMBEDDING_REBUILD)).thenReturn(null);
        AiStatusRespDTO status = service.getStatus();
        assertEquals(Constants.AiSemanticSearch.AVAILABLE, status.getSemanticSearch());
    }

    @Test
    void status_rebuildRunningDegraded() {
        when(aiConfigMapper.findActive()).thenReturn(enabledConfig(true));
        AiAnalysisTask rebuild = new AiAnalysisTask();
        rebuild.setStatus(Constants.AiTaskStatus.RUNNING);
        when(aiAnalysisTaskMapper.findLatestByType(Constants.AiTaskType.EMBEDDING_REBUILD)).thenReturn(rebuild);
        AiStatusRespDTO status = service.getStatus();
        assertEquals(Constants.AiSemanticSearch.DEGRADED, status.getSemanticSearch());
    }

    @Test
    void status_rebuildFailedDegraded() {
        when(aiConfigMapper.findActive()).thenReturn(enabledConfig(true));
        AiAnalysisTask rebuild = new AiAnalysisTask();
        rebuild.setStatus(Constants.AiTaskStatus.FAILED);
        when(aiAnalysisTaskMapper.findLatestByType(Constants.AiTaskType.EMBEDDING_REBUILD)).thenReturn(rebuild);
        // 向量数据不完整维持降级
        assertEquals(Constants.AiSemanticSearch.DEGRADED, service.getStatus().getSemanticSearch());
    }

    @Test
    void getMergedSettings_defaultsWhenNoOverride() {
        lenient().when(aiConfigMapper.findActive()).thenReturn(null);
        Map<String, Object> settings = service.getMergedSettings();
        assertEquals(100, service.getIntSetting("missingPoint.topK"));
        assertEquals(0.7, service.getNumberSetting("regression.similarityThreshold"));
        assertTrue(settings.containsKey("planOrder.weights"));
    }

    @Test
    void getIntSetting_overrideTakesEffect() {
        AiConfig config = enabledConfig(false);
        config.setSettings(Map.of("missingPoint.topK", 50));
        when(aiConfigMapper.findActive()).thenReturn(config);
        assertEquals(50, service.getIntSetting("missingPoint.topK"));
    }
}
