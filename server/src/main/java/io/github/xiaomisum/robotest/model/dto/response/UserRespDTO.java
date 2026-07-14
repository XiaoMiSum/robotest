package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserRespDTO {

    private String id;
    private String username;
    private String email;
    private String avatarUrl;
    private String status;
    private List<RoleSimple> roles;
    private List<WorkspaceSimple> workspaces;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class RoleSimple {
        private String id;
        private String name;
        private String type;
    }

    @Data
    public static class WorkspaceSimple {
        private String id;
        private String name;
        private String workspaceRole;
    }
}
