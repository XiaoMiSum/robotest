package io.github.xiaomisum.robotest.model.dto.request.bug;

import lombok.Data;

/**
 * 缺陷分析时间范围查询（GET /api/project/bugs/analysis/*，详设 3.2 / 3.3）。
 * 日期为 yyyy-MM-dd 的 UTC 日历日；缺省最近 30 天。
 */
@Data
public class BugAnalysisQueryReqDTO {

    private String from;

    private String to;

    /** 分组对比维度：none（默认）/ module / severity / type */
    private String groupBy;
}
