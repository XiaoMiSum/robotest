package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 配置响应（总开关 + Embedding 单一配置 + 系统配置项；对话模型独立于 chat-models 接口）。
 *
 * <p>密钥永不回传明文，仅 configured + keySuffix。</p>
 */
@Data
public class AiConfigRespDTO {

    private Boolean enabled;
    private EmbeddingGroup embedding;
    private Map<String, Object> settings;
    /** 供保存请求的乐观并发校验（expectedUpdatedAt）回传 */
    private LocalDateTime updatedAt;

    @Data
    public static class EmbeddingGroup {
        private String provider;
        private String baseUrl;
        private String model;
        private Integer dimension;
        private ApiKeyInfo apiKey;
        private Map<String, Object> extraParams;
    }

    @Data
    public static class ApiKeyInfo {
        private Boolean configured;
        private String keySuffix;
    }
}
