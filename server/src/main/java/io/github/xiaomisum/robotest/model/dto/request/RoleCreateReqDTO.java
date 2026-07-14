package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleCreateReqDTO {

    @NotBlank(message = "角色名称不能为空")
    private String name;

    @NotBlank(message = "角色类型不能为空")
    private String type;
}
