package io.github.xiaomisum.robotest.service.domain.review;

import java.util.UUID;

/**
 * 评审结论事件（06 §5.2）：completeReview 发布，verdict 由评审域 ReviewConclusionEvaluator
 * 按节点 lastMark 确定性判定（不经 LLM）；AI 域消费者在事务提交后生成 review 级结论，
 * 落 AiAnalysisTask 结果字段（type=review_conclusion）。
 */
public record ReviewConclusionEvent(UUID reviewId, String verdict, String reason) {
}