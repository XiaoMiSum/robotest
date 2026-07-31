package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 配置响应（密钥永不回传明文，仅 configured + keySuffix）
 */
@Data
public class AiConfigRespDTO {

    private Boolean enabled;
    private ChatGroup chat;
    private EmbeddingGroup embedding;
    private Map<String, Object> settings;
    /** 供保存请求的乐观并发校验（expectedUpdatedAt）回传 */
    private LocalDateTime updatedAt;

    @Data
    public static class ChatGroup {
        private String provider;
        private String baseUrl;
        private String model;
        private ApiKeyInfo apiKey;
        private Map<String, Object> extraParams;
    }

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
