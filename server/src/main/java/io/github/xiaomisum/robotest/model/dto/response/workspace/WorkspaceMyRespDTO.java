package io.github.xiaomisum.robotest.model.dto.response.workspace;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WorkspaceMyRespDTO {

    private UUID id;
    private String name;
    private String description;
    private String workspaceRole;
    private String workspaceRoleName;
    private UUID defaultProjectId;
    private String defaultProjectName;
    private Long memberCount;
    private Long projectCount;
    private Long testCaseCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;
}
