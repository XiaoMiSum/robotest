package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.convert.ProjectDashboardConvertMapper;
import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectDashboardRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.service.project.ProjectDashboardService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

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
    public ProjectDashboardRespDTO getDashboard(UUID projectId) {
        ProjectDashboardRespDTO dto = new ProjectDashboardRespDTO();

        // Count case nodes belonging to this project's documents
        List<String> projectDocIds = testCaseModuleMapper.findDocumentModulesByProjectId(projectId)
                .stream().map(m -> m.getId().toString()).collect(Collectors.toList());

        long caseCount = 0;
        if (!projectDocIds.isEmpty()) {
            caseCount = testCaseNodeMapper.countCaseNodesByDocumentIds(projectDocIds);
        }
        dto.setCaseCount(caseCount);

        dto.setActiveReviewCount(testReviewMapper.countActiveReviews(projectId));

        dto.setActivePlanCount(testPlanMapper.countActivePlans(projectId));

        dto.setOpenBugCount(bugMapper.countOpenBugs(projectId));

        // 最近 5 条缺陷
        List<Bug> recentBugs = bugMapper.findRecentBugs(projectId, 5);

        // 批量解析 assignee 姓名
        List<UUID> assigneeIds = recentBugs.stream()
                .map(Bug::getAssigneeId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<UUID, String> nameMap = assigneeIds.isEmpty() ? Map.of() :
                userMapper.listByIds(assigneeIds)
                        .stream().collect(Collectors.toMap(SysUser::getId, SysUser::getUsername));

        dto.setRecentBugs(recentBugs.stream().map(b -> {
            ProjectDashboardRespDTO.RecentBugItem item = ProjectDashboardConvertMapper.INSTANCE.toRecentBugItem(b);
            if (b.getAssigneeId() != null) {
                item.setAssignee(nameMap.get(b.getAssigneeId()));
            }
            return item;
        }).collect(Collectors.toList()));

        List<TestReview> recentReviews = testReviewMapper.findRecentReviews(projectId, 5);
        dto.setRecentReviews(recentReviews.stream()
                .map(ProjectDashboardConvertMapper.INSTANCE::toRecentItem)
                .collect(Collectors.toList()));

        List<TestPlan> recentPlans = testPlanMapper.findRecentPlans(projectId, 5);
        dto.setRecentPlans(recentPlans.stream()
                .map(ProjectDashboardConvertMapper.INSTANCE::toRecentItemFromPlan)
                .collect(Collectors.toList()));

        return dto;
    }
}
