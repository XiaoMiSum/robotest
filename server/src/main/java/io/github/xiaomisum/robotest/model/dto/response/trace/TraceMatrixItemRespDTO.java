package io.github.xiaomisum.robotest.model.dto.response.trace;

import lombok.Data;

import java.util.UUID;

/**
 * 矩阵视图行（详设 3.2）：需求 × 边计数矩阵的一行。
 */
@Data
public class TraceMatrixItemRespDTO {

    private UUID requirementId;

    private String code;

    private String title;

    /** 需求状态：draft / confirmed / changed / archived */
    private String status;

    /** 覆盖状态：covered / partial / uncovered / pending（无记录为 pending） */
    private String coverageStatus;

    /** 按目标类型统计的有效边数（排除 detached） */
    private EdgeCounts edgeCounts;

    /** 待重新确认边数：status = stale 或版本比对不一致（详设 4.1 版本感知） */
    private int staleCount;

    private int conflictCount;

    @Data
    public static class EdgeCounts {
        private int module;
        private int document;
        private int testCase;
        private int review;
        private int plan;
    }
}
