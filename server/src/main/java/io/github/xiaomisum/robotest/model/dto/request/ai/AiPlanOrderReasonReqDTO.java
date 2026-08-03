package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 执行顺序推荐理由生成请求（POST /api/project/ai/plans/:id/order-reason，同步，3.4.3）。
 */
@Data
public class AiPlanOrderReasonReqDTO {

    /** 推荐结果 items 中的快照节点标识 */
    @NotNull(message = "快照节点标识不能为空")
    private UUID snapshotNodeId;
}
