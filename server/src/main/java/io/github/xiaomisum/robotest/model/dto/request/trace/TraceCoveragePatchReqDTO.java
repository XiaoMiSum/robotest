package io.github.xiaomisum.robotest.model.dto.request.trace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 覆盖结论人工修正（PATCH /api/project/trace/coverage/{requirementId}，详设 3.8）。
 */
@Data
public class TraceCoveragePatchReqDTO {

    /** covered / partial / uncovered */
    @NotBlank(message = "覆盖状态不能为空")
    private String coverageStatus;

    /** 修正说明，落 reviewed_note */
    @Size(max = 500, message = "修正说明不能超过 500 字符")
    private String note;
}
