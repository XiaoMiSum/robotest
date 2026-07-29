package io.github.xiaomisum.robotest.model.dto.request.bug;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class BugUpdateReqDTO {

    private String title;

    private String severity;

    private String priority;

    private String bugType;

    /**
     * 重现步骤（Markdown 原文）
     */
    private String reproSteps;

    private UUID moduleId;

    private String keywords;

    private LocalDate dueDate;

    private UUID assigneeId;

    // status 字段已移除，状态变更请通过 changeBugStatus 接口
}
