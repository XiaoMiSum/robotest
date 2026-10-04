package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用量下钻行（GET /api/ai/usage/tasks，详设 3.7）：点击跳任务详情（3.6.3）。
 */
@Data
public class AiUsageTaskRespDTO {

    private UUID taskId;

    /** 任务类型（usage 行无任务时为 null，如下钻中含向量调用） */
    private String type;

    /** success / failed */
    private String status;

    /** 模型配置名（向量调用无模型行为 null） */
    private String modelName;

    private String scene;

    private Integer totalTokens;

    private Integer latencyMs;

    private LocalDateTime createdAt;
}
