package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import xyz.migoo.framework.common.validation.Email;
import xyz.migoo.framework.common.validation.Password;

import java.util.List;
import java.util.UUID;

@Data
public class UserCreateReqDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 30, message = "用户名长度为3-30个字符")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度为8-64个字符")
    @Password(message = "密码强度不够，需包含大小写字母、数字和特殊字符")
    private String password;

    private List<UUID> roleIds;
    private List<UUID> workspaceIds;
}
