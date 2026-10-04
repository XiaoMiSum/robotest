package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

public interface AiEmbeddingConfigMapper extends BaseMapperX<AiEmbeddingConfig> {

    /** 单例行（uk_ai_embedding_config_singleton）；缺失返回 null，由写入方按需创建 */
    default AiEmbeddingConfig selectSingleton() {
        return selectOne(new LambdaQueryWrapperX<AiEmbeddingConfig>().last("LIMIT 1"));
    }
}
