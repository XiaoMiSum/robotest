package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.framework.audit.AuditOperation;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConfigRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConnectivityTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import io.github.xiaomisum.robotest.service.ai.model.AiModels;
import io.github.xiaomisum.robotest.service.ai.provider.OpenAiCompatProvider;
import io.github.xiaomisum.robotest.service.ai.provider.ProviderPresetRegistry;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedAiConfig;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;
import io.github.xiaomisum.robotest.service.ai.support.AiCacheEntry;
import io.github.xiaomisum.robotest.service.ai.support.AiCryptoUtil;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class AiConfigServiceImpl implements AiConfigService {

    private static final long CACHE_TTL_MILLIS = 30_000L;
    private static final int EMBEDDING_DIMENSION_MAX = 2000;

    @Resource
    private AiConfigMapper aiConfigMapper;
    @Resource
    private AiAnalysisTaskMapper aiAnalysisTaskMapper;
    @Resource
    private ProviderPresetRegistry presetRegistry;
    @Resource
    private OpenAiCompatProvider openAiCompatProvider;
    @Resource
    private ObjectProvider<AiTaskService> aiTaskServiceProvider;
    @Resource
    private AiChatModelService aiChatModelService;
    @Resource
    private AiSettingDefinitions settingDefinitions;

    @Value("${robotest.ai.secret-key:}")
    private String secretKeyBase64;

    private volatile AiCacheEntry<AiConfig> configCache;
    private volatile AiCacheEntry<AiStatusRespDTO> statusCache;

    @Override
    public AiConfigRespDTO getConfig() {
        AiConfig config = aiConfigMapper.findActive();
        return config == null ? null : toRespDTO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "AiConfig", logParams = false)
    public AiConfigRespDTO saveConfig(AiConfigSaveReqDTO reqDTO, UUID operatorId) {
        byte[] secretKey = AiCryptoUtil.parseKey(secretKeyBase64);
        if (secretKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NOT_ENABLED);
        }

        // 总开关开启前须存在至少一个已启用对话模型（3.3.2）
        if (Boolean.TRUE.equals(reqDTO.getEnabled()) && !aiChatModelService.hasEnabledModel()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        // 系统配置项逐项校验（类型/范围/权重之和=1，未知键拒绝）
        settingDefinitions.validate(reqDTO.getSettings());

        AiConfigSaveReqDTO.EmbeddingGroup embedding = reqDTO.getEmbedding();
        boolean embeddingPresent = !isEmbeddingGroupEmpty(embedding);
        Map<String, Object> embeddingExtraParams = Map.of();
        if (embeddingPresent) {
            validateEmbeddingGroup(embedding);
            embeddingExtraParams = presetRegistry.validateAndExpand(
                    embedding.getProvider(), ProviderPresetRegistry.SCOPE_EMBEDDING, embedding.getExtraParams());
        }

        AiConfig existing = aiConfigMapper.findActive();
        // Embedding 组一旦填写则密钥须齐全（新密钥或已有密文其一）
        if (embeddingPresent && !StringUtils.hasText(embedding.getApiKey())
                && (existing == null || !StringUtils.hasText(existing.getEmbeddingApiKeyCipher()))) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        AiConfig config = new AiConfig();
        config.setEnabled(reqDTO.getEnabled());
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
        // 仅持久化与内置默认值不同的键（缺省回退默认值语义，2.2）
        config.setSettings(reqDTO.getSettings() != null ? settingDefinitions.stripDefaults(reqDTO.getSettings())
                : existing != null && existing.getSettings() != null ? existing.getSettings() : Map.of());

        if (existing == null) {
            aiConfigMapper.insert(config);
        } else {
            updateConfig(existing, config);
        }
        invalidateCache();

        // 联动：总开关关闭 → 全部进行中任务置 cancelled（4.6）
        if (!Boolean.TRUE.equals(reqDTO.getEnabled())) {
            aiTaskServiceProvider.getObject().cancelAllInProgress();
        } else if (isEmbeddingChanged(existing, config)) {
            // 联动：Embedding 模型/维度变更 → 覆盖式创建全局重建任务并进入语义降级（4.10）
            aiTaskServiceProvider.getObject().createTask(
                    Constants.AiTaskType.EMBEDDING_REBUILD, null, null, null, operatorId);
        }
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
    public AiConnectivityTestRespDTO testConnectivity(AiConfigTestReqDTO reqDTO) {
        AiConnectivityTestRespDTO resp = new AiConnectivityTestRespDTO();
        long start = System.currentTimeMillis();
        try {
            if (ProviderPresetRegistry.SCOPE_EMBEDDING.equals(reqDTO.getTarget())) {
                testEmbedding(resolveEmbeddingForTest(reqDTO.getEmbedding()), resp);
            } else {
                testChat(aiChatModelService.resolveForTest(reqDTO.getModelId(), reqDTO.getChat()), resp);
            }
            resp.setOk(true);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            resp.setOk(false);
            resp.setDetail(truncateDetail(e.getMessage()));
        }
        resp.setLatencyMs(System.currentTimeMillis() - start);
        return resp;
    }

    private void testChat(ResolvedChatModel config, AiConnectivityTestRespDTO resp) {
        openAiCompatProvider.complete(config,
                List.of(AiModels.ChatMessage.user("ping")),
                new AiModels.ChatCallOptions(16, null, false, null));
        // 顺带探测 response_format 结构化参数支持情况，结果仅作提示不阻断保存
        boolean jsonSupported;
        try {
            openAiCompatProvider.complete(config,
                    List.of(AiModels.ChatMessage.user("回复 JSON 对象 {\"ok\":true}")),
                    new AiModels.ChatCallOptions(16, null, true, null));
            jsonSupported = true;
        } catch (Exception e) {
            jsonSupported = false;
        }
        resp.setDetail(jsonSupported ? "连通正常，支持结构化输出参数（response_format）"
                : "连通正常，未探测到结构化输出参数支持，结构化功能将依赖输出校验");
    }

    private void testEmbedding(ResolvedAiConfig config, AiConnectivityTestRespDTO resp) {
        AiModels.EmbedResult result = openAiCompatProvider.embed(config, List.of("connectivity test"));
        int actual = result.vectors().isEmpty() ? 0 : result.vectors().getFirst().length;
        if (actual != config.embeddingDimension()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_DIMENSION_INVALID);
        }
        resp.setDetail("连通正常，向量维度 " + actual + " 与配置一致");
    }

    /**
     * 测试目标配置：请求附带的临时 Embedding 配置优先，缺省字段回退已保存配置（密钥回退已存密文解密）
     */
    private ResolvedAiConfig resolveEmbeddingForTest(AiConfigSaveReqDTO.EmbeddingGroup override) {
        byte[] secretKey = AiCryptoUtil.parseKey(secretKeyBase64);
        AiConfig saved = aiConfigMapper.findActive();
        String baseUrl = override != null && StringUtils.hasText(override.getBaseUrl())
                ? override.getBaseUrl() : saved != null ? saved.getEmbeddingBaseUrl() : null;
        String model = override != null && StringUtils.hasText(override.getModel())
                ? override.getModel() : saved != null ? saved.getEmbeddingModel() : null;
        Integer dimension = override != null && override.getDimension() != null
                ? override.getDimension() : saved != null ? saved.getEmbeddingDimension() : null;
        Map<String, Object> extraParams = override != null && override.getExtraParams() != null
                ? override.getExtraParams()
                : saved != null && saved.getEmbeddingExtraParams() != null ? saved.getEmbeddingExtraParams() : Map.of();
        String apiKey = override != null && StringUtils.hasText(override.getApiKey()) ? override.getApiKey()
                : saved != null && secretKey != null && StringUtils.hasText(saved.getEmbeddingApiKeyCipher())
                ? AiCryptoUtil.decrypt(secretKey, saved.getEmbeddingApiKeyCipher()) : null;
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)
                || dimension == null || !StringUtils.hasText(apiKey)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        return new ResolvedAiConfig(
                saved != null ? saved.getEmbeddingProvider() : null, baseUrl, apiKey, model, dimension, extraParams);
    }

    private String truncateDetail(String detail) {
        return detail != null && detail.length() > 300 ? detail.substring(0, 300) : detail;
    }

    @Override
    public AiStatusRespDTO getStatus() {
        AiCacheEntry<AiStatusRespDTO> cached = statusCache;
        if (cached != null && !cached.expired()) {
            return cached.value();
        }
        AiStatusRespDTO status = computeStatus();
        statusCache = new AiCacheEntry<>(status, System.currentTimeMillis() + CACHE_TTL_MILLIS);
        return status;
    }

    @Override
    public Map<String, Object> getMergedSettings() {
        Map<String, Object> merged = new LinkedHashMap<>(settingDefinitions.defaults());
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
        Object fallback = settingDefinitions.defaults().get(key);
        return fallback instanceof Number number ? number.intValue() : 0;
    }

    @Override
    public double getNumberSetting(String key) {
        Object value = getMergedSettings().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        Object fallback = settingDefinitions.defaults().get(key);
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
        String embeddingApiKey = StringUtils.hasText(config.getEmbeddingApiKeyCipher())
                ? AiCryptoUtil.decrypt(secretKey, config.getEmbeddingApiKeyCipher())
                : null;
        return new ResolvedAiConfig(
                config.getEmbeddingProvider(), config.getEmbeddingBaseUrl(), embeddingApiKey,
                config.getEmbeddingModel(), config.getEmbeddingDimension(),
                config.getEmbeddingExtraParams() != null ? config.getEmbeddingExtraParams() : Map.of());
    }

    private AiStatusRespDTO computeStatus() {
        AiStatusRespDTO status = new AiStatusRespDTO();
        ResolvedAiConfig resolved = getResolvedConfig();
        // 总开关开启 + 密钥有效 + 至少一个已启用对话模型，三者齐备方视为可用（4.10）
        List<AiStatusRespDTO.ChatModelView> chatModels = aiChatModelService.listEnabledForStatus();
        if (resolved == null || chatModels.isEmpty()) {
            status.setEnabled(false);
            return status;
        }
        status.setEnabled(true);
        status.setChatModels(chatModels);
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
    private void updateConfig(AiConfig existing, AiConfig config) {
        aiConfigMapper.update(null, new LambdaUpdateWrapperX<AiConfig>()
                .eq(AiConfig::getId, existing.getId())
                .set(AiConfig::getEnabled, config.getEnabled())
                .set(AiConfig::getEmbeddingProvider, config.getEmbeddingProvider())
                .set(AiConfig::getEmbeddingBaseUrl, config.getEmbeddingBaseUrl())
                .set(AiConfig::getEmbeddingModel, config.getEmbeddingModel())
                .set(AiConfig::getEmbeddingDimension, config.getEmbeddingDimension())
                .set(AiConfig::getEmbeddingApiKeyCipher, config.getEmbeddingApiKeyCipher())
                .set(AiConfig::getEmbeddingKeySuffix, config.getEmbeddingKeySuffix())
                .set(AiConfig::getEmbeddingExtraParams, toJson(config.getEmbeddingExtraParams()))
                .set(AiConfig::getSettings, toJson(config.getSettings()))
                .set(AiConfig::getUpdatedAt, LocalDateTime.now()));
    }

    private String toJson(Map<String, Object> map) {
        return xyz.migoo.framework.common.util.JsonUtils.toJsonString(map != null ? map : Map.of());
    }

    private AiConfig loadCachedConfig() {
        AiCacheEntry<AiConfig> cached = configCache;
        if (cached != null && !cached.expired()) {
            return cached.value();
        }
        AiConfig config = aiConfigMapper.findActive();
        configCache = new AiCacheEntry<>(config, System.currentTimeMillis() + CACHE_TTL_MILLIS);
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

        Map<String, Object> merged = new LinkedHashMap<>(settingDefinitions.defaults());
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

}
