package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存场景提示词（PUT /api/ai/prompts/{scene}，详设 3.5；创建或覆盖，版本自增）。
 */
@Data
public class AiPromptSaveReqDTO {

    /** 提示词内容；{{变量}} 必须全部出现在场景登记的变量清单（否则 1000018109） */
    @NotBlank(message = "提示词内容不能为空")
    private String content;
}
