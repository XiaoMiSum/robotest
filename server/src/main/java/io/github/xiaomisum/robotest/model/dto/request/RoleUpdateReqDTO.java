package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleUpdateReqDTO {

    @NotBlank(message = "角色名称不能为空")
    private String name;
}
