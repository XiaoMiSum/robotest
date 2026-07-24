package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WorkspaceMemberRespDTO {

    private UUID userId;
    private String username;
    private String email;
    private String avatarUrl;
    private UUID workspaceRole;
    private LocalDateTime joinedAt;
}
