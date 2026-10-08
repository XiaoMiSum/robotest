package io.github.xiaomisum.robotest.model.dto.response.bug;

import lombok.Data;

import java.util.List;

/**
 * 缺陷质量度量响应（GET /api/project/bugs/analysis/metrics，详设 3.3）。
 * 比率与分布的分母统一为区间内新增缺陷，分母为 0 时比率为 0。
 */
@Data
public class BugMetricsRespDTO {

    private FixDuration fixDuration;

    /** 重开率 = 激活次数 ≥ 1 的缺陷 / 区间内新增缺陷 */
    private Double reopenRate;

    /** 重复缺陷占比 = 解决方案为「重复缺陷」的缺陷 / 区间内新增缺陷 */
    private Double duplicateRate;

    private List<DistItem> severityDist;

    private List<DistItem> moduleDist;

    private List<DistItem> typeDist;

    @Data
    public static class FixDuration {

        private Double avgHours;

        private Double p50Hours;

        private Double p90Hours;

        /** 样本量：修复时间落在区间内的缺陷数 */
        private Integer sample;
    }

    @Data
    public static class DistItem {

        /** severity / type 为枚举值；module 为模块名称，未指定为「未指定模块」 */
        private String key;

        private Long count;
    }
}
