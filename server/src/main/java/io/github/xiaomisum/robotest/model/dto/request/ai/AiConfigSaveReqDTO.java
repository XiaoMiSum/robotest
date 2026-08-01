package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 保存 AI 配置请求（总开关 + Embedding 单一配置 + 系统配置项；对话模型独立管理，见 chat-models 接口）。
 *
 * <p>apiKey 为字符串：非空即更新，null 表示保持原值。</p>
 */
@Data
public class AiConfigSaveReqDTO {

    @NotNull(message = "AI 总开关不能为空")
    private Boolean enabled;

    @Valid
    private EmbeddingGroup embedding;

    private Map<String, Object> settings;

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

