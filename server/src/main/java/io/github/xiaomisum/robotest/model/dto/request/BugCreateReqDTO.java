package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class BugCreateReqDTO {

    @NotBlank(message = "缺陷标题不能为空")
    private String title;

    @NotBlank(message = "严重等级不能为空")
    private String severity;

    @NotBlank(message = "优先级不能为空")
    private String priority;

    private String description;

    private UUID assigneeId;

    private UUID relatedCaseId;

    private UUID relatedPlanId;
}
