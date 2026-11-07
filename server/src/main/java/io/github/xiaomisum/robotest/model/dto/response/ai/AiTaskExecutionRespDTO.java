package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * 任务执行摘要（详设 3.6.3 latestExecution / 3.6.2 提交响应）：
 * 执行态、步数、预算快照与消耗、计划步骤摘要、终止原因。
 */
@Data
public class AiTaskExecutionRespDTO {

    private UUID executionId;

    /** 同任务内第几次执行（首次 1，再次执行自增） */
    private Integer seq;

    /** running / waiting_confirmation / succeeded / aborted */
    private String status;

    /** 已执行步数 */
    private Integer steps;

    /** 预算快照 {maxSteps, maxDurationSeconds, maxTokens, maxCost} */
    private Map<String, Object> budget;

    /** 执行消耗 */
    private Consumed consumed;

    /** 计划步骤快照（plan，展示参考，不作为执行依据） */
    private Map<String, Object> plan;

    /** 执行级终止原因（aborted 时携带） */
    private AiTerminateReasonRespDTO terminateReason;

    @Data
    public static class Consumed {

        private Integer durationMs;

        private Integer tokensIn;

        private Integer tokensOut;

        private BigDecimal cost;
    }
}
