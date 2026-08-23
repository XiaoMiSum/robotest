package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 启用/停用接口（接口管理详细设计 3.1.11） */
@Data
public class ApiInterfaceStatusReqDTO {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "enabled|disabled", message = "状态仅允许 enabled / disabled")
    private String status;
}
