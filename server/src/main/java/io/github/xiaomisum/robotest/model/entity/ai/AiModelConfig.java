package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 模型配置（详设 2.3）：api_key 以 AES 加密落库（4.1），接口与日志不回显。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_model_config", autoResultMap = true)
public class AiModelConfig extends BaseUuidDO<AiModelConfig> {

    private String name;

    /** 提供商标识（openai 兼容等） */
    private String provider;

    private String baseUrl;

    /** AES-256-GCM 密文（SecretCryptoUtil），调用时解密 */
    private String apiKeyEncrypted;

    private String modelName;

    /** 能力标签（chat / embedding 等） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> capabilities;

    private Integer priority;

    private Boolean enabled;

    private LocalDateTime lastTestAt;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> lastTestResult;
}
