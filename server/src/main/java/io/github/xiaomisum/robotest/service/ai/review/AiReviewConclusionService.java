package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiReviewConclusionReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * AI 评审结论（06 §5.2 手动路径）：SSE 同步生成（verdict/statistics 帧即时返回 + done 帧完整结论）；
 * 结果覆盖式落库到 ai_analysis_task（type=review_conclusion）。
 */
public interface AiReviewConclusionService {

    /**
     * 手动触发/重新生成评审结论（SSE）。仅发起人 + 评审已完成 + 无进行中同类任务（6005）；
     * 失败由网关对校验错误自动重试 1 次，仍失败按 6003 错误帧返回。
     */
    SseEmitter generateConclusion(UUID userId, UUID workspaceId, UUID projectId, UUID reviewId,
            AiReviewConclusionReqDTO reqDTO);

    /**
     * 查询最近一次评审结论任务（result 含 verdict/reason/keyFindings/statistics），无则返回 null。
     */
    AiTaskRespDTO getConclusion(UUID userId, UUID projectId, UUID reviewId);
}