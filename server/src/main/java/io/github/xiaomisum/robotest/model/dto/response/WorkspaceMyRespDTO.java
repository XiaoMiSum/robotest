package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkspaceMyRespDTO {

    private String id;
    private String name;
    private String description;
    private String workspaceRole;
    private String defaultProjectId;
    private String defaultProjectName;
    private Long memberCount;
    private Long projectCount;
    private String status;
    private LocalDateTime createdAt;
}
