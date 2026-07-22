package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import xyz.migoo.framework.common.validation.Password;

@Data
public class LoginReqDTO {

    @NotBlank(message = "用户名或邮箱不能为空")
    private String identifier;

    @NotBlank(message = "密码不能为空")
    @Password(message = "密码强度不够，需包含大小写字母、数字和特殊字符")
    private String password;
}
