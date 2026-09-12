package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 定时任务列表项（定时任务详细设计 3.1.1）；nextExecutions 仅启用任务计算
 */
@Data
@Builder
public class ApiSchedulePageItemRespDTO {

    private UUID id;
    private String taskType;
    private String name;
    private String description;
    /** 历史遗留字段（旧版绑定对象），新任务为 null */
    private UUID boundObjectId;
    private String boundObjectName;
    private String executionScope;
    private List<UUID> moduleIds;
    private List<UUID> sceneIds;
    private String openapiUrl;
    private UUID environmentId;
    private String environmentName;
    private String cronExpression;
    private Boolean enabled;
    private String lastExecutionStatus;
    private LocalDateTime lastExecutionAt;
    private List<LocalDateTime> nextExecutions;
    private LocalDateTime createdAt;

}
