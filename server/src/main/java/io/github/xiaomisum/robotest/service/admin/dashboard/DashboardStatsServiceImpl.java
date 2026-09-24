package io.github.xiaomisum.robotest.service.admin.dashboard;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.admin.DashboardStatsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.DashboardStatsRespDTO.*;
import io.github.xiaomisum.robotest.repository.admin.AuditLogMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardStatsServiceImpl implements DashboardStatsService {

    /** 趋势图固定窗口：近 14 个自然日（含今日），与交互设计 §2.3 对应 */
    private static final int ACTIVE_USER_DAYS = 14;

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private AuditLogMapper auditLogMapper;

    @Override
    public DashboardStatsRespDTO getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekAgo = now.minusDays(7);

        DashboardStatsRespDTO dto = new DashboardStatsRespDTO();
        // 对外统一下发 UTC 墙钟，前端按浏览器时区还原。
        dto.setGeneratedAt(now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
        dto.setUsers(buildUsers(weekAgo));
        dto.setWorkspaces(buildWorkspaces());
        dto.setProjects(buildProjects(weekAgo));
        dto.setActivity(buildActivity(dayStart, now));
        dto.setActiveUsersDaily(buildActiveUsersDaily(now.toLocalDate()));
        return dto;
    }

    private UsersStats buildUsers(LocalDateTime weekAgo) {
        Map<String, Long> byStatus = userMapper.countGroupByStatus();
        UsersStats stats = new UsersStats();
        stats.setEnabled(byStatus.getOrDefault(Constants.Status.ACTIVE, 0L));
        stats.setDisabled(byStatus.getOrDefault(Constants.Status.DISABLED, 0L));
        stats.setLocked(byStatus.getOrDefault(Constants.Status.LOCKED, 0L));
        stats.setWeekNew(userMapper.countCreatedSince(weekAgo));
        // total 取分组求和：覆盖任意状态取值，保证 enabled+disabled+locked = total
        stats.setTotal(stats.getEnabled() + stats.getDisabled() + stats.getLocked());
        return stats;
    }

    private WorkspacesStats buildWorkspaces() {
        Map<String, Long> byStatus = workspaceMapper.countGroupByStatus();
        WorkspacesStats stats = new WorkspacesStats();
        stats.setActive(byStatus.getOrDefault(Constants.Status.ACTIVE, 0L));
        stats.setDissolved(byStatus.getOrDefault(Constants.Status.DISSOLVED, 0L));
        stats.setTotal(stats.getActive() + stats.getDissolved());
        return stats;
    }

    private ProjectsStats buildProjects(LocalDateTime weekAgo) {
        ProjectsStats stats = new ProjectsStats();
        stats.setTotal(projectMapper.countAll());
        stats.setWeekNew(projectMapper.countCreatedSince(weekAgo));
        return stats;
    }

    private ActivityStats buildActivity(LocalDateTime dayStart, LocalDateTime now) {
        ActivityStats stats = new ActivityStats();
        stats.setTodayLogins(auditLogMapper.countLoginsBetween(dayStart, now));
        return stats;
    }

    private List<DailyActiveUsers> buildActiveUsersDaily(LocalDate today) {
        LocalDate from = today.minusDays(ACTIVE_USER_DAYS - 1L);
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : auditLogMapper.countDistinctLoginsByDay(from.atStartOfDay())) {
            counts.put(String.valueOf(row.get("key")), ((Number) row.get("users")).longValue());
        }

        // 无记录的日期补 0，保证恒返回 14 项、日期升序
        List<DailyActiveUsers> result = new ArrayList<>(ACTIVE_USER_DAYS);
        for (int i = 0; i < ACTIVE_USER_DAYS; i++) {
            LocalDate date = from.plusDays(i);
            DailyActiveUsers item = new DailyActiveUsers();
            item.setDate(date.toString());
            item.setCount(counts.getOrDefault(date.toString(), 0L));
            result.add(item);
        }
        return result;
    }
}
