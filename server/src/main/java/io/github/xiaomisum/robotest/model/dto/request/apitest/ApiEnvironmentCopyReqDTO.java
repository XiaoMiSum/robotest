package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiEnvironmentCopyReqDTO {

    @NotBlank(message = "副本名称不能为空")
    @Size(max = 100, message = "环境名称不能超过 100 字符")
    private String name;
}
