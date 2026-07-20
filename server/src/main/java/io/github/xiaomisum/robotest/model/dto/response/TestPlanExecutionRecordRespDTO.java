package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestPlanExecutionRecordRespDTO {

    private String id;
    private String snapshotNodeId;
    private String executorId;
    private String executorName;
    private String result;
    private String note;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;
}
