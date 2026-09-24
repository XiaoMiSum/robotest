package io.github.xiaomisum.robotest.model.dto.response.workspace;

import lombok.Data;

import java.util.UUID;

@Data
public class ProjectActivityRespDTO {

    private UUID id;
    private UUID projectId;
    private UUID actorId;
    private String actorName;
    private String resourceType;
    private UUID resourceId;
    private String resourceName;
    private String action;
    private String summary;
    private String occurredAt;
}
