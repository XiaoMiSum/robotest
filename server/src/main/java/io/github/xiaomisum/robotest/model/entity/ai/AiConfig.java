package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.Map;

/**
 * AI 配置（系统级单行表：总开关、系统配置项与 Embedding 单一配置；对话模型多行独立存于 ai_chat_model）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_config", autoResultMap = true)
public class AiConfig extends BaseUuidDO<AiConfig> {

    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingApiKeyCipher;
    private String embeddingKeySuffix;
    private String embeddingModel;
    private Integer embeddingDimension;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> embeddingExtraParams;

    private Boolean enabled;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> settings;
}
