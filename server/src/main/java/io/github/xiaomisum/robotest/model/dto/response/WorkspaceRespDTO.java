package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkspaceRespDTO {

    private String id;
    private String name;
    private String description;
    private String status;
    private Long memberCount;
    private Long projectCount;
    private LocalDateTime createdAt;
}
