package io.github.xiaomisum.robotest.service.ai.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiPromptTemplate;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 纯 mock 单测下 wrapper 的 lambda 需要实体 TableInfo 缓存（仿 Code003MapperWrapperTest）。
 */
final class AiConfigTableInfo {

    private static final Class<?>[] ENTITIES = {
            AiConfig.class, AiModelConfig.class, AiEmbeddingConfig.class, AiPromptTemplate.class };

    private AiConfigTableInfo() {
    }

    static void init() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : ENTITIES) {
            if (TableInfoHelper.getTableInfo(entity) == null) {
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
    }
}
