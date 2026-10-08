package io.github.xiaomisum.robotest.model.dto.response.bug;

import lombok.Data;

import java.util.List;

/**
 * 缺陷趋势查询响应（GET /api/project/bugs/analysis/trends，详设 3.2）。
 */
@Data
public class BugTrendsRespDTO {

    /** 横轴：UTC 日期（yyyy-MM-dd），区间逐日 */
    private List<String> axis;

    private List<Series> series;

    /** 实际生效的分组维度（none / module / severity / type） */
    private String groupBy;

    @Data
    public static class Series {

        /** 分组键：all=全部；module=模块 id；severity / type=枚举值，未指定为 none */
        private String key;

        private String label;

        private List<Integer> created;

        private List<Integer> closed;

        private List<Integer> active;
    }
}
