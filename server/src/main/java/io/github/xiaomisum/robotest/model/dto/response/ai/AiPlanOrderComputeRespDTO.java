package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.UUID;

/**
 * 执行顺序推荐计算响应（3.4.1）：同步计算立即返回任务标识与推荐结果。
 */
@Data
public class AiPlanOrderComputeRespDTO {

    private UUID taskId;
    private AiPlanOrderRecommendRespDTO result;
}
