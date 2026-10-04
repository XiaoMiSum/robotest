package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用量汇总聚合行（Mapper 结果映射，详设 3.7 summary）。
 */
@Data
public class AiUsageSummaryRowDTO {

    private Long totalCalls;

    private Long failedCalls;

    private Long totalTokens;

    private BigDecimal avgLatencyMs;

    private BigDecimal totalCost;
}
