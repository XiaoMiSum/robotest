package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.Map;
import java.util.UUID;

/**
 * AI 对话模型配置（多行表：每行一个可用对话模型，全系统唯一默认，由应用层保证）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_chat_model", autoResultMap = true)
public class AiChatModel extends BaseUuidDO<AiChatModel> {

    private String name;
    private String provider;
    private String baseUrl;
    private String apiKeyCipher;
    private String keySuffix;
    private String model;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> extraParams;
    private Boolean enabled;
    private Boolean isDefault;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID updatedBy;
}
