package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.repository.ai.AiEmbeddingConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

/**
 * 向量能力门禁（详设 4.4 / 4.5 降级口径）：读侧未就绪抛错给调用方说明原因，
 * 写侧未就绪返回 null 静默跳过，业务保存不因向量底座缺失而失败。
 */
@Component
public class AiEmbeddingGate {

    @Resource
    private AiEmbeddingConfigMapper embeddingMapper;

    /**
     * 读侧门禁：未配置 / 未启用 → 1000018119；全量重建中（versions 残留）→ 1000018122。
     */
    public AiEmbeddingConfig requireReady() {
        AiEmbeddingConfig row = requireEnabled();
        if (requiresReindex(row)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE);
        }
        return row;
    }

    /**
     * handler 门禁：只校验配置可用；重建中本身就是本任务的起因，不再以 1000018122 拦截。
     */
    public AiEmbeddingConfig requireEnabled() {
        AiEmbeddingConfig row = embeddingMapper.selectSingleton();
        if (row == null || !Boolean.TRUE.equals(row.getEnabled())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED);
        }
        return row;
    }

    /**
     * 写侧门禁：未配置 / 未启用 / 重建中返回 null（跳过重嵌）——
     * 重建期间列维度仍是旧值，新维度向量写入会失败，由全量重建统一收口。
     */
    public AiEmbeddingConfig writableConfig() {
        AiEmbeddingConfig row = embeddingMapper.selectSingleton();
        if (row == null || !Boolean.TRUE.equals(row.getEnabled()) || requiresReindex(row)) {
            return null;
        }
        return row;
    }

    /** 是否处于全量重建待办态（versions 非空，同 AiEmbeddingAdminService.get 的 requiresReindex 口径） */
    public static boolean requiresReindex(AiEmbeddingConfig row) {
        return row.getVersions() != null && !row.getVersions().isEmpty();
    }
}
