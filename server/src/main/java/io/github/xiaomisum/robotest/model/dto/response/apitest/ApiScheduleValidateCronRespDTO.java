package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cron 校验结果（定时任务详细设计 3.1.8）；非法时 valid=false 且其余字段为 null
 */
@Data
@Builder
public class ApiScheduleValidateCronRespDTO {

    private boolean valid;
    private String description;
    private List<LocalDateTime> nextExecutions;

}
