package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ProjectDashboardRespDTO {

    private Long caseCount;
    private Long activeReviewCount;
    private Long activePlanCount;
    private Long openBugCount;
    private List<RecentItem> recentReviews;
    private List<RecentItem> recentPlans;
    private List<RecentBugItem> recentBugs;

    @Data
    public static class RecentItem {
        private UUID id;
        private String title;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    public static class RecentBugItem {
        private UUID id;
        private String title;
        private String severity;
        private String priority;
        private String status;
        private String assignee;
        private LocalDateTime createdAt;
    }
}
