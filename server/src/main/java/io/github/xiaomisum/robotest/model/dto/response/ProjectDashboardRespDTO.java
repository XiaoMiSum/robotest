package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProjectDashboardRespDTO {

    private Long caseCount;
    private Long activeReviewCount;
    private Long activePlanCount;
    private Long openBugCount;
    private List<RecentItem> recentReviews;
    private List<RecentItem> recentPlans;

    @Data
    public static class RecentItem {
        private String id;
        private String title;
        private String status;
        private LocalDateTime createdAt;
    }
}
