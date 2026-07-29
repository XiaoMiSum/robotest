package io.github.xiaomisum.robotest.model.dto.request.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import xyz.migoo.framework.common.validation.Email;

@Data
public class InvitationJoinReqDTO {

    @NotBlank(message = "邀请令牌不能为空")
    private String token;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度为8-64个字符")
    private String password;

    /** 新用户时传入的姓名，已有用户时忽略 */
    private String name;
}
