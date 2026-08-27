package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiCustomFunctionSaveReqDTO {

    @NotBlank(message = "函数名不能为空")
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,99}", message = "函数名须以字母开头，仅含字母、数字、下划线")
    private String name;

    @Size(max = 500, message = "函数描述不能超过 500 字符")
    private String description;

    @Size(max = 500, message = "参数说明不能超过 500 字符")
    private String paramsDesc;

    @NotBlank(message = "Groovy 脚本不能为空")
    @Size(max = 20000, message = "Groovy 脚本不能超过 20000 字符")
    private String script;

    @Pattern(regexp = "project|workspace|global", message = "作用域不合法")
    private String scope = "project";
}
