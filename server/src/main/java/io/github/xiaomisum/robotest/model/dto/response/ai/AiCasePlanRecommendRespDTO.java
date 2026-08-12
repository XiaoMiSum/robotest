package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * AI 用例规划智能推荐响应（3.5）：按 score 降序、结果上限 50 条、排除已纳入用例。
 */
@Data
public class AiCasePlanRecommendRespDTO {

    /** 语义降级：true 时 items 为关键词模式结果（score 0.6 仅作展示排序用） */
    private boolean semanticDegraded;

    private List<Item> items;

    @Data
    public static class Item {

        private UUID caseNodeId;

        private String title;

        private String modulePath;

        /** semantic（语义匹配；降级态关键词匹配亦归此值） */
        private String matchType;

        private double score;

        /** 一句话推荐理由，可空：理由生成失败或长度不匹配时整体置空，不影响清单可用性（4.5） */
        private String reason;
    }
}
