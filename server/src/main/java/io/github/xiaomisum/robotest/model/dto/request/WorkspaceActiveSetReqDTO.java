package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class WorkspaceActiveSetReqDTO {

    @NotBlank(message = "工作空间ID不能为空")
    private UUID workspaceId;
}
