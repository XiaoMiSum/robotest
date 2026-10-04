package io.github.xiaomisum.robotest.model.dto.request.trace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 追溯边修正（PATCH /api/project/trace/edges/{edgeId}，详设 3.6）：按 action 分发。
 */
@Data
public class TraceEdgePatchReqDTO {

    /** confirm / reattach / detach / restore（见 Constants.TraceEdgeAction） */
    @NotBlank(message = "修正动作不能为空")
    private String action;

    /** reattach 时的新目标类型与 ID */
    private String targetType;

    private UUID targetId;

    @Size(max = 64, message = "目标版本不能超过 64 字符")
    private String targetVersion;

    /** detach 必填：断开理由（留痕展示） */
    @Size(max = 500, message = "断开理由不能超过 500 字符")
    private String reason;
}
