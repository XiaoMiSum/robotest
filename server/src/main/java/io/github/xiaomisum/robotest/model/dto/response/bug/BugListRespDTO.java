package io.github.xiaomisum.robotest.model.dto.response.bug;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BugListRespDTO {

    private UUID id;
    private UUID projectId;
    private String title;
    private String severity;
    private String priority;
    private String status;
    private String bugType;
    private String reproSteps;
    private UUID moduleId;
    private String keywords;
    private Boolean confirmed;
    private Integer reopenCount;
    private LocalDateTime lastReopenedAt;
    private String resolution;
    private UUID duplicateOfBugId;
    private LocalDate dueDate;
    private UUID relatedCaseId;
    private UUID relatedPlanId;
    private UserInfo reporter;
    private UserInfo assignee;
    private UserInfo resolvedBy;
    private LocalDateTime resolvedAt;
    private UserInfo rejectedBy;
    private UserInfo closedBy;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class UserInfo {
        private UUID id;
        private String name;
    }
}
