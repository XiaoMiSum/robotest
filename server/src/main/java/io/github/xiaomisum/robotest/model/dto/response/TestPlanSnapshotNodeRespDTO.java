package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TestPlanSnapshotNodeRespDTO {

    private String id;
    private String originalNodeId;
    private String parentId;
    private String title;
    private String type;
    private String priority;
    private Boolean isAssociated;
    private String lastResult;
    private String lastExecutorId;
    private LocalDateTime lastExecutedAt;
    private Integer sortOrder;
    private List<TestPlanSnapshotNodeRespDTO> children;
}
