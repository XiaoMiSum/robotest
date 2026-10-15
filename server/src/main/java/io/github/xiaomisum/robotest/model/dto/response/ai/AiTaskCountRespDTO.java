package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 进行中任务计数（详设 3.6.3：GET /api/ai/tasks/running-count，顶栏指示器轮询专用）。
 */
@Data
public class AiTaskCountRespDTO {

    /** pending + running 任务数 */
    private Long count;
}
