package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 保存 AI 配置请求（结构同 GET 响应；apiKey 为字符串：非空即更新，null 表示保持原值）
 */
@Data
public class AiConfigSaveReqDTO {

    @NotNull(message = "AI 总开关不能为空")
    private Boolean enabled;

    @NotNull(message = "对话模型配置不能为空")
    @Valid
    private ChatGroup chat;

    @Valid
    private EmbeddingGroup embedding;

    private Map<String, Object> settings;

    /** 乐观并发校验：取自查询响应的 updatedAt，不一致时拒绝保存要求刷新重试 */
    private LocalDateTime expectedUpdatedAt;

    @Data
    public static class ChatGroup {

        @NotBlank(message = "对话模型供应商不能为空")
        private String provider;

        @NotBlank(message = "对话模型服务地址不能为空")
        private String baseUrl;

        @NotBlank(message = "对话模型名不能为空")
        private String model;

        private String apiKey;

        private Map<String, Object> extraParams;
    }

    @Data
    public static class EmbeddingGroup {

        private String provider;
        private String baseUrl;
        private String model;
        private Integer dimension;
        private String apiKey;
        private Map<String, Object> extraParams;
    }
}
