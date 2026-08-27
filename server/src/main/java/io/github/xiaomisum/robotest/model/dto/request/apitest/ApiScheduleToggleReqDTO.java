package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启停定时任务（定时任务详细设计 3.1.4）
 */
@Data
public class ApiScheduleToggleReqDTO {

    @NotNull
    private Boolean enabled;

}
