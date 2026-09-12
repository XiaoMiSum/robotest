package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 立即执行响应（定时任务详细设计 3.1.6）：测试计划任务 executionId 为 taskId（批量场景句柄），
 * status=running；接口同步任务同步完成返回最终状态，executionId 为导入记录 ID
 */
@Data
@Builder
public class ApiScheduleExecuteNowRespDTO {

    private UUID executionId;
    private String status;

}
