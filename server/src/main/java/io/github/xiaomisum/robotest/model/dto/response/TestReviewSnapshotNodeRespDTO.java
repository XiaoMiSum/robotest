package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TestReviewSnapshotNodeRespDTO {

    private UUID id;
    private UUID originalNodeId;
    private UUID parentId;
    private String title;
    private String type;
    private String priority;
    private Boolean isAssociated;
    private String lastMark;
    private UUID lastReviewerId;
    private LocalDateTime lastReviewedAt;
    private Integer sortOrder;
    private List<TestReviewSnapshotNodeRespDTO> children;
}
