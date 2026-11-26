package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.UUID;

/**
 * 供拆解记录列表联查的任务摘要（详设 3.10）：跨层只读入口为 AiTaskService，
 * 业务域不得直读 ai_task / 执行 / 提案表。
 */
@Data
public class AiTaskSplitSummaryRespDTO {

    private UUID taskId;

    private String title;

    private String status;

    private UUID submittedBy;

    private LatestExecution latestExecution;

    private Integer proposalCount;

    private Integer adoptedCount;

    @Data
    public static class LatestExecution {

        private UUID executionId;

        private Integer seq;

        private String status;
    }
}
