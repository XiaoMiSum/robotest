package io.github.xiaomisum.robotest.model.dto.response.trace;

import lombok.Data;

import java.util.UUID;

/**
 * 缺口项（详设 3.9）：按缺口类型给出引导动作。
 */
@Data
public class TraceGapRespDTO {

    /** 缺口对象类型：requirement（未覆盖需求）/ test_case（孤儿、未评审、未入计划用例） */
    private String targetType;

    private UUID targetId;

    /** 标题：需求为「REQ-001 标题」，用例为节点标题 */
    private String title;

    /** 仅需求缺口回填，用例缺口为 null */
    private String coverageStatus;

    /** generate（发起生成）/ review（发起评审）/ schedule（加入计划） */
    private String suggestedAction;
}
