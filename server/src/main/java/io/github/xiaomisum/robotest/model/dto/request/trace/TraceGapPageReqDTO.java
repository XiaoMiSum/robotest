package io.github.xiaomisum.robotest.model.dto.request.trace;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

/**
 * 缺口列表查询（GET /api/project/trace/gaps，详设 3.9），type 必填。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceGapPageReqDTO extends PageParam {

    /** uncovered_requirement / orphan_case / unreviewed_case / unscheduled_case */
    @NotBlank(message = "缺口类型不能为空")
    private String type;
}
