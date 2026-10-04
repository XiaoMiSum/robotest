package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用量聚合查询（GET /api/ai/usage/statistics，详设 3.7）：UTC 时间范围，默认最近 30 天。
 */
@Data
public class AiUsageStatisticsReqDTO {

    /** 起始日期（含，UTC） */
    private LocalDate from;

    /** 结束日期（含，UTC）；to - from ≤ 366 天（否则 1000018120） */
    private LocalDate to;

    /** 分组维度：day（默认）/ model / scene / callType */
    private String groupBy;
}
