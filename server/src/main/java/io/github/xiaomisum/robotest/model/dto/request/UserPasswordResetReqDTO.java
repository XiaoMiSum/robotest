package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserPasswordResetReqDTO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度为8-64个字符")
    private String newPassword;
}
