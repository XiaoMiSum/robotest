package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TestReviewRecordReqDTO {

    @NotBlank(message = "快照节点ID不能为空")
    private String snapshotNodeId;

    @NotBlank(message = "操作类型不能为空")
    private String operationType;

    private String mark;

    private String comment;
}
