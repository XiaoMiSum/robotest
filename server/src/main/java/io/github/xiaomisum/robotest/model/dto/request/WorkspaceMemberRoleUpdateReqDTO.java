package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspaceMemberRoleUpdateReqDTO {

    @NotBlank(message = "工作空间角色不能为空")
    private String workspaceRole;
}
