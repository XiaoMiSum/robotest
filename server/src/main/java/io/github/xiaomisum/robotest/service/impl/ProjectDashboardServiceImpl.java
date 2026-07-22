package io.github.xiaomisum.robotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.ProjectDashboardRespDTO;
import io.github.xiaomisum.robotest.model.entity.*;
import io.github.xiaomisum.robotest.repository.*;
import io.github.xiaomisum.robotest.service.ProjectDashboardService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectDashboardServiceImpl implements ProjectDashboardService {

    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private BugMapper bugMapper;

    @Override
    public ProjectDashboardRespDTO getDashboard(String projectId) {
        ProjectDashboardRespDTO dto = new ProjectDashboardRespDTO();

        // Count case nodes belonging to this project's documents
        List<String> projectDocIds = testCaseModuleMapper.selectList(
                new LambdaQueryWrapper<TestCaseModule>()
                        .eq(TestCaseModule::getProjectId, projectId)
                        .eq(TestCaseModule::getType, Constants.ModuleType.DOCUMENT))
                .stream().map(m -> m.getId().toString()).collect(Collectors.toList());

        long caseCount = 0;
        if (!projectDocIds.isEmpty()) {
            caseCount = testCaseNodeMapper.selectCount(
                    new LambdaQueryWrapper<TestCaseNode>()
                            .in(TestCaseNode::getDocumentId, projectDocIds)
                            .eq(TestCaseNode::getType, Constants.NodeType.CASE));
        }
        dto.setCaseCount(caseCount);

        dto.setActiveReviewCount(testReviewMapper.selectCount(
                new LambdaQueryWrapper<TestReview>()
                        .eq(TestReview::getProjectId, projectId)
                        .eq(TestReview::getStatus, Constants.Status.IN_PROGRESS)));

        dto.setActivePlanCount(testPlanMapper.selectCount(
                new LambdaQueryWrapper<TestPlan>()
                        .eq(TestPlan::getProjectId, projectId)
                        .in(TestPlan::getStatus, Constants.Status.NEW, Constants.Status.IN_PROGRESS)));

        dto.setOpenBugCount(bugMapper.selectCount(
                new LambdaQueryWrapper<Bug>()
                        .eq(Bug::getProjectId, projectId)
                        .in(Bug::getStatus, Constants.Status.NEW, Constants.Status.ASSIGNED, Constants.Status.FIXING)));

        List<TestReview> recentReviews = testReviewMapper.selectList(
                new LambdaQueryWrapper<TestReview>()
                        .eq(TestReview::getProjectId, projectId)
                        .orderByDesc(TestReview::getCreatedAt)
                        .last("LIMIT 5"));
        dto.setRecentReviews(recentReviews.stream().map(r -> {
            ProjectDashboardRespDTO.RecentItem item = new ProjectDashboardRespDTO.RecentItem();
            item.setId(r.getId());
            item.setTitle(r.getTitle());
            item.setStatus(r.getStatus());
            item.setCreatedAt(r.getCreatedAt());
            return item;
        }).collect(Collectors.toList()));

        List<TestPlan> recentPlans = testPlanMapper.selectList(
                new LambdaQueryWrapper<TestPlan>()
                        .eq(TestPlan::getProjectId, projectId)
                        .orderByDesc(TestPlan::getCreatedAt)
                        .last("LIMIT 5"));
        dto.setRecentPlans(recentPlans.stream().map(p -> {
            ProjectDashboardRespDTO.RecentItem item = new ProjectDashboardRespDTO.RecentItem();
            item.setId(p.getId());
            item.setTitle(p.getName());
            item.setStatus(p.getStatus());
            item.setCreatedAt(p.getCreatedAt());
            return item;
        }).collect(Collectors.toList()));

        return dto;
    }
}
