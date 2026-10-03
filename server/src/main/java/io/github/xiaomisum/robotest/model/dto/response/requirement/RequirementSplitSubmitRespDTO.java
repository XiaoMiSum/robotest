package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.util.UUID;

/**
 * 条目内 AI 拆分提交回执（详设 3.9）：提交即返回任务入口，进度与审核在任务详情页完成。
 */
@Data
public class RequirementSplitSubmitRespDTO {

    private UUID taskId;

    private UUID splitRecordId;

    /** 任务初始状态（pending），后续经任务资源轮询 */
    private String status;
}
