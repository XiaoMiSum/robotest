package io.github.xiaomisum.robotest.model.dto.response.workspace;

import lombok.Data;

@Data
public class WorkspaceMyScopeCountsDTO {

    private Long all = 0L;
    private Long managed = 0L;
    private Long archived = 0L;
}
