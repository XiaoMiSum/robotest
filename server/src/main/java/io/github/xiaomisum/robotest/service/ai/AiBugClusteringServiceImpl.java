package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugClusteringStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.UUID;

/**
 * 缺陷聚类发起与查询实现（3.3）：发起仅校验范围非空，执行细节由任务处理器负责
 */
@Service
public class AiBugClusteringServiceImpl implements AiBugClusteringService {

    @Resource
    private BugMapper bugMapper;
    @Resource
    private AiTaskService aiTaskService;

    @Override
    public AiBugClusteringStartRespDTO startClustering(UUID userId, UUID workspaceId, UUID projectId) {
        if (bugMapper.countOpenBugsByProjectId(projectId) == 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TARGET_STATE_INVALID);
        }
        AiAnalysisTask task = aiTaskService.createTask(Constants.AiTaskType.BUG_CLUSTERING,
                workspaceId, projectId, projectId, userId);
        AiBugClusteringStartRespDTO dto = new AiBugClusteringStartRespDTO();
        dto.setTaskId(task.getId());
        return dto;
    }

    @Override
    public AiTaskRespDTO getLatestClustering(UUID projectId) {
        return aiTaskService.getLatestTaskByTypeAndTarget(
                Constants.AiTaskType.BUG_CLUSTERING, projectId, projectId);
    }
}
