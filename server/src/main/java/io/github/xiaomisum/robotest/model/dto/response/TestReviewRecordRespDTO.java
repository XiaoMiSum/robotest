package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestReviewRecordRespDTO {

    private String id;
    private String snapshotNodeId;
    private String reviewerId;
    private String reviewerName;
    private String operationType;
    private String mark;
    private String comment;
    private LocalDateTime createdAt;
}
