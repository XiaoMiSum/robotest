package io.github.xiaomisum.robotest.model.dto.request.trace;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

/**
 * 覆盖状态查询（GET /api/project/trace/coverage，详设 3.7）。
 * requirementIds 为 uuid 逗号分隔，最多 100 个；不传时按当前项目全量分页。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceCoveragePageReqDTO extends PageParam {

    private String requirementIds;
}
