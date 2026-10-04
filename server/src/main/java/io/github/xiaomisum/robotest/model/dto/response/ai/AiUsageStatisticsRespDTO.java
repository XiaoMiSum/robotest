package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用量统计（GET /api/ai/usage/statistics，详设 3.7）：汇总 + 分组序列。
 */
@Data
public class AiUsageStatisticsRespDTO {

    private Summary summary;

    private List<SeriesItem> series;

    /**
     * 区间汇总。
     */
    @Data
    public static class Summary {

        private long totalCalls;

        private long failedCalls;

        /** 失败率补数 = (total - failed) / total；无调用为 0 */
        private double successRate;

        private long totalTokens;

        private int avgLatencyMs;

        private BigDecimal totalCost;
    }

    /**
     * 分组序列项：model / scene 分组附 keyName（详设 3.7）。
     */
    @Data
    public static class SeriesItem {

        private String key;

        private String keyName;

        private long calls;

        private long failed;

        private long tokens;

        private int avgLatencyMs;

        private BigDecimal cost;
    }
}
