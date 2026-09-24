package io.github.xiaomisum.robotest.model.dto.response.admin;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class UserRespDTO {

    private UUID id;
    private String username;
    private String name;
    private String email;
    private String avatarUrl;
    private String status;
    private List<RoleSimple> roles;
    private List<WorkspaceSimple> workspaces;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 授权时间：仅按角色过滤查询时回填 sys_user_role.updated_at，其余场景为 null */
    private LocalDateTime grantedAt;

    @Data
    public static class RoleSimple {
        private UUID id;
        private String name;
        private String type;
    }

    @Data
    public static class WorkspaceSimple {
        private UUID id;
        private String name;
        private String workspaceRole;
    }
}
