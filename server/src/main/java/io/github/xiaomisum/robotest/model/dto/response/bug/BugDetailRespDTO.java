package io.github.xiaomisum.robotest.model.dto.response.bug;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 缺陷详情响应 DTO
 */
@Data
public class BugDetailRespDTO {

    private UUID id;
    private String title;
    private String severity;
    private String priority;
    private String status;
    private String bugType;
    /**
     * 重现步骤（Markdown 原文）
     */
    private String reproSteps;
    private UUID moduleId;
    private String moduleName;
    private String keywords;
    private LocalDate dueDate;
    private Boolean confirmed;
    private Integer reopenCount;
    private LocalDateTime lastReopenedAt;
    private String resolution;
    private UUID duplicateOfBugId;
    private UserInfo resolvedBy;
    private LocalDateTime resolvedAt;
    private UserInfo closedBy;
    private LocalDateTime closedAt;
    private UserInfo reporter;
    private UserInfo assignee;
    private UUID relatedCaseId;
    private UUID relatedPlanId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 最近的操作日志（可选，最多返回 10 条）
     */
    private List<BugLogRespDTO> recentLogs;

    @Data
    public static class UserInfo {
        private UUID id;
        private String name;
    }
}
