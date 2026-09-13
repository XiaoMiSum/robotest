package io.github.xiaomisum.robotest.model.dto.response.admin;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class AuditLogRespDTO {

    private UUID id;
    private UUID operatorId;
    private String operatorName;
    private String operation;
    private String entityType;
    private UUID entityId;
    private Map<String, Object> changes;
    private String requestIp;
    private LocalDateTime createdAt;
}