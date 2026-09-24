package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AiConfigMapper extends BaseMapperX<AiConfig> {

    /**
     * 系统级单行表：返回唯一有效记录，未配置时为 null
     */
    default AiConfig findActive() {
        return selectOne(new LambdaQueryWrapperX<AiConfig>()
                .orderByDesc(AiConfig::getCreatedAt)
                .last("LIMIT 1"));
    }

    /** 覆盖式保存系统配置；显式写入可空字段，允许清空 Embedding 配置。 */
    default int updateConfig(UUID id, Boolean enabled, String embeddingProvider, String embeddingBaseUrl,
                             String embeddingModel, Integer embeddingDimension, String embeddingApiKeyCipher,
                             String embeddingKeySuffix, String embeddingExtraParamsJson, String settingsJson) {
        return update(null, new LambdaUpdateWrapperX<AiConfig>()
                .eq(AiConfig::getId, id)
                .set(AiConfig::getEnabled, enabled)
                .set(AiConfig::getEmbeddingProvider, embeddingProvider)
                .set(AiConfig::getEmbeddingBaseUrl, embeddingBaseUrl)
                .set(AiConfig::getEmbeddingModel, embeddingModel)
                .set(AiConfig::getEmbeddingDimension, embeddingDimension)
                .set(AiConfig::getEmbeddingApiKeyCipher, embeddingApiKeyCipher)
                .set(AiConfig::getEmbeddingKeySuffix, embeddingKeySuffix)
                .set(AiConfig::getEmbeddingExtraParams, embeddingExtraParamsJson)
                .set(AiConfig::getSettings, settingsJson)
                .set(AiConfig::getUpdatedAt, LocalDateTime.now()));
    }
}
