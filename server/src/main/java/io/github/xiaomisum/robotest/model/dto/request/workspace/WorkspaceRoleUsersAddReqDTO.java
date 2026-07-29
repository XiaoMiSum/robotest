package io.github.xiaomisum.robotest.model.dto.request.workspace;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class WorkspaceRoleUsersAddReqDTO {

    @NotEmpty(message = "用户ID列表不能为空")
    private List<UUID> userIds;

    @NotEmpty(message = "空间ID列表不能为空")
    private List<UUID> workspaceIds;
}
