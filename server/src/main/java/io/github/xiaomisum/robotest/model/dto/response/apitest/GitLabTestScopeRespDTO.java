package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class GitLabTestScopeRespDTO {
    private UUID id;
    private UUID repositoryId;
    private String variableName;
    private String scopeType;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
