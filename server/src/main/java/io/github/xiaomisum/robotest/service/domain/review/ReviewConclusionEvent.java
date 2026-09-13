package io.github.xiaomisum.robotest.service.domain.review;

import java.util.UUID;

/**
 * 评审结论事件骨架：AI 结论产出后供订阅方写回（06 §5.2）。
 * 本轮仅建类不接线，AiInference 写回链路留待后续增量；
 * 评审结论当前仍落 AiAnalysisTask 结果字段，不写评审域表（03 D3）。
 */
public record ReviewConclusionEvent(UUID reviewId, String verdict, String reason) {
}