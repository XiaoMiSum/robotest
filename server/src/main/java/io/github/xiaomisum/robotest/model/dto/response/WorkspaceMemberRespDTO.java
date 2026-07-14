package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkspaceMemberRespDTO {

    private String userId;
    private String username;
    private String email;
    private String avatarUrl;
    private String workspaceRole;
    private LocalDateTime joinedAt;
}
