package io.github.xiaomisum.robotest.model.dto.response.trace;

import lombok.Data;

import java.util.UUID;

/**
 * 受影响项（详设 3.10）：由 impact_analysis 任务遍历产出并逐项写入处置标记。
 */
@Data
public class TraceImpactItemRespDTO {

    private UUID edgeId;

    /** 受影响对象（用例 / 模块 / 脑图文档 / 评审 / 计划） */
    private TraceNodeRefRespDTO target;

    /** 到达该受影响项的边类型：derivation / case_snapshot */
    private String impactType;

    private String disposition;

    private String reason;

    private UUID disposedBy;
}
