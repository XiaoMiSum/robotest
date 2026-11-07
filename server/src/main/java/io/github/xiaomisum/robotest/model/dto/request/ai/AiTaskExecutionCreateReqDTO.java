package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 再次执行（POST /api/ai/tasks/{taskId}/executions，详设 3.6.4）：
 * 任务 aborted（终止后重跑）或 proposals_ready（附反馈重新生成）时可发起。
 */
@Data
public class AiTaskExecutionCreateReqDTO {

    /** 重新生成反馈（可选）：并入任务输入随新执行下发给模型 */
    @Size(max = 2000, message = "反馈不能超过 2000 字符")
    private String feedback;
}
