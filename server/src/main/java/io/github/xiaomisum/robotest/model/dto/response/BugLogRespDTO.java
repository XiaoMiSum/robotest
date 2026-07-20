package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BugLogRespDTO {

    private String id;
    private String operatorId;
    private String operatorName;
    private String operationType;
    private String content;
    private LocalDateTime createdAt;
}
