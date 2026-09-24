package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectDashboardRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.service.admin.PermissionFacade;
import io.github.xiaomisum.robotest.service.admin.PermissionScope;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDashboardServiceImplTest {

    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
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
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private ProjectActivityService projectActivityService;
    @Mock
    private PermissionFacade permissionFacade;

    @InjectMocks
    private ProjectDashboardServiceImpl dashboardService;

    private UUID projectId;
    private UUID workspaceId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        userId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Project project = new Project();
        project.setId(projectId);
        project.setName("Test Project");
        project.setStatus("active");
        when(projectMapper.selectById(projectId)).thenReturn(project);
        when(projectActivityService.listRecent(projectId, 8)).thenReturn(Collections.emptyList());
        when(permissionFacade.permissionsOf(userId, PermissionScope.WORKSPACE, workspaceId))
                .thenReturn(Set.of("project:view"));
    }

    @Test
    void getDashboard_withData() {
        TestCaseDocument doc = new TestCaseDocument();
        doc.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(testCaseDocumentMapper.listByProjectId(projectId))
                .thenReturn(List.of(doc));
        when(testCaseNodeMapper.countCaseNodesByDocumentIds(anyList()))
                .thenReturn(5L);
        when(testReviewMapper.countActiveReviews(projectId))
                .thenReturn(2L);
        when(testPlanMapper.countActivePlans(projectId))
                .thenReturn(3L);
        when(bugMapper.countOpenBugs(projectId))
                .thenReturn(4L);
        when(testReviewMapper.findRecentReviews(projectId, 5))
                .thenReturn(Collections.emptyList());
        when(testPlanMapper.findRecentPlans(projectId, 5))
                .thenReturn(Collections.emptyList());
        when(bugMapper.findRecentBugs(projectId, 5))
                .thenReturn(Collections.emptyList());

        ProjectDashboardRespDTO result = dashboardService.getDashboard(projectId, workspaceId, userId);

        assertNotNull(result);
        assertEquals("Test Project", result.getProjectName());
        assertEquals("active", result.getProjectStatus());
        assertNotNull(result.getRecentActivities());
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
        when(testCaseDocumentMapper.listByProjectId(projectId))
                .thenReturn(Collections.emptyList());
        when(testReviewMapper.countActiveReviews(projectId))
                .thenReturn(0L);
        when(testPlanMapper.countActivePlans(projectId))
                .thenReturn(0L);
        when(bugMapper.countOpenBugs(projectId))
                .thenReturn(0L);
        when(testReviewMapper.findRecentReviews(projectId, 5))
                .thenReturn(Collections.emptyList());
        when(testPlanMapper.findRecentPlans(projectId, 5))
                .thenReturn(Collections.emptyList());
        when(bugMapper.findRecentBugs(projectId, 5))
                .thenReturn(Collections.emptyList());

        ProjectDashboardRespDTO result = dashboardService.getDashboard(projectId, workspaceId, userId);

        assertNotNull(result);
        assertEquals(0L, result.getCaseCount());
        assertEquals(0L, result.getActiveReviewCount());
        assertEquals(0L, result.getActivePlanCount());
        assertEquals(0L, result.getOpenBugCount());
    }

    @Test
    void getDashboard_withRecentBugs() {
        when(testCaseDocumentMapper.listByProjectId(projectId))
                .thenReturn(Collections.emptyList());
        when(testReviewMapper.countActiveReviews(projectId))
                .thenReturn(0L);
        when(testPlanMapper.countActivePlans(projectId))
                .thenReturn(0L);
        when(bugMapper.countOpenBugs(projectId))
                .thenReturn(0L);
        when(testReviewMapper.findRecentReviews(projectId, 5))
                .thenReturn(Collections.emptyList());
        when(testPlanMapper.findRecentPlans(projectId, 5))
                .thenReturn(Collections.emptyList());

        Bug bug = new Bug();
        bug.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        bug.setTitle("Test Bug");
        bug.setSeverity("high");
        bug.setPriority("high");
        bug.setStatus("active");
        bug.setAssigneeId(UUID.fromString("00000000-0000-0000-0000-000000000011"));

        when(bugMapper.findRecentBugs(projectId, 5))
                .thenReturn(List.of(bug));

        SysUser assignee = new SysUser();
        assignee.setId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        assignee.setUsername("assignee");
        when(userMapper.listByIds(anyList()))
                .thenReturn(List.of(assignee));

        ProjectDashboardRespDTO result = dashboardService.getDashboard(projectId, workspaceId, userId);

        assertNotNull(result.getRecentBugs());
        assertEquals(1, result.getRecentBugs().size());
        assertEquals("Test Bug", result.getRecentBugs().get(0).getTitle());
        assertEquals("assignee", result.getRecentBugs().get(0).getAssignee());
    }

    @Test
    void getDashboard_bugsWithoutAssignee() {
        when(testCaseDocumentMapper.listByProjectId(projectId))
                .thenReturn(Collections.emptyList());
        when(testReviewMapper.countActiveReviews(projectId))
                .thenReturn(0L);
        when(testPlanMapper.countActivePlans(projectId))
                .thenReturn(0L);
        when(bugMapper.countOpenBugs(projectId))
                .thenReturn(0L);
        when(testReviewMapper.findRecentReviews(projectId, 5))
                .thenReturn(Collections.emptyList());
        when(testPlanMapper.findRecentPlans(projectId, 5))
                .thenReturn(Collections.emptyList());

        Bug bug = new Bug();
        bug.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        bug.setTitle("Unassigned Bug");
        bug.setSeverity("low");
        bug.setPriority("low");
        bug.setStatus("active");
        bug.setAssigneeId(null);

        when(bugMapper.findRecentBugs(projectId, 5))
                .thenReturn(List.of(bug));

        ProjectDashboardRespDTO result = dashboardService.getDashboard(projectId, workspaceId, userId);

        assertNotNull(result.getRecentBugs());
        assertEquals(1, result.getRecentBugs().size());
        assertNull(result.getRecentBugs().get(0).getAssignee());
    }
}
