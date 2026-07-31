package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 对话模型配置响应（密钥永不回传明文，仅 configured + keySuffix）
 */
@Data
public class AiChatModelRespDTO {

    private String id;
    private String name;
    private String provider;
    private String baseUrl;
    private String model;
    private AiConfigRespDTO.ApiKeyInfo apiKey;
    private Map<String, Object> extraParams;
    private Boolean enabled;
    private Boolean isDefault;
    /** 最后更新人 sys_user.id */
    private String updatedBy;
    /** 供更新请求的乐观并发校验（expectedUpdatedAt）回传 */
    private LocalDateTime updatedAt;
}
