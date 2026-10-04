package io.github.xiaomisum.robotest.model.dto.request.trace;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

import java.util.UUID;

/**
 * 受影响项查询（GET /api/project/trace/impact-items，详设 3.10）：只列已纳入影响分析的边
 * （disposition 非空）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceImpactItemPageReqDTO extends PageParam {

    @NotNull(message = "需求 ID 不能为空")
    private UUID requirementId;

    /** 处置标记筛选：pending / regenerate / re_review / no_impact */
    private String disposition;
}
