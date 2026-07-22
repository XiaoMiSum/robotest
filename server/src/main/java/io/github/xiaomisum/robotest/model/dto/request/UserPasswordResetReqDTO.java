package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import xyz.migoo.framework.common.validation.Password;

@Data
public class UserPasswordResetReqDTO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度为8-64个字符")
    @Password(message = "密码强度不够，需包含大小写字母、数字和特殊字符")
    private String newPassword;
}
