package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;

import java.util.UUID;

/**
 * AI 生成评审摘要请求（POST /api/project/ai/reviews/{id}/summary，SSE）。
 *
 * <p>
 * 摘要为交互式功能，允许用户临时切换对话模型（详细设计 3.2.1 / 基础设施 4.11）；
 * 缺省或失效回退系统默认模型。
 * </p>
 */
@Data
public class AiReviewSummaryReqDTO {

    /** 用户选择的对话模型，可空 */
    private UUID modelId;
}
