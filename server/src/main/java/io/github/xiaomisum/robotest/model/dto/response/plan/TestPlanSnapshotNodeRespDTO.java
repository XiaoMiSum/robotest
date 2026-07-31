package io.github.xiaomisum.robotest.model.dto.response.plan;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TestPlanSnapshotNodeRespDTO {

    private UUID id;
    private UUID originalNodeId;
    private UUID parentId;
    private String title;
    private String type;
    private String priority;
    private Boolean isAssociated;
    private String lastResult;
    private UUID lastExecutorId;
    private LocalDateTime lastExecutedAt;
    private Integer sortOrder;
    private Boolean aiGenerated;
    private List<TestPlanSnapshotNodeRespDTO> children;
}
