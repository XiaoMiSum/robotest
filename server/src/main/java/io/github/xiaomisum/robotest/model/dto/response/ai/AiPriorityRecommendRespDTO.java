package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * AI 优先级推荐响应（详细设计 3.3.1）。
 *
 * <p>
 * priority 可空：规则未命中且 LLM 失败/超时时返回 null（前端静默忽略，非侵入原则）；
 * source 标识推荐来源：rule（关键词规则命中）/ llm（模型兜底）。
 * </p>
 */
@Data
public class AiPriorityRecommendRespDTO {

    /** 推荐优先级（P0-P3），无推荐时为 null */
    private String priority;

    /** 推荐来源：rule / llm */
    private String source;
}
