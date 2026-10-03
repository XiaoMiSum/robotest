package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiModelConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AI 配置读取（详设 4.1，批次一仅读；写侧配置中心随 WP-4.1 交付）。
 */
@Component
public class AiSettingsReader {

    private static final int DEFAULT_TASK_TIMEOUT_SECONDS = 600;
    private static final int DEFAULT_TASK_MAX_RETRIES = 2;

    @Resource
    private AiConfigMapper configMapper;
    @Resource
    private AiModelConfigMapper modelMapper;

    /** 全局配置；单例行缺失时按内置默认（详设 2.2，enabled = false） */
    public AiSettings settings() {
        AiConfig row = configMapper.selectSingleton();
        if (row == null) {
            return new AiSettings(false, null, DEFAULT_TASK_TIMEOUT_SECONDS, DEFAULT_TASK_MAX_RETRIES);
        }
        return new AiSettings(
                Boolean.TRUE.equals(row.getEnabled()),
                row.getDefaultModelId(),
                row.getTaskTimeoutSeconds() == null ? DEFAULT_TASK_TIMEOUT_SECONDS : row.getTaskTimeoutSeconds(),
                row.getTaskMaxRetries() == null ? DEFAULT_TASK_MAX_RETRIES : row.getTaskMaxRetries());
    }

    /** 可用模型 = 默认模型配置且启用（modelReady / 提交校验 1000018118 的口径） */
    public AiModelConfig usableModel() {
        UUID defaultModelId = settings().defaultModelId();
        if (defaultModelId == null) {
            return null;
        }
        AiModelConfig model = modelMapper.selectById(defaultModelId);
        return model != null && Boolean.TRUE.equals(model.getEnabled()) ? model : null;
    }

    /**
     * 执行期解析：优先任务回写的 model_id（提交时解析，4.1），其被停用 / 删除时回落当前可用模型
     * （失败重试复用同模型，3.6.4；回落保证存量任务仍可执行）。
     */
    public AiModelConfig resolveModel(UUID preferredModelId) {
        if (preferredModelId != null) {
            AiModelConfig preferred = modelMapper.selectById(preferredModelId);
            if (preferred != null && Boolean.TRUE.equals(preferred.getEnabled())) {
                return preferred;
            }
        }
        return usableModel();
    }

    /** 业务端可用性（详设 3.2：仅回三个布尔口径，不泄露配置明细） */
    public AiStatusRespDTO status() {
        AiSettings settings = settings();
        boolean modelReady = usableModel() != null;
        AiStatusRespDTO dto = new AiStatusRespDTO();
        dto.setEnabled(settings.enabled());
        dto.setModelReady(modelReady);
        dto.setAvailable(settings.enabled() && modelReady);
        return dto;
    }

    /** 全局配置快照（任务超时 / 重试参数的唯一读取入口） */
    public record AiSettings(boolean enabled, UUID defaultModelId, int taskTimeoutSeconds, int taskMaxRetries) {
    }
}
