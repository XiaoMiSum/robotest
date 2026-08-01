package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiPriorityRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPriorityRecommendRespDTO;

import java.util.UUID;

/**
 * AI 优先级推荐（US-AI-003）：规则前置 + LLM 兜底，规则命中不经 LLM。
 */
public interface AiPriorityRecommendService {

    /**
     * 推荐用例优先级：规则命中直接返回（source=rule）；未命中走 LLM 同步调用
     * （超时 5s），失败返回 priority=null（非侵入原则，前端静默忽略）。
     */
    AiPriorityRecommendRespDTO recommend(UUID userId, UUID workspaceId, UUID projectId,
            AiPriorityRecommendReqDTO reqDTO);
}
