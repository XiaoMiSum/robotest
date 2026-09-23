package io.github.xiaomisum.robotest.model.dto.response.admin;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 数据概览统计（口径见 docs/详细设计/系统管理模块详细设计说明书.md §3.2） */
@Data
public class DashboardStatsRespDTO {

    private LocalDateTime generatedAt;
    private UsersStats users;
    private WorkspacesStats workspaces;
    private ProjectsStats projects;
    private ActivityStats activity;
    private List<DailyActiveUsers> activeUsersDaily;

    @Data
    public static class UsersStats {
        private long total;
        private long weekNew;
        private long enabled;
        private long disabled;
        private long locked;
    }

    @Data
    public static class WorkspacesStats {
        private long total;
        private long active;
        private long dissolved;
    }

    @Data
    public static class ProjectsStats {
        private long total;
        private long weekNew;
    }

    @Data
    public static class ActivityStats {
        private long todayLogins;
    }

    @Data
    public static class DailyActiveUsers {
        private String date;
        private long count;
    }
}
