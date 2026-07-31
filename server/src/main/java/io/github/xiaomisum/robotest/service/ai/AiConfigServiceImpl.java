package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.audit.AuditOperation;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConfigRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AiConfigServiceImpl implements AiConfigService {

    /** settings 缺省键的代码内置默认值（文档 2.2 全量键清单） */
    static final Map<String, Object> SETTING_DEFAULTS;

    static {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("rateLimit.generation", 20);
        defaults.put("rateLimit.suggestion", 60);
        defaults.put("rateLimit.retrieval", 120);
        defaults.put("rateLimit.task", 10);
        defaults.put("rateLimit.assistant", 60);
        defaults.put("dedup.topK", 5);
        defaults.put("dedup.similarityThreshold", 0.75);
        defaults.put("clustering.similarityThreshold", 0.82);
        defaults.put("clustering.maxLabeledClusters", 30);
        defaults.put("importTextMaxLength", 20000);
        defaults.put("requirementContentMaxLength", 20000);
        defaults.put("missingPoint.topK", 100);
        defaults.put("regression.topK", 50);
        defaults.put("regression.similarityThreshold", 0.7);
        defaults.put("planOrder.weights", Map.of("w1", 0.5, "w2", 0.3, "w3", 0.2));
        defaults.put("assistantConfirmTimeoutSeconds", 300);
        defaults.put("assistantWriteToolWhitelist", List.of("create_bug", "create_plan_draft"));
        defaults.put("logRetentionDays", 180);
        defaults.put("conversationRetentionDays", 180);
        SETTING_DEFAULTS = Map.copyOf(defaults);
    }

    private static final long CACHE_TTL_MILLIS = 30_000L;
    private static final int EMBEDDING_DIMENSION_MAX = 2000;

    @Resource
    private AiConfigMapper aiConfigMapper;
    @Resource
    private AiAnalysisTaskMapper aiAnalysisTaskMapper;
    @Resource
    private ProviderPresetRegistry presetRegistry;

    @Value("${robotest.ai.secret-key:}")
    private String secretKeyBase64;

    private volatile CacheEntry<AiConfig> configCache;
    private volatile CacheEntry<AiStatusRespDTO> statusCache;

    @Override
    public AiConfigRespDTO getConfig() {
        AiConfig config = aiConfigMapper.findActive();
        return config == null ? null : toRespDTO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "AiConfig", logParams = false)
    public AiConfigRespDTO saveConfig(AiConfigSaveReqDTO reqDTO) {
        byte[] secretKey = AiCryptoUtil.parseKey(secretKeyBase64);
        if (secretKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NOT_ENABLED);
        }

        AiConfigSaveReqDTO.ChatGroup chat = reqDTO.getChat();
        if (!presetRegistry.supports(chat.getProvider(), ProviderPresetRegistry.SCOPE_CHAT)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        Map<String, Object> chatExtraParams = presetRegistry.validateAndExpand(
                chat.getProvider(), ProviderPresetRegistry.SCOPE_CHAT, chat.getExtraParams());

        AiConfigSaveReqDTO.EmbeddingGroup embedding = reqDTO.getEmbedding();
        boolean embeddingPresent = !isEmbeddingGroupEmpty(embedding);
        Map<String, Object> embeddingExtraParams = Map.of();
        if (embeddingPresent) {
            validateEmbeddingGroup(embedding);
            embeddingExtraParams = presetRegistry.validateAndExpand(
                    embedding.getProvider(), ProviderPresetRegistry.SCOPE_EMBEDDING, embedding.getExtraParams());
        }

        AiConfig existing = aiConfigMapper.findActive();
        // 密钥齐全性：新密钥或已有密文至少其一（组内一旦填写则必须齐全）
        if (!StringUtils.hasText(chat.getApiKey())
                && (existing == null || !StringUtils.hasText(existing.getChatApiKeyCipher()))) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        if (embeddingPresent && !StringUtils.hasText(embedding.getApiKey())
                && (existing == null || !StringUtils.hasText(existing.getEmbeddingApiKeyCipher()))) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        AiConfig config = new AiConfig();
        config.setEnabled(reqDTO.getEnabled());
        config.setChatProvider(chat.getProvider());
        config.setChatBaseUrl(chat.getBaseUrl());
        config.setChatModel(chat.getModel());
        config.setChatExtraParams(chatExtraParams);
        if (StringUtils.hasText(chat.getApiKey())) {
            config.setChatApiKeyCipher(AiCryptoUtil.encrypt(secretKey, chat.getApiKey()));
            config.setChatKeySuffix(AiCryptoUtil.keySuffix(chat.getApiKey()));
        } else {
            config.setChatApiKeyCipher(existing.getChatApiKeyCipher());
            config.setChatKeySuffix(existing.getChatKeySuffix());
        }
        if (embeddingPresent) {
            config.setEmbeddingProvider(embedding.getProvider());
            config.setEmbeddingBaseUrl(embedding.getBaseUrl());
            config.setEmbeddingModel(embedding.getModel());
            config.setEmbeddingDimension(embedding.getDimension());
            config.setEmbeddingExtraParams(embeddingExtraParams);
            if (StringUtils.hasText(embedding.getApiKey())) {
                config.setEmbeddingApiKeyCipher(AiCryptoUtil.encrypt(secretKey, embedding.getApiKey()));
                config.setEmbeddingKeySuffix(AiCryptoUtil.keySuffix(embedding.getApiKey()));
            } else {
                config.setEmbeddingApiKeyCipher(existing.getEmbeddingApiKeyCipher());
                config.setEmbeddingKeySuffix(existing.getEmbeddingKeySuffix());
            }
        }
        config.setSettings(reqDTO.getSettings() != null ? reqDTO.getSettings()
                : existing != null && existing.getSettings() != null ? existing.getSettings() : Map.of());

        if (existing == null) {
            aiConfigMapper.insert(config);
        } else {
            updateWithOptimisticLock(existing, config,
                    reqDTO.getExpectedUpdatedAt() != null ? reqDTO.getExpectedUpdatedAt() : existing.getUpdatedAt());
        }
        invalidateCache();
        return getConfig();
    }

    /**
     * Embedding 模型或维度是否发生变更（触发向量重建的判定口径，仅 provider/独有配置项变更不算）
     */
    public boolean isEmbeddingChanged(AiConfig before, AiConfig after) {
        if (after.getEmbeddingModel() == null) {
            return false;
        }
        return before == null
                || !Objects.equals(before.getEmbeddingModel(), after.getEmbeddingModel())
                || !Objects.equals(before.getEmbeddingDimension(), after.getEmbeddingDimension());
    }

    @Override
    public AiStatusRespDTO getStatus() {
        CacheEntry<AiStatusRespDTO> cached = statusCache;
        if (cached != null && !cached.expired()) {
            return cached.value();
        }
        AiStatusRespDTO status = computeStatus();
        statusCache = new CacheEntry<>(status, System.currentTimeMillis() + CACHE_TTL_MILLIS);
        return status;
    }

    @Override
    public Map<String, Object> getMergedSettings() {
        Map<String, Object> merged = new LinkedHashMap<>(SETTING_DEFAULTS);
        AiConfig config = loadCachedConfig();
        if (config != null && config.getSettings() != null) {
            config.getSettings().forEach((key, value) -> {
                if (value != null) {
                    merged.put(key, value);
                }
            });
        }
        return merged;
    }

    @Override
    public int getIntSetting(String key) {
        Object value = getMergedSettings().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        Object fallback = SETTING_DEFAULTS.get(key);
        return fallback instanceof Number number ? number.intValue() : 0;
    }

    @Override
    public double getNumberSetting(String key) {
        Object value = getMergedSettings().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        Object fallback = SETTING_DEFAULTS.get(key);
        return fallback instanceof Number number ? number.doubleValue() : 0;
    }

    @Override
    public ResolvedAiConfig getResolvedConfig() {
        byte[] secretKey = AiCryptoUtil.parseKey(secretKeyBase64);
        if (secretKey == null) {
            return null;
        }
        AiConfig config = loadCachedConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        String chatApiKey = AiCryptoUtil.decrypt(secretKey, config.getChatApiKeyCipher());
        if (chatApiKey == null) {
            // 密钥轮换/密文损坏：按未启用降级
            return null;
        }
        String embeddingApiKey = StringUtils.hasText(config.getEmbeddingApiKeyCipher())
                ? AiCryptoUtil.decrypt(secretKey, config.getEmbeddingApiKeyCipher())
                : null;
        return new ResolvedAiConfig(
                config.getChatProvider(), config.getChatBaseUrl(), chatApiKey, config.getChatModel(),
                config.getChatExtraParams() != null ? config.getChatExtraParams() : Map.of(),
                config.getEmbeddingProvider(), config.getEmbeddingBaseUrl(), embeddingApiKey,
                config.getEmbeddingModel(), config.getEmbeddingDimension(),
                config.getEmbeddingExtraParams() != null ? config.getEmbeddingExtraParams() : Map.of());
    }

    private AiStatusRespDTO computeStatus() {
        AiStatusRespDTO status = new AiStatusRespDTO();
        ResolvedAiConfig resolved = getResolvedConfig();
        if (resolved == null) {
            status.setEnabled(false);
            return status;
        }
        status.setEnabled(true);
        if (!resolved.embeddingConfigured()) {
            status.setSemanticSearch(Constants.AiSemanticSearch.UNAVAILABLE);
            return status;
        }
        AiAnalysisTask latestRebuild = aiAnalysisTaskMapper.findLatestByType(Constants.AiTaskType.EMBEDDING_REBUILD);
        if (latestRebuild != null && Set.of(Constants.AiTaskStatus.PENDING, Constants.AiTaskStatus.RUNNING,
                Constants.AiTaskStatus.FAILED, Constants.AiTaskStatus.CANCELLED).contains(latestRebuild.getStatus())) {
            status.setSemanticSearch(Constants.AiSemanticSearch.DEGRADED);
        } else {
            status.setSemanticSearch(Constants.AiSemanticSearch.AVAILABLE);
        }
        return status;
    }

    private void validateEmbeddingGroup(AiConfigSaveReqDTO.EmbeddingGroup embedding) {
        if (!presetRegistry.supports(embedding.getProvider(), ProviderPresetRegistry.SCOPE_EMBEDDING)
                || !StringUtils.hasText(embedding.getBaseUrl())
                || !StringUtils.hasText(embedding.getModel())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        if (embedding.getDimension() == null
                || embedding.getDimension() < 1 || embedding.getDimension() > EMBEDDING_DIMENSION_MAX) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_DIMENSION_INVALID);
        }
    }

    private boolean isEmbeddingGroupEmpty(AiConfigSaveReqDTO.EmbeddingGroup embedding) {
        return embedding == null
                || (!StringUtils.hasText(embedding.getProvider())
                && !StringUtils.hasText(embedding.getBaseUrl())
                && !StringUtils.hasText(embedding.getModel())
                && embedding.getDimension() == null
                && !StringUtils.hasText(embedding.getApiKey()));
    }

    /**
     * 单行表全列覆盖更新：清空 Embedding 组等置空场景 updateById 会忽略 null 字段，须显式 set
     */
    private void updateWithOptimisticLock(AiConfig existing, AiConfig config, LocalDateTime expectedUpdatedAt) {
        int rows = aiConfigMapper.update(null, new LambdaUpdateWrapperX<AiConfig>()
                .eq(AiConfig::getId, existing.getId())
                .eq(AiConfig::getUpdatedAt, expectedUpdatedAt)
                .set(AiConfig::getEnabled, config.getEnabled())
                .set(AiConfig::getChatProvider, config.getChatProvider())
                .set(AiConfig::getChatBaseUrl, config.getChatBaseUrl())
                .set(AiConfig::getChatModel, config.getChatModel())
                .set(AiConfig::getChatApiKeyCipher, config.getChatApiKeyCipher())
                .set(AiConfig::getChatKeySuffix, config.getChatKeySuffix())
                .set(AiConfig::getChatExtraParams, toJson(config.getChatExtraParams()))
                .set(AiConfig::getEmbeddingProvider, config.getEmbeddingProvider())
                .set(AiConfig::getEmbeddingBaseUrl, config.getEmbeddingBaseUrl())
                .set(AiConfig::getEmbeddingModel, config.getEmbeddingModel())
                .set(AiConfig::getEmbeddingDimension, config.getEmbeddingDimension())
                .set(AiConfig::getEmbeddingApiKeyCipher, config.getEmbeddingApiKeyCipher())
                .set(AiConfig::getEmbeddingKeySuffix, config.getEmbeddingKeySuffix())
                .set(AiConfig::getEmbeddingExtraParams, toJson(config.getEmbeddingExtraParams()))
                .set(AiConfig::getSettings, toJson(config.getSettings()))
                .set(AiConfig::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_CONFIG_CONFLICT);
        }
    }

    private String toJson(Map<String, Object> map) {
        return xyz.migoo.framework.common.util.JsonUtils.toJsonString(map != null ? map : Map.of());
    }

    private AiConfig loadCachedConfig() {
        CacheEntry<AiConfig> cached = configCache;
        if (cached != null && !cached.expired()) {
            return cached.value();
        }
        AiConfig config = aiConfigMapper.findActive();
        configCache = new CacheEntry<>(config, System.currentTimeMillis() + CACHE_TTL_MILLIS);
        return config;
    }

    private void invalidateCache() {
        configCache = null;
        statusCache = null;
    }

    private AiConfigRespDTO toRespDTO(AiConfig config) {
        AiConfigRespDTO dto = new AiConfigRespDTO();
        dto.setEnabled(config.getEnabled());
        dto.setUpdatedAt(config.getUpdatedAt());

        AiConfigRespDTO.ChatGroup chat = new AiConfigRespDTO.ChatGroup();
        chat.setProvider(config.getChatProvider());
        chat.setBaseUrl(config.getChatBaseUrl());
        chat.setModel(config.getChatModel());
        chat.setExtraParams(config.getChatExtraParams() != null ? config.getChatExtraParams() : Map.of());
        chat.setApiKey(toApiKeyInfo(config.getChatApiKeyCipher(), config.getChatKeySuffix()));
        dto.setChat(chat);

        if (StringUtils.hasText(config.getEmbeddingBaseUrl())) {
            AiConfigRespDTO.EmbeddingGroup embedding = new AiConfigRespDTO.EmbeddingGroup();
            embedding.setProvider(config.getEmbeddingProvider());
            embedding.setBaseUrl(config.getEmbeddingBaseUrl());
            embedding.setModel(config.getEmbeddingModel());
            embedding.setDimension(config.getEmbeddingDimension());
            embedding.setExtraParams(config.getEmbeddingExtraParams() != null ? config.getEmbeddingExtraParams() : Map.of());
            embedding.setApiKey(toApiKeyInfo(config.getEmbeddingApiKeyCipher(), config.getEmbeddingKeySuffix()));
            dto.setEmbedding(embedding);
        }

        Map<String, Object> merged = new LinkedHashMap<>(SETTING_DEFAULTS);
        if (config.getSettings() != null) {
            config.getSettings().forEach((key, value) -> {
                if (value != null) {
                    merged.put(key, value);
                }
            });
        }
        dto.setSettings(merged);
        return dto;
    }

    private AiConfigRespDTO.ApiKeyInfo toApiKeyInfo(String cipher, String suffix) {
        AiConfigRespDTO.ApiKeyInfo info = new AiConfigRespDTO.ApiKeyInfo();
        info.setConfigured(StringUtils.hasText(cipher));
        info.setKeySuffix(suffix);
        return info;
    }

    private record CacheEntry<T>(T value, long expireAt) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
