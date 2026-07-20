package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TestReviewSnapshotNodeRespDTO {

    private String id;
    private String originalNodeId;
    private String parentId;
    private String title;
    private String type;
    private String priority;
    private Boolean isAssociated;
    private String lastMark;
    private String lastReviewerId;
    private LocalDateTime lastReviewedAt;
    private Integer sortOrder;
    private List<TestReviewSnapshotNodeRespDTO> children;
}
