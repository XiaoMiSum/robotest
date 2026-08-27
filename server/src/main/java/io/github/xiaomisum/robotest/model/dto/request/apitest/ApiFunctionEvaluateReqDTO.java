package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 函数试算请求（POST /api/project/functions/evaluate）：对完整调用表达式求值 */
@Data
public class ApiFunctionEvaluateReqDTO {

    @NotBlank(message = "求值表达式不能为空")
    @Size(max = 2000, message = "求值表达式不能超过 2000 字符")
    private String expression;
}
