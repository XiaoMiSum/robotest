package io.github.xiaomisum.robotest.model.dto.request.trace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 链路视图查询（GET /api/project/trace/chain，详设 3.3）。
 * direction 未定义参数非法错误码，非法取值按详设默认值兜底（down）。
 */
@Data
public class TraceChainReqDTO {

    /** 起点类型：requirement / module / mindmap_document / test_case / test_review / test_plan */
    @NotBlank(message = "起点类型不能为空")
    private String sourceType;

    @NotNull(message = "起点 ID 不能为空")
    private UUID sourceId;

    /** down（默认，向下游）/ up（向上游回溯）/ both */
    private String direction;
}
