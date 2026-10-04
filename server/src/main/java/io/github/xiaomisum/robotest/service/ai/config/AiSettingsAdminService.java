package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.audit.AuditLogWriter;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiSettingsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiSettingsRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiEmbeddingConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiModelConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 全局配置管理（详设 3.2）：部分更新（C11）、默认模型校验、
 * 开关关闭副作用（4.2：pending / running 批量置 failed）与审计。
 */
@Component
public class AiSettingsAdminService {

    private static final int DEFAULT_TASK_TIMEOUT_SECONDS = 600;
    private static final int DEFAULT_TASK_MAX_RETRIES = 2;

    private static final String OPERATION_UPDATE = "update";
    private static final String ENTITY_TYPE_AI_SETTINGS = "ai_settings";
    /** 关开关副作用的失败原因（4.2） */
    private static final String CLOSE_ERROR_MSG = "AI 总开关关闭";

    @Resource
    private AiConfigMapper configMapper;
    @Resource
    private AiModelConfigMapper modelMapper;
    @Resource
    private AiEmbeddingConfigMapper embeddingMapper;
    @Resource
    private AiTaskMapper taskMapper;
    @Resource
    private AiSettingsReader settingsReader;
    @Resource
    private AuditLogWriter auditLogWriter;
    @Resource
    private ClientIpResolver clientIpResolver;

    /** 全局配置查询（3.2）：含 modelReady / embeddingReady / available 三布尔 */
    public AiSettingsRespDTO get() {
        AiSettingsReader.AiSettings settings = settingsReader.settings();
        AiModelConfig model = settingsReader.usableModel();

        AiSettingsRespDTO dto = new AiSettingsRespDTO();
        dto.setEnabled(settings.enabled());
        dto.setDefaultModelId(settings.defaultModelId());
        dto.setDefaultModelName(resolveModelName(settings.defaultModelId()));
        dto.setTaskTimeoutSeconds(settings.taskTimeoutSeconds());
        dto.setTaskMaxRetries(settings.taskMaxRetries());
        dto.setModelReady(model != null);
        dto.setEmbeddingReady(embeddingReady());
        dto.setAvailable(settings.enabled() && model != null);
        return dto;
    }

    /** 更新（3.2，部分更新）：默认模型须存在且启用；关开关触发存量任务批量失败（4.2） */
    @Transactional(rollbackFor = Exception.class)
    public AiSettingsRespDTO update(AiSettingsUpdateReqDTO req, LoginUser loginUser, HttpServletRequest request) {
        if (req.getDefaultModelId() != null) {
            AiModelConfig model = modelMapper.selectById(req.getDefaultModelId());
            if (model == null || !Boolean.TRUE.equals(model.getEnabled())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_DEFAULT_MODEL_CONFLICT);
            }
        }

        int failedTasks = 0;
        AiConfig row = configMapper.selectSingleton();
        if (row == null) {
            row = new AiConfig();
            row.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
            row.setDefaultModelId(req.getDefaultModelId());
            row.setTaskTimeoutSeconds(req.getTaskTimeoutSeconds() == null
                    ? DEFAULT_TASK_TIMEOUT_SECONDS : req.getTaskTimeoutSeconds());
            row.setTaskMaxRetries(req.getTaskMaxRetries() == null
                    ? DEFAULT_TASK_MAX_RETRIES : req.getTaskMaxRetries());
            configMapper.insert(row);
        } else {
            configMapper.update(null, new LambdaUpdateWrapperX<AiConfig>()
                    .set(req.getEnabled() != null, AiConfig::getEnabled, req.getEnabled())
                    .set(req.getDefaultModelId() != null, AiConfig::getDefaultModelId, req.getDefaultModelId())
                    .set(req.getTaskTimeoutSeconds() != null, AiConfig::getTaskTimeoutSeconds,
                            req.getTaskTimeoutSeconds())
                    .set(req.getTaskMaxRetries() != null, AiConfig::getTaskMaxRetries, req.getTaskMaxRetries()));
        }

        // 关闭开关 → 存量 pending / running 批量置 failed（4.2）
        if (Boolean.FALSE.equals(req.getEnabled())) {
            failedTasks = taskMapper.failActive(CLOSE_ERROR_MSG, ErrorCodeConstants.AI_DISABLED.code());
        }

        writeAudit(loginUser, request, buildChanges(req), failedTasks);
        return get();
    }

    private boolean embeddingReady() {
        AiEmbeddingConfig embedding = embeddingMapper.selectSingleton();
        return embedding != null && Boolean.TRUE.equals(embedding.getEnabled());
    }

    private String resolveModelName(UUID modelId) {
        if (modelId == null) {
            return null;
        }
        AiModelConfig model = modelMapper.selectById(modelId);
        return model == null ? null : model.getName();
    }

    private Map<String, Object> buildChanges(AiSettingsUpdateReqDTO req) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (req.getEnabled() != null) {
            changes.put("enabled", req.getEnabled());
        }
        if (req.getDefaultModelId() != null) {
            changes.put("defaultModelId", req.getDefaultModelId().toString());
        }
        if (req.getTaskTimeoutSeconds() != null) {
            changes.put("taskTimeoutSeconds", req.getTaskTimeoutSeconds());
        }
        if (req.getTaskMaxRetries() != null) {
            changes.put("taskMaxRetries", req.getTaskMaxRetries());
        }
        return changes;
    }

    private void writeAudit(LoginUser loginUser, HttpServletRequest request, Map<String, Object> changes,
            int failedTasks) {
        if (failedTasks > 0) {
            changes = new LinkedHashMap<>(changes);
            changes.put("failedTasks", failedTasks);
        }
        AuditLog record = new AuditLog();
        record.setOperation(OPERATION_UPDATE);
        record.setEntityType(ENTITY_TYPE_AI_SETTINGS);
        record.setOperatorId(loginUser.getId());
        record.setOperatorName(loginUser.getUsername());
        record.setRequestIp(clientIpResolver.resolve(request));
        record.setChanges(changes);
        auditLogWriter.write(record);
    }
}
