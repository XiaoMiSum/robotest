package io.github.xiaomisum.robotest.service.ai.recommend;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiCasePlanRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiCasePlanRecommendRespDTO;

import java.util.UUID;

/**
 * AI 用例规划智能推荐（详细设计 3.5 / 4.5）：基于需求上下文语义检索，推荐应纳入当前评审/计划的用例清单。
 */
public interface AiCasePlanRecommendService {

    /**
     * 用例规划推荐：需求描述块语义匹配（降级态关键词匹配），排除已纳入用例，截断 50 条按 score 降序，
     * 最后一次 LLM 调用为全部结果批量生成一句话 reason。
     */
    AiCasePlanRecommendRespDTO recommend(UUID userId, UUID workspaceId, UUID projectId,
                                         AiCasePlanRecommendReqDTO reqDTO);
}
