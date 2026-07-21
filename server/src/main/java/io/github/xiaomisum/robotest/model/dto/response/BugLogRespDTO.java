package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BugLogRespDTO {

    private UUID id;
    private UUID operatorId;
    private String operatorName;
    private String operationType;
    private String content;
    private LocalDateTime createdAt;
}
