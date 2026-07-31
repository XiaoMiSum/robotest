package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.Map;

/**
 * AI 配置（系统级单行表：全系统仅一条有效记录）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_config", autoResultMap = true)
public class AiConfig extends BaseUuidDO<AiConfig> {

    private String chatProvider;
    private String chatBaseUrl;
    private String chatApiKeyCipher;
    private String chatKeySuffix;
    private String chatModel;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> chatExtraParams;

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
