package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * 连通性测试请求：可附带未保存的临时配置，缺省时用已保存配置测试。
 *
 * <p>target=chat 时：临时配置 chat 优先，缺省则按 modelId 取已保存的对话模型配置测试
 * （密钥缺省回退该模型已存密文）。target=embedding 时缺省用已保存 Embedding 配置。</p>
 */
@Data
public class AiConfigTestReqDTO {

    @NotBlank(message = "测试目标不能为空")
    private String target;

    /** target=chat 且缺省临时配置时，指定已保存的对话模型（缺省则测系统默认模型） */
    private UUID modelId;

    @Valid
    private AiConfigSaveReqDTO.EmbeddingGroup embedding;

    /** target=chat 的未保存临时对话配置（结构同对话模型保存请求的核心字段） */
    @Valid
    private ChatGroup chat;

    @Data
    public static class ChatGroup {

        private String provider;
        private String baseUrl;
        private String model;
        private String apiKey;
        private Map<String, Object> extraParams;
    }
}

