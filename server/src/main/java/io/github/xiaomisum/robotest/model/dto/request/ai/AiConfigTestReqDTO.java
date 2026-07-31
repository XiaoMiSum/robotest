package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 连通性测试请求：可附带未保存的临时配置，缺省时用已保存配置测试
 */
@Data
public class AiConfigTestReqDTO {

    @NotBlank(message = "测试目标不能为空")
    private String target;

    @Valid
    private AiConfigSaveReqDTO.ChatGroup chat;

    @Valid
    private AiConfigSaveReqDTO.EmbeddingGroup embedding;
}
