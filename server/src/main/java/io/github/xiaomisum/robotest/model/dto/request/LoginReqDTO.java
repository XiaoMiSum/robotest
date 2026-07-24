package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginReqDTO {

    @NotBlank(message = "用户名或邮箱不能为空")
    private String identifier;

    @NotBlank(message = "密码不能为空")
    private String password;
}
