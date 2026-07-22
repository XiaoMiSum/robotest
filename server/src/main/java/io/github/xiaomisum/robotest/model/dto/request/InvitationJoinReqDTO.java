package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import xyz.migoo.framework.common.validation.Email;
import xyz.migoo.framework.common.validation.Password;

@Data
public class InvitationJoinReqDTO {

    @NotBlank(message = "邀请令牌不能为空")
    private String token;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Password(message = "密码强度不够，需包含大小写字母、数字和特殊字符")
    private String password;
}
