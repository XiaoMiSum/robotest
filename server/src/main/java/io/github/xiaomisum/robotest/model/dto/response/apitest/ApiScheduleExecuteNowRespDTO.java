package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 立即执行响应（定时任务详细设计 3.1.6）：executionId 为执行记录 ID；
 * 场景执行任务返回 running，导入任务同步完成返回最终状态
 */
@Data
@Builder
public class ApiScheduleExecuteNowRespDTO {

    private UUID executionId;
    private String status;

}
