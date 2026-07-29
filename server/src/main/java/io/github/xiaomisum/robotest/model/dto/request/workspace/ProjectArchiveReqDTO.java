package io.github.xiaomisum.robotest.model.dto.request.workspace;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectArchiveReqDTO {

    @NotNull(message = "归档状态不能为空")
    private Boolean archived;
}
