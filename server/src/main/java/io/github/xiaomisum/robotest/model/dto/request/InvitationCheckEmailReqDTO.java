package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import xyz.migoo.framework.common.validation.Email;

@Data
public class InvitationCheckEmailReqDTO {

    @NotBlank(message = "邀请令牌不能为空")
    private String token;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
