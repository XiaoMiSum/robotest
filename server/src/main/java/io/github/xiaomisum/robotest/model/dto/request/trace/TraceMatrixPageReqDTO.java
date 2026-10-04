package io.github.xiaomisum.robotest.model.dto.request.trace;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

/**
 * 矩阵视图查询（GET /api/project/trace/matrix，详设 3.2）：
 * 需求维度分页，覆盖状态与关键词筛选联动需求侧查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceMatrixPageReqDTO extends PageParam {

    /** 需求状态筛选：draft / confirmed / changed / archived */
    private String requirementStatus;

    /** 覆盖状态筛选：covered / partial / uncovered / pending（pending = 待分析） */
    private String coverage;

    /** 需求编号前缀 / 后缀或标题包含匹配（命中规则见需求管理详设 4.3） */
    private String keyword;
}
