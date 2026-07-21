package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TestReviewRecordRespDTO {

    private UUID id;
    private UUID snapshotNodeId;
    private UUID reviewerId;
    private String reviewerName;
    private String operationType;
    private String mark;
    private String comment;
    private LocalDateTime createdAt;
}
