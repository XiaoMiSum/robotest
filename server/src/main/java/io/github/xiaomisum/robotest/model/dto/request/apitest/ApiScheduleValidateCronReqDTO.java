package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 校验 Cron 表达式（定时任务详细设计 3.1.8）
 */
@Data
public class ApiScheduleValidateCronReqDTO {

    @NotBlank
    private String cronExpression;

}
