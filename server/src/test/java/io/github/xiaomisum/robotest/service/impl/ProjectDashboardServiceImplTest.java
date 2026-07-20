package io.github.xiaomisum.robotest.service.impl;

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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        doc.setId("doc-1");
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

        ProjectDashboardRespDTO result = dashboardService.getDashboard(projectId);

        assertNotNull(result);
        assertEquals(5L, result.getCaseCount());
        assertEquals(2L, result.getActiveReviewCount());
        assertEquals(3L, result.getActivePlanCount());
        assertEquals(4L, result.getOpenBugCount());
        assertNotNull(result.getRecentReviews());
        assertNotNull(result.getRecentPlans());
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

        ProjectDashboardRespDTO result = dashboardService.getDashboard(projectId);

        assertNotNull(result);
        assertEquals(0L, result.getCaseCount());
        assertEquals(0L, result.getActiveReviewCount());
        assertEquals(0L, result.getActivePlanCount());
        assertEquals(0L, result.getOpenBugCount());
    }
}
