package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TestPlanExecutionRecordRespDTO {

    private UUID id;
    private UUID snapshotNodeId;
    private UUID executorId;
    private String executorName;
    private String result;
    private String note;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;
}
