package io.github.xiaomisum.robotest.model.dto.response.workspace;

import lombok.Data;

import java.util.List;

@Data
public class WorkspaceMyPageRespDTO {

    private List<WorkspaceMyRespDTO> list;
    private Long total;
    private WorkspaceMyScopeCountsDTO counts;
}
