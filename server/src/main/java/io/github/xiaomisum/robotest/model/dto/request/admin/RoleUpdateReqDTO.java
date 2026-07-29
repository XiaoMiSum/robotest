package io.github.xiaomisum.robotest.model.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleUpdateReqDTO {

    @NotBlank(message = "角色名称不能为空")
    private String name;
}
