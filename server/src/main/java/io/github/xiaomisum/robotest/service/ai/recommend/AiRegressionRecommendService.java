package io.github.xiaomisum.robotest.service.ai.recommend;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiRegressionRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiRegressionRecommendRespDTO;

import java.util.UUID;

/**
 * AI 回归测试用例子集推荐（详细设计 3.5 / 4.5）：变更模块 + 语义检索召回回归候选子集。
 */
public interface AiRegressionRecommendService {

    /**
     * 回归子集推荐：模块名匹配 + 语义匹配（降级态关键词匹配）合并去重，截断 50 条按 score 降序，
     * 最后一次 LLM 调用为全部结果批量生成一句话 reason。
     */
    AiRegressionRecommendRespDTO recommend(UUID userId, UUID workspaceId, UUID projectId,
                                           AiRegressionRecommendReqDTO reqDTO);
}
