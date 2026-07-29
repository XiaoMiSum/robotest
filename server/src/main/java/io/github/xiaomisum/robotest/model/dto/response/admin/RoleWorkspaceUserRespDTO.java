package io.github.xiaomisum.robotest.model.dto.response.admin;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class RoleWorkspaceUserRespDTO {

    private UUID userId;
    private String username;
    private String name;
    private List<WorkspaceInfo> workspaces;

    @Data
    public static class WorkspaceInfo {
        private UUID workspaceId;
        private String workspaceName;
    }
}
