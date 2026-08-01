package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewCheckStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;

import java.util.UUID;

/**
 * AI 评审一键检查（US-AI-005）：后台异步任务，result 分批累计写入（2.2.1/4.1）
 */
public interface AiReviewCheckService {

    /**
     * 发起检查：仅发起人（2001）+ 评审 in_progress（6012）+ 无进行中同类任务（6005）
     */
    AiReviewCheckStartRespDTO startCheck(UUID userId, UUID workspaceId, UUID projectId, UUID reviewId);

    /**
     * 查询最近一次检查任务（含 status/progress/result，running/cancelled 亦含部分结果；3.1.2）
     */
    AiTaskRespDTO getCheckResult(UUID userId, UUID projectId, UUID reviewId);
}
