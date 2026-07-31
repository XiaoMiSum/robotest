package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;

/**
 * AI 调用量统计（ai_invocation_log 聚合）
 */
@Data
public class AiStatisticsRespDTO {

    private Long totalCalls;
    private Long totalTokens;
    private Long failedCalls;
    private List<Item> items;

    @Data
    public static class Item {
        /** groupBy=functionType 为功能类型，=workspace 为空间名称，=day 为日期 */
        private String key;
        private Long calls;
        private Long tokens;
        private Long avgDurationMs;
        private Long failed;
    }
}
