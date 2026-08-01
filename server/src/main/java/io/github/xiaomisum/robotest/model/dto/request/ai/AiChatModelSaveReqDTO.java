package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 新建/更新对话模型请求（apiKey 非空即更新、缺省保持原值）
 */
@Data
public class AiChatModelSaveReqDTO {

    @NotBlank(message = "显示名不能为空")
    private String name;

    @NotBlank(message = "供应商不能为空")
    private String provider;

    @NotBlank(message = "服务地址不能为空")
    private String baseUrl;

    @NotBlank(message = "模型名不能为空")
    private String model;

    private String apiKey;

    private Map<String, Object> extraParams;
}
