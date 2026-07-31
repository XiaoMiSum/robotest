package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

public interface AiConfigMapper extends BaseMapperX<AiConfig> {

    /**
     * 系统级单行表：返回唯一有效记录，未配置时为 null
     */
    default AiConfig findActive() {
        return selectOne(new LambdaQueryWrapperX<AiConfig>()
                .orderByDesc(AiConfig::getCreatedAt)
                .last("LIMIT 1"));
    }
}
