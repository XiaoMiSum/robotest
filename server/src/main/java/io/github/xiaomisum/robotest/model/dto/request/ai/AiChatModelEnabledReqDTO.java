package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用/停用对话模型请求
 */
@Data
public class AiChatModelEnabledReqDTO {

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
