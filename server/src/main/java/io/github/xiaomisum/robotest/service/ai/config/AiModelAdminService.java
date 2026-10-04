package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.audit.AuditLogWriter;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiModelCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiModelUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiModelListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiModelRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiModelTestRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiModelConfigMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 模型配置管理（详设 3.3）：CRUD + 连通性测试；密钥 AES 加密落库、读取永不回显。
 */
@Component
public class AiModelAdminService {

    private static final Set<String> SUPPORTED_CAPABILITIES = Set.of("chat", "vision", "embedding");
    private static final int TEST_MSG_MAX_LENGTH = 300;

    private static final String OPERATION_CREATE = "create";
    private static final String OPERATION_UPDATE = "update";
    private static final String OPERATION_DELETE = "delete";
    private static final String OPERATION_TEST = "test";
    private static final String ENTITY_TYPE_AI_MODEL = "ai_model";

    private static final String TEST_SYSTEM_PROMPT = "你是连通性探测助手。";
    private static final String TEST_USER_PROMPT = "请回复 ok";

    @Resource
    private AiModelConfigMapper modelMapper;
    @Resource
    private AiConfigMapper configMapper;
    @Resource
    private AiModelClient modelClient;
    @Resource
    private AuditLogWriter auditLogWriter;
    @Resource
    private ClientIpResolver clientIpResolver;

    private final byte[] secretKey;

    @Autowired
    public AiModelAdminService(@Value("${robotest.env.secret-key:}") String base64SecretKey) {
        this(SecretCryptoUtil.parseKey(base64SecretKey));
    }

    /** 测试构造：直接注入已解析密钥 */
    AiModelAdminService(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    /** 列表（3.3）：全量配置行，密钥仅回 keyConfigured */
    public AiModelListRespDTO list() {
        List<AiModelConfig> rows = modelMapper.selectList(
                new LambdaQueryWrapperX<AiModelConfig>().orderByAsc(AiModelConfig::getPriority));
        List<AiModelRespDTO> list = rows.stream().map(this::toResp).toList();
        AiModelListRespDTO dto = new AiModelListRespDTO();
        dto.setList(list);
        dto.setTotal((long) list.size());
        return dto;
    }

    /** 创建（3.3）：name 唯一、URL 合法、能力标签受控 */
    @Transactional(rollbackFor = Exception.class)
    public AiModelRespDTO create(AiModelCreateReqDTO req, LoginUser loginUser, HttpServletRequest request) {
        validateName(req.getName(), null);
        validateBaseUrl(req.getBaseUrl());
        validateCapabilities(req.getCapabilities());

        AiModelConfig row = new AiModelConfig();
        row.setName(req.getName());
        row.setProvider(req.getProvider());
        row.setBaseUrl(req.getBaseUrl());
        row.setApiKeyEncrypted(encrypt(req.getApiKey()));
        row.setModelName(req.getModelName());
        row.setCapabilities(req.getCapabilities());
        row.setPriority(req.getPriority() == null ? 100 : req.getPriority());
        row.setEnabled(req.getEnabled() == null || req.getEnabled());
        row.setInputPrice(req.getInputPrice());
        row.setOutputPrice(req.getOutputPrice());
        modelMapper.insert(row);

        writeAudit(OPERATION_CREATE, row.getId(), loginUser, request, Map.of("name", req.getName()));
        return toResp(row);
    }

    /** 更新（3.3，部分更新）：apiKey 传入即替换；默认模型不可删除或停用（1000018104） */
    @Transactional(rollbackFor = Exception.class)
    public AiModelRespDTO update(UUID modelId, AiModelUpdateReqDTO req, LoginUser loginUser,
            HttpServletRequest request) {
        AiModelConfig existing = modelMapper.selectById(modelId);
        if (existing == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_NOT_FOUND);
        }
        if (req.getName() != null) {
            validateName(req.getName(), modelId);
        }
        if (req.getBaseUrl() != null) {
            validateBaseUrl(req.getBaseUrl());
        }
        if (req.getCapabilities() != null) {
            validateCapabilities(req.getCapabilities());
        }

        LambdaUpdateWrapper<AiModelConfig> wrapper = new LambdaUpdateWrapperX<AiModelConfig>()
                .set(req.getName() != null, AiModelConfig::getName, req.getName())
                .set(req.getProvider() != null, AiModelConfig::getProvider, req.getProvider())
                .set(req.getBaseUrl() != null, AiModelConfig::getBaseUrl, req.getBaseUrl())
                .set(req.getModelName() != null, AiModelConfig::getModelName, req.getModelName())
                .set(req.getCapabilities() != null, AiModelConfig::getCapabilities, req.getCapabilities())
                .set(req.getPriority() != null, AiModelConfig::getPriority, req.getPriority())
                .set(req.getEnabled() != null, AiModelConfig::getEnabled, req.getEnabled())
                .set(req.getInputPrice() != null, AiModelConfig::getInputPrice, req.getInputPrice())
                .set(req.getOutputPrice() != null, AiModelConfig::getOutputPrice, req.getOutputPrice());
        if (req.getApiKey() != null) {
            wrapper.set(AiModelConfig::getApiKeyEncrypted, encrypt(req.getApiKey()));
        }

        // 停用 / 删除被引用的默认模型属于 1000018104 冲突（3.3 删除语义同样适用停用）
        if (Boolean.FALSE.equals(req.getEnabled())) {
            assertNotDefault(modelId);
        }
        modelMapper.update(null, wrapper);

        writeAudit(OPERATION_UPDATE, modelId, loginUser, request, changedFields(req));
        AiModelConfig updated = modelMapper.selectById(modelId);
        return toResp(updated);
    }

