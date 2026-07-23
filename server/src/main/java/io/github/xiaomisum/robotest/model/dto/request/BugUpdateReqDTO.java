package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class BugUpdateReqDTO {

    private String title;

    private String severity;

    private String priority;

    private String description;

    private UUID assigneeId;

    // status 字段已移除，状态变更请通过 changeBugStatus 接口
}
