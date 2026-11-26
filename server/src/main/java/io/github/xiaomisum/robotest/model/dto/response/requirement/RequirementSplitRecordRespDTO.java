package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 拆解记录列表项（详设 3.10，导入历史页数据源）。
 * 任务态与产出统计为联查派生值，记录自身 status 与 adoptResult 仍是单一事实源。
 */
@Data
public class RequirementSplitRecordRespDTO {

    private UUID id;
    /** document（导入文档） */
    private String sourceType;
    private UUID sourceFileId;
    private String sourceFileName;
    private UUID aiTaskId;
    /** 联查 ai_task */
    private String taskTitle;
    /** 联查 ai_task：running / proposals_ready / closed / aborted */
    private String taskStatus;
    /** 联查最新一次执行 */
    private LatestExecution latestExecution;
    /** 最新执行的提案数 */
    private Integer proposalCount;
    /** 最新执行的已采纳数（含编辑后采纳） */
    private Integer adoptedCount;
    /** pending / adopted / rejected */
    private String status;
    private Map<String, Object> adoptResult;
    /** 联查 ai_task 的提交人 */
    private UUID submittedBy;
    private LocalDateTime createdAt;

    @Data
    public static class LatestExecution {

        private UUID executionId;

        private Integer seq;

        private String status;
    }
}
