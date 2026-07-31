package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiReviewSummaryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewSummaryRespDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * AI 评审辅助（US-AI-006 评审摘要）：statistics 由 SQL 精确计算，summaryMarkdown 由 LLM 流式产出。
 */
public interface AiReviewSummaryService {

    /**
     * 生成评审摘要（SSE：statistics 帧即时返回 + delta 流式 Markdown + done 帧完整结构）。
     * 结果覆盖式落库到 ai_analysis_task（type=review_summary）。
     */
    SseEmitter generateSummary(UUID userId, UUID workspaceId, UUID projectId, UUID reviewId,
            AiReviewSummaryReqDTO reqDTO);

    /**
     * 查询最近一次成功摘要（3.2.2），无则返回 null。
     */
    AiReviewSummaryRespDTO getSummary(UUID reviewId, UUID userId);
}
