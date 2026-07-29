package io.github.xiaomisum.robotest.model.dto.request.workspace;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class WorkspaceActiveSetReqDTO {

    @NotNull(message = "工作空间ID不能为空")
    private UUID workspaceId;
}
