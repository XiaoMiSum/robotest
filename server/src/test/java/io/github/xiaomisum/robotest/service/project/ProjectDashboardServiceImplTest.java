package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.dto.response.ProjectDashboardRespDTO;
import io.github.xiaomisum.robotest.model.entity.*;
import io.github.xiaomisum.robotest.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDashboardServiceImplTest {

    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestReviewMapper testReviewMapper;
    @Mock
    private TestPlanMapper testPlanMapper;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private ProjectDashboardServiceImpl dashboardService;

    private String projectId;

    @BeforeEach
    void setUp() {
        projectId = "proj-1";
    }

    @Test
    void getDashboard_withData() {
        TestCaseModule doc = new TestCaseModule();
        doc.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(testCaseModuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(doc));
        when(testCaseNodeMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(5L);
        when(testReviewMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(2L);
        when(testPlanMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(3L);
        when(bugMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(4L);
        when(testReviewMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(testPlanMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(bugMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        ProjectDashboardRespDTO result = dashboardService.getDashboard(projectId);

        assertNotNull(result);
        assertEquals(5L, result.getCaseCount());
        assertEquals(2L, result.getActiveReviewCount());
        assertEquals(3L, result.getActivePlanCount());
        assertEquals(4L, result.getOpenBugCount());
        assertNotNull(result.getRecentReviews());
        assertNotNull(result.getRecentPlans());
        assertNotNull(result.getRecentBugs());
    }

    @Test
    void getDashboard_noDocuments() {
        when(testCaseModuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(testReviewMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(testPlanMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(bugMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(testReviewMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(testPlanMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(bugMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        ProjectDashboardRespDTO result = dashboardService.getDashboard(projectId);

        assertNotNull(result);
        assertEquals(0L, result.getCaseCount());
        assertEquals(0L, result.getActiveReviewCount());
        assertEquals(0L, result.getActivePlanCount());
        assertEquals(0L, result.getOpenBugCount());
    }

    @Test
    void getDashboard_withRecentBugs() {
        when(testCaseModuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(testReviewMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(testPlanMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(bugMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(testReviewMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(testPlanMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Bug bug = new Bug();
        bug.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        bug.setTitle("Test Bug");
        bug.setSeverity("high");
        bug.setPriority("high");
        bug.setStatus("new");
        bug.setAssigneeId("00000000-0000-0000-0000-000000000011");

        when(bugMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(bug));

        SysUser assignee = new SysUser();
        assignee.setId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        assignee.setUsername("assignee");
        when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(assignee));

        ProjectDashboardRespDTO result = dashboardService.getDashboard(projectId);

        assertNotNull(result.getRecentBugs());
        assertEquals(1, result.getRecentBugs().size());
        assertEquals("Test Bug", result.getRecentBugs().get(0).getTitle());
        assertEquals("assignee", result.getRecentBugs().get(0).getAssignee());
    }

    @Test
    void getDashboard_bugsWithoutAssignee() {
        when(testCaseModuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(testReviewMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(testPlanMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(bugMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(testReviewMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(testPlanMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Bug bug = new Bug();
        bug.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        bug.setTitle("Unassigned Bug");
        bug.setSeverity("low");
        bug.setPriority("low");
        bug.setStatus("new");
        bug.setAssigneeId(null);

        when(bugMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(bug));

        ProjectDashboardRespDTO result = dashboardService.getDashboard(projectId);

        assertNotNull(result.getRecentBugs());
        assertEquals(1, result.getRecentBugs().size());
        assertNull(result.getRecentBugs().get(0).getAssignee());
    }
}
