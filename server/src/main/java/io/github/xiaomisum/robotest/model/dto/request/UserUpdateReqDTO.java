package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UserUpdateReqDTO {

    @Email(message = "邮箱格式不正确")
    private String email;

    private List<UUID> roleIds;
    private List<UUID> workspaceIds;
}
