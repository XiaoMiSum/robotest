package io.github.xiaomisum.robotest.model.dto.request.trace;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

import java.util.UUID;

/**
 * 追溯边列表查询（GET /api/project/trace/edges，详设 3.4），全部筛选条件可选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceEdgePageReqDTO extends PageParam {

    private String edgeType;

    private String status;

    private String sourceType;

    private UUID sourceId;

    private String targetType;

    private UUID targetId;
}
