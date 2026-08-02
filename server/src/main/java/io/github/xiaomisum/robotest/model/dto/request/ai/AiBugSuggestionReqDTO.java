package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 缺陷表单智能建议请求（3.1）：title 必填且 ≤ 300（对齐 bug 表列宽），reproSteps 可空，超长由服务端截断不报错
 */
@Data
public class AiBugSuggestionReqDTO {

    @NotBlank(message = "缺陷标题不能为空")
    @Size(max = 300, message = "缺陷标题不能超过 300 字符")
    private String title;

    private String reproSteps;
}
