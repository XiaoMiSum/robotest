package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.service.domain.requirement.ImpactAnalysisTaskPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 影响分析任务发布实现（需求详设 4.2 / AI 详设 4.4）：
 * 需求确认时触发 impact_analysis 任务提交；提交失败由需求侧 runSafely 捕获，只记日志不阻断确认。
 * P2 无 impact_analysis 处理器注册，提交按 1000018114 被拒绝并记日志，任务接通随 P3 补齐。
 */
@Component
public class ImpactAnalysisTaskPublisherImpl implements ImpactAnalysisTaskPublisher {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private AiTaskService aiTaskService;

    @Override
    public void publish(UUID requirementId, UUID projectId, UUID operatorId) {
        Project project = projectMapper.selectById(projectId);
        Map<String, Object> input = Map.of("requirementId", String.valueOf(requirementId));
        aiTaskService.submitInternal("impact_analysis", input, operatorId, projectId,
                project == null ? null : project.getWorkspaceId());
    }
}
