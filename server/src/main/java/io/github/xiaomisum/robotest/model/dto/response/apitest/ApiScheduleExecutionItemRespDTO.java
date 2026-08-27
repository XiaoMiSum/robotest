package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 定时任务执行记录项（定时任务详细设计 3.1.7）；
 * importSummary 冗余自关联导入记录，展示新增/更新/失败数
 */
@Data
public class ApiScheduleExecutionItemRespDTO {

    private UUID id;
    private String triggerType;
    private String status;
    private String errorMessage;
    private UUID reportId;
    private UUID importRecordId;
    private Map<String, Object> importSummary;
    private LocalDateTime triggeredAt;
    private Integer durationMs;

}
