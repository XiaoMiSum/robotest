package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiModelConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

/**
 * AI 配置读取（详设 4.1，批次一仅读；写侧配置中心随 WP-4.1 交付）。
 */
@Component
public class AiSettingsReader {

    private static final int DEFAULT_TASK_TIMEOUT_SECONDS = 600;
    private static final int DEFAULT_TASK_MAX_RETRIES = 2;

    /** 模型能力标签：vision（详设 2.3 / 4.2 模型选型的必需能力取值） */
    public static final String CAPABILITY_VISION = "vision";

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
        return resolveModel(preferredModelId, null);
    }

    /**
     * 带必需能力的解析（详设 4.2 模型选型）：任务指定模型（启用且满足能力）→ 默认模型（启用且满足能力）
     * → 启用且含能力的模型按 priority 升序取首 → null（调用方映射 1000018118 / 1000018124）。
     * 能力为 null 时与两级解析口径一致；模型行数受配置规模约束，能力过滤在内存执行、依赖 SQL 侧 priority 排序。
     */
    public AiModelConfig resolveModel(UUID preferredModelId, String requiredCapability) {
        if (preferredModelId != null) {
            AiModelConfig preferred = modelMapper.selectById(preferredModelId);
            if (enabledAndCapable(preferred, requiredCapability)) {
                return preferred;
            }
        }
        AiModelConfig defaultModel = usableModel();
        if (enabledAndCapable(defaultModel, requiredCapability)) {
            return defaultModel;
        }
        if (requiredCapability == null) {
            return null;
        }
        return modelMapper.selectList(new LambdaQueryWrapperX<AiModelConfig>()
                        .eq(AiModelConfig::getEnabled, Boolean.TRUE)
                        .orderByAsc(AiModelConfig::getPriority))
                .stream()
                .filter(candidate -> enabledAndCapable(candidate, requiredCapability))
                .findFirst()
                .orElse(null);
    }

    private static boolean enabledAndCapable(AiModelConfig model, String requiredCapability) {
        if (model == null || !Boolean.TRUE.equals(model.getEnabled())) {
            return false;
        }
        return requiredCapability == null
                || (model.getCapabilities() != null && model.getCapabilities().contains(requiredCapability));
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
