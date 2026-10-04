package io.github.xiaomisum.robotest.model.dto.request.trace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 受影响项处置（PATCH /api/project/trace/impact-items/{edgeId}，详设 3.10）：
 * 处置只改变边的标记，不修改下游内容。
 */
@Data
public class TraceImpactPatchReqDTO {

    /** pending / regenerate / re_review / no_impact */
    @NotBlank(message = "处置标记不能为空")
    private String disposition;

    /** no_impact 必填 */
    @Size(max = 500, message = "处置理由不能超过 500 字符")
    private String reason;
}
