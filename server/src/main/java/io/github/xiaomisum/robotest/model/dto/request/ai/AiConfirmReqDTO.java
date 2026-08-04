package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 写操作确认/取消请求（详细设计 3.3）
 */
@Data
public class AiConfirmReqDTO {

    /** 确认令牌（UUID） */
    @NotBlank(message = "确认令牌不能为空")
    private String confirmToken;
}
