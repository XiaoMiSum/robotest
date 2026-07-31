package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 评审摘要（详细设计 2.2.2）：statistics 由 SQL 精确计算，summaryMarkdown 为 LLM 流式产出。
 *
 * <p>
 * dimensionDist（维度分布）来源于评审一键检查任务结果（US-AI-005，梯队二），本迭代暂不输出。
 * </p>
 */
@Data
public class AiReviewSummaryRespDTO {

    private Statistics statistics;

    private String summaryMarkdown;

    private LocalDateTime generatedAt;

    @Data
    public static class Statistics {
        private long totalCases;
        private long passCount;
        private long failCount;
        private long pendingCount;
        private double passRate;
        private List<FailByDocument> failByDocument;
    }

    @Data
    public static class FailByDocument {
        private String documentName;
        private long failCount;
    }
}
