package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.audit.AuditLogWriter;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiEmbeddingSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiEmbeddingRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiEmbeddingTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiModelRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.repository.ai.AiEmbeddingConfigMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量 API 配置管理（详设 3.4，单例）：维度 / 算子变更时旧配置入 versions 并
 * 响应 requiresReindex（前端引导全量重建，4.4）。
 */
@Component
public class AiEmbeddingAdminService {

    private static final String OPERATION_SAVE = "save";
    private static final String OPERATION_TEST = "test";
    private static final String ENTITY_TYPE_AI_EMBEDDING = "ai_embedding_config";
    private static final int TEST_MSG_MAX_LENGTH = 300;

    @Resource
    private AiEmbeddingConfigMapper embeddingMapper;
    @Resource
    private AiEmbeddingClient embeddingClient;
    @Resource
    private AuditLogWriter auditLogWriter;
    @Resource
    private ClientIpResolver clientIpResolver;

    private final byte[] secretKey;

    @Autowired
    public AiEmbeddingAdminService(@Value("${robotest.env.secret-key:}") String base64SecretKey) {
        this(SecretCryptoUtil.parseKey(base64SecretKey));
    }

    /** 测试构造：直接注入已解析密钥 */
    AiEmbeddingAdminService(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    /** 查询（3.4）：行缺失时返回未配置默认（enabled = false，其余为空） */
    public AiEmbeddingRespDTO get() {
        AiEmbeddingConfig row = embeddingMapper.selectSingleton();
        AiEmbeddingRespDTO dto = new AiEmbeddingRespDTO();
        if (row == null) {
            dto.setProvider(null);
            dto.setBaseUrl(null);
            dto.setEmbeddingModel(null);
            dto.setDimensions(null);
            dto.setOperator(null);
            dto.setIndexType(null);
            dto.setEnabled(false);
            dto.setKeyConfigured(false);
            dto.setVersions(new ArrayList<>());
            dto.setRequiresReindex(false);
            dto.setLastTest(null);
            return dto;
        }
        dto.setProvider(row.getProvider());
        dto.setBaseUrl(row.getBaseUrl());
        dto.setEmbeddingModel(row.getEmbeddingModel());
        dto.setDimensions(row.getDimensions());
        dto.setOperator(row.getOperator());
        dto.setIndexType(row.getIndexType());
        dto.setEnabled(row.getEnabled());
        dto.setKeyConfigured(row.getApiKeyEncrypted() != null && !row.getApiKeyEncrypted().isBlank());
        List<Map<String, Object>> versions = row.getVersions() == null ? new ArrayList<>() : row.getVersions();
        dto.setVersions(versions);
        // versions 非空即存在未消费的维度 / 算子变更（2.4，重建完成前检索不可用）
        dto.setRequiresReindex(!versions.isEmpty());
        dto.setLastTest(toLastTest(row));
        return dto;
    }

    /** 保存（3.4，单例创建或部分更新）：dimensions / operator 相对原值变化 → 旧配置入 versions */
    @Transactional(rollbackFor = Exception.class)
    public AiEmbeddingRespDTO save(AiEmbeddingSaveReqDTO req, LoginUser loginUser, HttpServletRequest request) {
        if (req.getBaseUrl() != null) {
            validateBaseUrl(req.getBaseUrl());
        }
        AiEmbeddingConfig row = embeddingMapper.selectSingleton();
        boolean requiresReindex = false;

        if (row == null) {
            row = new AiEmbeddingConfig();
            if (req.getProvider() == null || req.getBaseUrl() == null || req.getApiKey() == null
                    || req.getEmbeddingModel() == null || req.getDimensions() == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_INVALID.code(),
                        "首次保存需完整配置：供应商、端点、密钥、嵌入模型与维度");
            }
            row.setProvider(req.getProvider());
            row.setBaseUrl(req.getBaseUrl());
            row.setApiKeyEncrypted(encrypt(req.getApiKey()));
            row.setEmbeddingModel(req.getEmbeddingModel());
            row.setDimensions(req.getDimensions());
            row.setOperator(req.getOperator() == null ? "cosine" : req.getOperator());
            row.setIndexType("hnsw");
            row.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
            row.setVersions(new ArrayList<>());
            embeddingMapper.insert(row);
        } else {
            boolean dimensionChanged = req.getDimensions() != null
                    && !req.getDimensions().equals(row.getDimensions());
            boolean operatorChanged = req.getOperator() != null
                    && !req.getOperator().equals(row.getOperator());
            if (dimensionChanged || operatorChanged) {
                requiresReindex = retireCurrentVersion(row);
            }

            LambdaUpdateWrapper<AiEmbeddingConfig> wrapper = new LambdaUpdateWrapperX<AiEmbeddingConfig>()
                    .set(req.getProvider() != null, AiEmbeddingConfig::getProvider, req.getProvider())
                    .set(req.getBaseUrl() != null, AiEmbeddingConfig::getBaseUrl, req.getBaseUrl())
                    .set(req.getEmbeddingModel() != null, AiEmbeddingConfig::getEmbeddingModel,
                            req.getEmbeddingModel())
                    .set(req.getDimensions() != null, AiEmbeddingConfig::getDimensions, req.getDimensions())
                    .set(req.getOperator() != null, AiEmbeddingConfig::getOperator, req.getOperator())
                    .set(req.getEnabled() != null, AiEmbeddingConfig::getEnabled, req.getEnabled());
            if (req.getApiKey() != null) {
                wrapper.set(AiEmbeddingConfig::getApiKeyEncrypted, encrypt(req.getApiKey()));
            }
            embeddingMapper.update(null, wrapper);
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("dimensions", req.getDimensions());
        changes.put("operator", req.getOperator());
        changes.put("requiresReindex", requiresReindex);
        writeAudit(OPERATION_SAVE, loginUser, request, changes);
        AiEmbeddingRespDTO resp = get();
        resp.setRequiresReindex(requiresReindex || resp.getRequiresReindex());
        return resp;
    }

    /**
     * 连通性测试（3.4）：以已保存配置发起最小向量化（计入 ai_usage_log）。
     * 成功持久化 last_test；失败抛 1000018107（响应含失败摘要）。
     */
    public AiEmbeddingTestRespDTO test(LoginUser loginUser, HttpServletRequest request) {
        AiEmbeddingConfig row = embeddingMapper.selectSingleton();
        if (row == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_TEST_FAILED.code(), "未配置向量 API，请先保存配置");
        }

        long start = System.currentTimeMillis();
        try {
            AiEmbeddingClient.EmbeddingReply reply = embeddingClient.embed(row, List.of("连通性探测"),
                    loginUser.getId(), null, null);
            int latencyMs = (int) (System.currentTimeMillis() - start);
            saveLastTest(row, true, reply.dimensions(), latencyMs, null);
            writeAudit(OPERATION_TEST, loginUser, request, Map.of("success", true));
            AiEmbeddingTestRespDTO resp = new AiEmbeddingTestRespDTO();
            resp.setSuccess(true);
            resp.setDimensions(reply.dimensions());
            resp.setLatencyMs(latencyMs);
            resp.setMsg("ok");
            return resp;
        } catch (ServiceException e) {
            int latencyMs = (int) (System.currentTimeMillis() - start);
            String msg = truncate(e.getMessage());
            saveLastTest(row, false, null, latencyMs, msg);
            writeAudit(OPERATION_TEST, loginUser, request, Map.of("success", false, "msg", msg));
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_TEST_FAILED.code(), msg);
        }
    }

    // ========== 私有 ==========

    /** 旧配置追加进 versions（2.4 结构 { version, embeddingModel, dimensions, operator, retiredAt }） */
    private boolean retireCurrentVersion(AiEmbeddingConfig row) {
        List<Map<String, Object>> versions = row.getVersions() == null ? new ArrayList<>() : row.getVersions();
        Map<String, Object> retired = new LinkedHashMap<>();
        retired.put("version", versions.size() + 1);
        retired.put("embeddingModel", row.getEmbeddingModel());
        retired.put("dimensions", row.getDimensions());
        retired.put("operator", row.getOperator());
        retired.put("retiredAt", LocalDateTime.now().toString());
        versions.add(retired);
        embeddingMapper.update(null, new LambdaUpdateWrapperX<AiEmbeddingConfig>()
                .eq(AiEmbeddingConfig::getId, row.getId())
                .set(AiEmbeddingConfig::getVersions, versions));
        return true;
    }

    private void saveLastTest(AiEmbeddingConfig row, boolean success, Integer dimensions, int latencyMs, String msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("latencyMs", latencyMs);
        if (dimensions != null) {
            result.put("dimensions", dimensions);
        }
        if (msg != null) {
            result.put("msg", msg);
        }
        embeddingMapper.update(null, new LambdaUpdateWrapperX<AiEmbeddingConfig>()
                .eq(AiEmbeddingConfig::getId, row.getId())
                .set(AiEmbeddingConfig::getLastTestAt, LocalDateTime.now())
                .set(AiEmbeddingConfig::getLastTestResult, result));
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
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_INVALID.code(), "端点地址非法：" + baseUrl);
        }
    }

    private String encrypt(String plainKey) {
        if (secretKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_INVALID.code(), "服务端密钥未配置，无法加密存储");
        }
        return SecretCryptoUtil.encrypt(secretKey, plainKey);
    }

    private AiModelRespDTO.LastTest toLastTest(AiEmbeddingConfig row) {
        if (row.getLastTestAt() == null) {
            return null;
        }
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
        return lastTest;
    }

    private void writeAudit(String operation, LoginUser loginUser, HttpServletRequest request,
            Map<String, Object> changes) {
        AuditLog record = new AuditLog();
        record.setOperation(operation);
        record.setEntityType(ENTITY_TYPE_AI_EMBEDDING);
        record.setOperatorId(loginUser.getId());
        record.setOperatorName(loginUser.getUsername());
        record.setRequestIp(clientIpResolver.resolve(request));
        record.setChanges(changes);
        auditLogWriter.write(record);
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= TEST_MSG_MAX_LENGTH ? text : text.substring(0, TEST_MSG_MAX_LENGTH);
    }
}
