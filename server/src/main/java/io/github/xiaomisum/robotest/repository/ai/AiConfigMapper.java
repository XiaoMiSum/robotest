package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

public interface AiConfigMapper extends BaseMapperX<AiConfig> {

    /** 单例行（uk_ai_config_singleton）；缺失返回 null，由读取方按内置默认（enabled = false）降级 */
    default AiConfig selectSingleton() {
        return selectOne(new LambdaQueryWrapperX<AiConfig>().last("LIMIT 1"));
    }
}
