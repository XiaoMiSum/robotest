package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * 缺陷统计响应 DTO
 */
@Data
public class BugStatisticsRespDTO {

    /**
     * 缺陷总数
     */
    private long total;

    /**
     * 按状态统计：{ "new": 5, "assigned": 3, ... }
     */
    private Map<String, Long> byStatus;

    /**
     * 按严重等级统计：{ "critical": 2, "major": 5, ... }
     */
    private Map<String, Long> bySeverity;

    /**
     * 按优先级统计：{ "high": 3, "medium": 4, ... }
     */
    private Map<String, Long> byPriority;

    /**
     * 按处理人统计：{ "user_id": 6, ... }
     */
    private Map<UUID, Long> byAssignee;

    /**
     * 按报告人统计：{ "user_id": 8, ... }
     */
    private Map<UUID, Long> byReporter;
}
