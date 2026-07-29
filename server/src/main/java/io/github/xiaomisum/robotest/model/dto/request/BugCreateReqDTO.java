package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class BugCreateReqDTO {

    @NotBlank(message = "缺陷标题不能为空")
    private String title;

    @NotBlank(message = "严重等级不能为空")
    private String severity;

    @NotBlank(message = "优先级不能为空")
    private String priority;

    @NotBlank(message = "缺陷类型不能为空")
    private String bugType;

    /**
     * 重现步骤（Markdown 原文）
     */
    private String reproSteps;

    private UUID moduleId;

    private String keywords;

    private LocalDate dueDate;

    @NotNull(message = "处理人不能为空")
    private UUID assigneeId;

    private UUID relatedCaseId;

    private UUID relatedPlanId;
}
