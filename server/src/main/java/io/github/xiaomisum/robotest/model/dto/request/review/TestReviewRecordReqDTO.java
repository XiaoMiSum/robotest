package io.github.xiaomisum.robotest.model.dto.request.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TestReviewRecordReqDTO {

    @NotNull(message = "快照节点ID不能为空")
    private UUID snapshotNodeId;

    @NotBlank(message = "操作类型不能为空")
    private String operationType;

    private String mark;

    private String comment;
}
