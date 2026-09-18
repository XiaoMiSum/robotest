package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;

import java.util.UUID;

/**
 * AI 生成评审结论请求（POST /api/project/ai/reviews/{id}/conclusion，SSE，body 可空）。
 * 交互式功能允许用户临时切换对话模型；缺省或失效回退系统默认模型。
 */
@Data
public class AiReviewConclusionReqDTO {

    /** 用户选择的对话模型，可空 */
    private UUID modelId;
}