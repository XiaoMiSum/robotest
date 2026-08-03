package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiPlanOrderReasonReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPlanOrderComputeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPlanOrderQueryRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPlanOrderReasonRespDTO;

import java.util.UUID;

/**
 * AI 执行顺序推荐（详细设计 3.4 / 4.4）：确定性评分 + 按需 LLM 理由生成。
 */
public interface AiPlanOrderRecommendService {

    /**
     * 计算推荐（3.4.1）：同步计算立即返回任务标识与推荐结果，重复计算覆盖旧记录。
     * 仅计划执行人（TestPlan.executorId）可调用，计划需已关联快照（6012）。
     */
    AiPlanOrderComputeRespDTO compute(UUID userId, UUID workspaceId, UUID projectId, UUID planId);

    /**
     * 查询推荐结果（3.4.2）：返回最近一次 success 任务的结果与失效标记
     * （result.planSyncedAt 与当前 snapshot_synced_at 不相等即 stale，含 NULL 口径）。
     */
    AiPlanOrderQueryRespDTO query(UUID userId, UUID workspaceId, UUID projectId, UUID planId);

    /**
     * 生成推荐理由（3.4.3）：LLM 基于该条 factors 生成一句话理由并回填 result 对应 item，
     * 重复请求直接返回已生成理由（缓存复用）；LLM 不参与排序。
     */
    AiPlanOrderReasonRespDTO reason(UUID userId, UUID workspaceId, UUID projectId, UUID planId,
                                    AiPlanOrderReasonReqDTO reqDTO);
}
