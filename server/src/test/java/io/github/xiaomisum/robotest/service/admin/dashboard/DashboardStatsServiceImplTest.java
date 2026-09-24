package io.github.xiaomisum.robotest.service.admin.dashboard;

import io.github.xiaomisum.robotest.model.dto.response.admin.DashboardStatsRespDTO;
import io.github.xiaomisum.robotest.repository.admin.AuditLogMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardStatsServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private DashboardStatsServiceImpl service;

    /** 其余指标无断言诉求时统一置零，避免 Mockito 严格模式因未 stub 报错 */
    private void stubEmptyOthers() {
        when(userMapper.countGroupByStatus()).thenReturn(Map.of());
        when(userMapper.countCreatedSince(any())).thenReturn(0L);
        when(workspaceMapper.countGroupByStatus()).thenReturn(Map.of());
        when(projectMapper.countAll()).thenReturn(0L);
        when(projectMapper.countCreatedSince(any())).thenReturn(0L);
        when(auditLogMapper.countLoginsBetween(any(), any())).thenReturn(0L);
        when(auditLogMapper.countDistinctLoginsByDay(any())).thenReturn(List.of());
    }

    @Test
    void getStats_aggregatesStatusGroupsAndWeekNew() {
        Map<String, Long> userStatus = new HashMap<>();
        userStatus.put("active", 100L);
        userStatus.put("disabled", 20L);
        userStatus.put("locked", 8L);
        when(userMapper.countGroupByStatus()).thenReturn(userStatus);
        when(userMapper.countCreatedSince(any())).thenReturn(6L);

        Map<String, Long> wsStatus = new HashMap<>();
        wsStatus.put("active", 14L);
        wsStatus.put("dissolved", 2L);
        when(workspaceMapper.countGroupByStatus()).thenReturn(wsStatus);
        when(projectMapper.countAll()).thenReturn(54L);
        when(projectMapper.countCreatedSince(any())).thenReturn(3L);
        when(auditLogMapper.countLoginsBetween(any(), any())).thenReturn(63L);
        when(auditLogMapper.countDistinctLoginsByDay(any())).thenReturn(List.of());

        DashboardStatsRespDTO stats = service.getStats();

        // generatedAt 须为 UTC 墙钟（docs/00-spec/03-frontend.md §8），容差 ≤60s 防止回退为本地钟面
        Duration skew = Duration.between(stats.getGeneratedAt(), LocalDateTime.now(ZoneOffset.UTC));
        assertTrue(skew.abs().toSeconds() <= 60, "generatedAt 应为 UTC 墙钟，实际=" + stats.getGeneratedAt());
        assertEquals(128L, stats.getUsers().getTotal());
        assertEquals(100L, stats.getUsers().getEnabled());
        assertEquals(20L, stats.getUsers().getDisabled());
        assertEquals(8L, stats.getUsers().getLocked());
        assertEquals(6L, stats.getUsers().getWeekNew());
        assertEquals(16L, stats.getWorkspaces().getTotal());
        assertEquals(14L, stats.getWorkspaces().getActive());
        assertEquals(2L, stats.getWorkspaces().getDissolved());
        assertEquals(54L, stats.getProjects().getTotal());
        assertEquals(3L, stats.getProjects().getWeekNew());
        assertEquals(63L, stats.getActivity().getTodayLogins());
    }

    @Test
    void getStats_zeroWhenAllEmpty() {
        stubEmptyOthers();

        DashboardStatsRespDTO stats = service.getStats();

        assertEquals(0L, stats.getUsers().getTotal());
        assertEquals(0L, stats.getWorkspaces().getTotal());
        assertEquals(0L, stats.getProjects().getTotal());
        assertEquals(0L, stats.getActivity().getTodayLogins());
    }

    @Test
    void activeUsersDaily_alwaysReturns14AscendingDaysWithZeroFill() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(13);
        // 仅首日与今日有数据，其余 12 天补 0
        Map<String, Object> firstRow = new HashMap<>();
        firstRow.put("key", firstDay.toString());
        firstRow.put("users", 41L);
        Map<String, Object> lastRow = new HashMap<>();
        lastRow.put("key", today.toString());
        lastRow.put("users", 63L);
        stubEmptyOthers();
        when(auditLogMapper.countDistinctLoginsByDay(any())).thenReturn(List.of(firstRow, lastRow));

        DashboardStatsRespDTO stats = service.getStats();

        List<DashboardStatsRespDTO.DailyActiveUsers> daily = stats.getActiveUsersDaily();
        assertEquals(14, daily.size());
        assertEquals(firstDay.toString(), daily.get(0).getDate());
        assertEquals(today.toString(), daily.get(13).getDate());
        assertEquals(41L, daily.get(0).getCount());
        assertEquals(63L, daily.get(13).getCount());
        for (int i = 1; i < 13; i++) {
            assertEquals(0L, daily.get(i).getCount(), "中间日应补 0，index=" + i);
            assertEquals(firstDay.plusDays(i).toString(), daily.get(i).getDate());
        }
    }
}
