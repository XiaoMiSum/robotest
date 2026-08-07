package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.framework.audit.AuditOperation;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiChatModelSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiChatModelRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConfigRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiChatModel;
import io.github.xiaomisum.robotest.repository.ai.AiChatModelMapper;
import io.github.xiaomisum.robotest.service.ai.provider.ProviderPresetRegistry;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;
import io.github.xiaomisum.robotest.service.ai.support.AiCacheEntry;
import io.github.xiaomisum.robotest.service.ai.support.AiCryptoUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiChatModelServiceImpl implements AiChatModelService {

    private static final long CACHE_TTL_MILLIS = 30_000L;

    @Resource
    private AiChatModelMapper aiChatModelMapper;
    @Resource
    private ProviderPresetRegistry presetRegistry;

    @Value("${robotest.ai.secret-key:}")
    private String secretKeyBase64;

    /** 已启用模型快照缓存（运行期解析/状态/门控数据源，变更失效） */
    private volatile AiCacheEntry<List<AiChatModel>> enabledCache;

    @Override
    public List<AiChatModelRespDTO> list() {
        return aiChatModelMapper.findAllActive().stream().map(this::toRespDTO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "CREATE", entityType = "AiChatModel", logParams = false)
    public AiChatModelRespDTO create(AiChatModelSaveReqDTO reqDTO, UUID operatorId) {
        byte[] secretKey = requireSecretKey();
        validateProviderAndName(reqDTO, null);
        if (!StringUtils.hasText(reqDTO.getApiKey())) {
            // 新建无已存密文，密钥必填
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        Map<String, Object> extraParams = presetRegistry.validateAndExpand(
                reqDTO.getProvider(), ProviderPresetRegistry.SCOPE_CHAT, reqDTO.getExtraParams());

        AiChatModel model = new AiChatModel();
        model.setName(reqDTO.getName());
        model.setProvider(reqDTO.getProvider());
        model.setBaseUrl(reqDTO.getBaseUrl());
        model.setModel(reqDTO.getModel());
        model.setExtraParams(extraParams);
        model.setApiKeyCipher(AiCryptoUtil.encrypt(secretKey, reqDTO.getApiKey()));
        model.setKeySuffix(AiCryptoUtil.keySuffix(reqDTO.getApiKey()));
        model.setEnabled(true);
        // 首个创建的模型自动置默认，避免「有模型但无默认」的空窗（4.11）
        model.setIsDefault(aiChatModelMapper.findAllActive().isEmpty());
        model.setUpdatedBy(operatorId);
        aiChatModelMapper.insert(model);
        invalidateCache();
        return toRespDTO(aiChatModelMapper.findByIdActive(model.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "AiChatModel", logParams = false)
    public AiChatModelRespDTO update(UUID id, AiChatModelSaveReqDTO reqDTO, UUID operatorId) {
        byte[] secretKey = requireSecretKey();
        AiChatModel existing = aiChatModelMapper.findByIdActive(id);
        if (existing == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        validateProviderAndName(reqDTO, id);
        Map<String, Object> extraParams = presetRegistry.validateAndExpand(
                reqDTO.getProvider(), ProviderPresetRegistry.SCOPE_CHAT, reqDTO.getExtraParams());

        String cipher;
        String suffix;
        if (StringUtils.hasText(reqDTO.getApiKey())) {
            cipher = AiCryptoUtil.encrypt(secretKey, reqDTO.getApiKey());
            suffix = AiCryptoUtil.keySuffix(reqDTO.getApiKey());
        } else {
            cipher = existing.getApiKeyCipher();
            suffix = existing.getKeySuffix();
        }
        int rows = aiChatModelMapper.updateModelFields(id, reqDTO.getName(), reqDTO.getProvider(),
                reqDTO.getBaseUrl(), reqDTO.getModel(), cipher, suffix, toJson(extraParams), operatorId);
        invalidateCache();
        return toRespDTO(aiChatModelMapper.findByIdActive(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "DELETE", entityType = "AiChatModel", logParams = false)
    public void delete(UUID id, UUID operatorId) {
        AiChatModel existing = aiChatModelMapper.findByIdActive(id);
        if (existing == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        if (Boolean.TRUE.equals(existing.getIsDefault())) {
            // 默认模型不可删除，需先转移默认
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        aiChatModelMapper.deleteById(id);
        invalidateCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "AiChatModel", logParams = false)
    public void setDefault(UUID id, UUID operatorId) {
        AiChatModel existing = aiChatModelMapper.findByIdActive(id);
        if (existing == null || !Boolean.TRUE.equals(existing.getEnabled())) {
            // 不存在或停用状态不可设为默认
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        // 事务内先清除原默认，再置新默认（唯一默认保证，4.11）
        aiChatModelMapper.clearDefault();
        int rows = aiChatModelMapper.setDefaultById(id, operatorId);
        if (rows == 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        invalidateCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "AiChatModel", logParams = false)
    public void setEnabled(UUID id, boolean enabled, UUID operatorId) {
        AiChatModel existing = aiChatModelMapper.findByIdActive(id);
        if (existing == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        if (!enabled && Boolean.TRUE.equals(existing.getIsDefault())) {
            // 默认模型不可停用，需先转移默认
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        aiChatModelMapper.updateEnabledById(id, enabled, operatorId);
        invalidateCache();
    }

    @Override
    public ResolvedChatModel resolve(UUID modelId) {
        byte[] secretKey = AiCryptoUtil.parseKey(secretKeyBase64);
        if (secretKey == null) {
            return null;
        }
        List<AiChatModel> enabled = loadEnabledCached();
        AiChatModel row = null;
        if (modelId != null) {
            row = enabled.stream().filter(m -> modelId.equals(m.getId())).findFirst().orElse(null);
        }
        if (row == null) {
            // modelId 缺省或失效（不存在/已停用/已删除）静默回退系统默认（4.11）
            row = enabled.stream().filter(m -> Boolean.TRUE.equals(m.getIsDefault())).findFirst().orElse(null);
        }
        if (row == null) {
            return null;
        }
        String apiKey = AiCryptoUtil.decrypt(secretKey, row.getApiKeyCipher());
        if (apiKey == null) {
            return null;
        }
        return new ResolvedChatModel(row.getId(), row.getName(), row.getProvider(), row.getBaseUrl(),
                apiKey, row.getModel(), row.getExtraParams() != null ? row.getExtraParams() : Map.of());
    }

    @Override
    public ResolvedChatModel resolveForTest(UUID modelId, AiConfigTestReqDTO.ChatGroup override) {
        byte[] secretKey = AiCryptoUtil.parseKey(secretKeyBase64);
        AiChatModel saved = modelId != null ? aiChatModelMapper.findByIdActive(modelId) : null;
        String provider = override != null && StringUtils.hasText(override.getProvider())
                ? override.getProvider() : saved != null ? saved.getProvider() : null;
        String baseUrl = override != null && StringUtils.hasText(override.getBaseUrl())
                ? override.getBaseUrl() : saved != null ? saved.getBaseUrl() : null;
        String model = override != null && StringUtils.hasText(override.getModel())
                ? override.getModel() : saved != null ? saved.getModel() : null;
        Map<String, Object> extraParams = override != null && override.getExtraParams() != null
                ? override.getExtraParams()
                : saved != null && saved.getExtraParams() != null ? saved.getExtraParams() : Map.of();
        String apiKey = override != null && StringUtils.hasText(override.getApiKey())
                ? override.getApiKey()
                : saved != null && secretKey != null ? AiCryptoUtil.decrypt(secretKey, saved.getApiKeyCipher()) : null;
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model) || !StringUtils.hasText(apiKey)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        return new ResolvedChatModel(saved != null ? saved.getId() : null, saved != null ? saved.getName() : null,
                provider, baseUrl, apiKey, model, extraParams);
    }

    @Override
    public List<AiStatusRespDTO.ChatModelView> listEnabledForStatus() {
        return loadEnabledCached().stream()
                .map(m -> new AiStatusRespDTO.ChatModelView(m.getId().toString(), m.getName(), m.getIsDefault()))
                .toList();
    }

    @Override
    public boolean hasEnabledModel() {
        return !loadEnabledCached().isEmpty();
    }

    private void validateProviderAndName(AiChatModelSaveReqDTO reqDTO, UUID currentId) {
        if (!presetRegistry.supports(reqDTO.getProvider(), ProviderPresetRegistry.SCOPE_CHAT)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        AiChatModel byName = aiChatModelMapper.findByNameActive(reqDTO.getName());
        if (byName != null && !byName.getId().equals(currentId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
    }

    private byte[] requireSecretKey() {
        byte[] secretKey = AiCryptoUtil.parseKey(secretKeyBase64);
        if (secretKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NOT_ENABLED);
        }
        return secretKey;
    }

    private List<AiChatModel> loadEnabledCached() {
        AiCacheEntry<List<AiChatModel>> cached = enabledCache;
        if (cached != null && !cached.expired()) {
            return cached.value();
        }
        List<AiChatModel> list = aiChatModelMapper.findEnabledActive();
        enabledCache = new AiCacheEntry<>(list, System.currentTimeMillis() + CACHE_TTL_MILLIS);
        return list;
    }

    private void invalidateCache() {
        enabledCache = null;
    }

    private String toJson(Map<String, Object> map) {
        return JsonUtils.toJsonString(map != null ? map : Map.of());
    }

    private AiChatModelRespDTO toRespDTO(AiChatModel model) {
        AiChatModelRespDTO dto = new AiChatModelRespDTO();
        dto.setId(model.getId().toString());
        dto.setName(model.getName());
        dto.setProvider(model.getProvider());
        dto.setBaseUrl(model.getBaseUrl());
        dto.setModel(model.getModel());
        AiConfigRespDTO.ApiKeyInfo info = new AiConfigRespDTO.ApiKeyInfo();
        info.setConfigured(StringUtils.hasText(model.getApiKeyCipher()));
        info.setKeySuffix(model.getKeySuffix());
        dto.setApiKey(info);
        dto.setExtraParams(model.getExtraParams() != null ? model.getExtraParams() : Map.of());
        dto.setEnabled(model.getEnabled());
        dto.setIsDefault(model.getIsDefault());
        dto.setUpdatedBy(model.getUpdatedBy() != null ? model.getUpdatedBy().toString() : null);
        dto.setUpdatedAt(model.getUpdatedAt());
        return dto;
    }

}
