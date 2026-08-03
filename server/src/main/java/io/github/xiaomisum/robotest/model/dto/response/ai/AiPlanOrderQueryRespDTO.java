package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 执行顺序推荐结果查询响应（3.4.2）。
 */
@Data
public class AiPlanOrderQueryRespDTO {

    /** true 表示计划快照在计算后重新同步过，结果已失效需重算 */
    private Boolean stale;
    private AiPlanOrderRecommendRespDTO result;
}
