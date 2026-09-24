package io.github.xiaomisum.robotest.model.dto.response.admin;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class RoleWorkspaceUserRespDTO {

    private UUID userId;
    private String username;
    private String name;
    private List<WorkspaceInfo> workspaces;
    /** 授权时间：ws_user.updated_at，多空间聚合取最近一次 */
    private LocalDateTime grantedAt;

    @Data
    public static class WorkspaceInfo {
        private UUID workspaceId;
        private String workspaceName;
    }
}
