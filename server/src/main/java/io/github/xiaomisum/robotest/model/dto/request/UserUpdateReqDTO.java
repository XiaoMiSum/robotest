package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.List;

@Data
public class UserUpdateReqDTO {

    @Email(message = "邮箱格式不正确")
    private String email;

    private List<String> roleIds;
    private List<String> workspaceIds;
}
