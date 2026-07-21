package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class TestPlanRecordReqDTO {

    @NotBlank(message = "快照节点ID不能为空")
    private UUID snapshotNodeId;

    @NotBlank(message = "执行结果不能为空")
    private String result;

    private String note;
}
