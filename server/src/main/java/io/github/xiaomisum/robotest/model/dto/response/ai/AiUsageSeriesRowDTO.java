package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用量分组聚合行（Mapper 结果映射，详设 3.7 series）。
 */
@Data
public class AiUsageSeriesRowDTO {

    /** 分组键：日期（day）/ modelId / scene / callType */
    private String key;

    private Long calls;

    private Long failed;

    private Long tokens;

    private BigDecimal avgLatencyMs;

    private BigDecimal cost;
}