    /** 删除（3.3，逻辑删除）：被 default_model_id 引用时拒绝（1000018104） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID modelId, LoginUser loginUser, HttpServletRequest request) {
        AiModelConfig existing = modelMapper.selectById(modelId);
        if (existing == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_NOT_FOUND);
        }
        assertNotDefault(modelId);
        modelMapper.deleteById(modelId);
        writeAudit(OPERATION_DELETE, modelId, loginUser, request, Map.of("name", existing.getName()));
    }

    /**
     * 连通性测试（3.3）：以该配置发起最小 chat 调用（计入 ai_usage_log、task_id = NULL）。
     * 无论成败都回写 last_test_*（交互 2.4：失败悬浮原因，刷新后仍显示），失败抛 1000018105。
     */
    public AiModelTestRespDTO test(UUID modelId, LoginUser loginUser, HttpServletRequest request) {
        AiModelConfig model = modelMapper.selectById(modelId);
        if (model == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_NOT_FOUND);
        }

        long start = System.currentTimeMillis();
        try {
            AiChatReply reply = modelClient.chat(new AiChatRequest(null, null, loginUser.getId(), null,
                    model, TEST_SYSTEM_PROMPT, TEST_USER_PROMPT));
            int latencyMs = (int) (System.currentTimeMillis() - start);
            saveLastTest(model, true, latencyMs, null);
            writeAudit(OPERATION_TEST, modelId, loginUser, request, Map.of("success", true));
            AiModelTestRespDTO resp = new AiModelTestRespDTO();
            resp.setSuccess(true);
            resp.setLatencyMs(latencyMs);
            resp.setMsg("ok (" + reply.tokensIn() + " tokens)");
            return resp;
        } catch (ServiceException e) {
            int latencyMs = (int) (System.currentTimeMillis() - start);
            String msg = truncate(e.getMessage());
            saveLastTest(model, false, latencyMs, msg);
            writeAudit(OPERATION_TEST, modelId, loginUser, request, Map.of("success", false, "msg", msg));
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_TEST_FAILED.code(), msg);
        }
    }

    // ========== 私有 ==========

    private void saveLastTest(AiModelConfig model, boolean success, int latencyMs, String msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("latencyMs", latencyMs);
        if (msg != null) {
            result.put("msg", msg);
        }
        LocalDateTime at = LocalDateTime.now();
        modelMapper.update(null, new LambdaUpdateWrapperX<AiModelConfig>()
                .eq(AiModelConfig::getId, model.getId())
                .set(AiModelConfig::getLastTestAt, at)
                .set(AiModelConfig::getLastTestResult, result));
    }

    private void validateName(String name, UUID selfId) {
        AiModelConfig conflict = modelMapper.selectOne(new LambdaQueryWrapperX<AiModelConfig>()
                .eq(AiModelConfig::getName, name));
        if (conflict != null && !conflict.getId().equals(selfId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_INVALID.code(), "配置名称已存在");
        }
    }

    private void validateBaseUrl(String baseUrl) {
        try {
            URI uri = new URI(baseUrl);
            if (uri.getScheme() == null
                    || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                throw new URISyntaxException(baseUrl, "非 http(s) 合法地址");
            }
        } catch (URISyntaxException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_INVALID.code(), "端点地址非法：" + baseUrl);
        }
    }

    private void validateCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_INVALID.code(), "能力标签不能为空");
        }
        for (String capability : capabilities) {
            if (!SUPPORTED_CAPABILITIES.contains(capability)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_INVALID.code(), "不支持的能力标签：" + capability);
            }
        }
    }

    private void assertNotDefault(UUID modelId) {
        AiConfig config = configMapper.selectSingleton();
        if (config != null && modelId.equals(config.getDefaultModelId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_DEFAULT_MODEL_CONFLICT);
        }
    }

    private String encrypt(String plainKey) {
        if (secretKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_INVALID.code(), "服务端密钥未配置，无法加密存储");
        }
        return SecretCryptoUtil.encrypt(secretKey, plainKey);
    }

    private AiModelRespDTO toResp(AiModelConfig row) {
        AiModelRespDTO dto = new AiModelRespDTO();
        dto.setId(row.getId());
        dto.setName(row.getName());
        dto.setProvider(row.getProvider());
        dto.setBaseUrl(row.getBaseUrl());
        dto.setModelName(row.getModelName());
        dto.setCapabilities(row.getCapabilities());
        dto.setPriority(row.getPriority());
        dto.setEnabled(row.getEnabled());
        dto.setInputPrice(row.getInputPrice());
        dto.setOutputPrice(row.getOutputPrice());
        dto.setKeyConfigured(row.getApiKeyEncrypted() != null && !row.getApiKeyEncrypted().isBlank());
        if (row.getLastTestAt() != null) {
            AiModelRespDTO.LastTest lastTest = new AiModelRespDTO.LastTest();
            lastTest.setAt(row.getLastTestAt());
            Map<String, Object> result = row.getLastTestResult();
            if (result != null) {
                lastTest.setSuccess(Boolean.TRUE.equals(result.get("success")));
                Object latency = result.get("latencyMs");
                if (latency instanceof Number number) {
                    lastTest.setLatencyMs(number.intValue());
                }
                Object msg = result.get("msg");
                if (msg instanceof String text) {
                    lastTest.setMsg(text);
                }
            }
            dto.setLastTest(lastTest);
        }
        return dto;
    }

    private Map<String, Object> changedFields(AiModelUpdateReqDTO req) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (req.getName() != null) {
            changes.put("name", req.getName());
        }
        if (req.getBaseUrl() != null) {
            changes.put("baseUrl", req.getBaseUrl());
        }
        if (req.getEnabled() != null) {
            changes.put("enabled", req.getEnabled());
        }
        if (req.getInputPrice() != null) {
            changes.put("inputPrice", req.getInputPrice());
        }
        if (req.getOutputPrice() != null) {
            changes.put("outputPrice", req.getOutputPrice());
        }
        if (req.getApiKey() != null) {
            changes.put("apiKey", "***");
        }
        return changes;
    }

    private void writeAudit(String operation, UUID entityId, LoginUser loginUser, HttpServletRequest request,
            Map<String, Object> changes) {
        AuditLog record = new AuditLog();
        record.setOperation(operation);
        record.setEntityType(ENTITY_TYPE_AI_MODEL);
        record.setEntityId(entityId);
        record.setOperatorId(loginUser.getId());
        record.setOperatorName(loginUser.getUsername());
        record.setRequestIp(clientIpResolver.resolve(request));
        record.setChanges(changes == null ? Map.of() : changes);
        auditLogWriter.write(record);
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= TEST_MSG_MAX_LENGTH ? text : text.substring(0, TEST_MSG_MAX_LENGTH);
    }
}
