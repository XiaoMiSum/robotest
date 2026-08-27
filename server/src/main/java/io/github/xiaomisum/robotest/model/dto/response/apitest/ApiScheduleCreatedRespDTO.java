package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 创建任务响应（定时任务详细设计 3.1.2）
 */
@Data
@Builder
public class ApiScheduleCreatedRespDTO {

    private UUID id;
    private LocalDateTime nextExecutionAt;

}
