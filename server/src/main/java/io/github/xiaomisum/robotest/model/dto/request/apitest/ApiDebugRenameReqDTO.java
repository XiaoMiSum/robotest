package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiDebugRenameReqDTO {

    @NotBlank(message = "名称不能为空")
    @Size(max = 200, message = "名称长度不能超过 200")
    private String name;
}
