package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.ProjectDashboardRespDTO;
import io.github.xiaomisum.robotest.model.entity.*;
import io.github.xiaomisum.robotest.repository.*;
import io.github.xiaomisum.robotest.service.project.ProjectDashboardService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
    @Resource
    private SysUserMapper userMapper;

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
                        .in(Bug::getStatus,
                                Constants.BugStatus.NEW,
                                Constants.BugStatus.ASSIGNED,
                                Constants.BugStatus.FIXING)));

        // 最近 5 条缺陷
        List<Bug> recentBugs = bugMapper.selectList(
                new LambdaQueryWrapper<Bug>()
                        .eq(Bug::getProjectId, projectId)
                        .orderByDesc(Bug::getCreatedAt)
                        .last("LIMIT 5"));

        // 批量解析 assignee 姓名
        Set<String> assigneeIds = recentBugs.stream()
                .map(Bug::getAssigneeId)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .collect(Collectors.toSet());
        if (!assigneeIds.isEmpty()) {
            Map<String, String> nameMap = userMapper.selectBatchIds(assigneeIds)
                    .stream()
                    .collect(Collectors.toMap(
                            u -> u.getId().toString(),
                            SysUser::getUsername,
                            (a, b) -> a));

            dto.setRecentBugs(recentBugs.stream().map(b -> {
                ProjectDashboardRespDTO.RecentBugItem item = new ProjectDashboardRespDTO.RecentBugItem();
                item.setId(b.getId());
                item.setTitle(b.getTitle());
                item.setSeverity(b.getSeverity());
                item.setPriority(b.getPriority());
                item.setStatus(b.getStatus());
                if (b.getAssigneeId() != null) {
                    item.setAssignee(nameMap.get(b.getAssigneeId().toString()));
                }
                item.setCreatedAt(b.getCreatedAt());
                return item;
            }).collect(Collectors.toList()));
        } else {
            dto.setRecentBugs(recentBugs.stream().map(b -> {
                ProjectDashboardRespDTO.RecentBugItem item = new ProjectDashboardRespDTO.RecentBugItem();
                item.setId(b.getId());
                item.setTitle(b.getTitle());
                item.setSeverity(b.getSeverity());
                item.setPriority(b.getPriority());
                item.setStatus(b.getStatus());
                item.setCreatedAt(b.getCreatedAt());
                return item;
            }).collect(Collectors.toList()));
        }

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
