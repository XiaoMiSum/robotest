package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewCheckStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.UUID;

@Service
public class AiReviewCheckServiceImpl implements AiReviewCheckService {

    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private AiTaskService aiTaskService;

    @Override
    public AiReviewCheckStartRespDTO startCheck(UUID userId, UUID workspaceId, UUID projectId, UUID reviewId) {
        TestReview review = requireInitiator(projectId, reviewId, userId);
        // 待评审 / 进行中均可发起；评审进入终态（completed 已通过 / rejected 已驳回）后仅保留历史结果查看，不可再发起
        boolean runnable = Constants.Status.NEW.equals(review.getStatus())
                || Constants.Status.IN_PROGRESS.equals(review.getStatus());
        if (!runnable) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TARGET_STATE_INVALID);
        }
        AiAnalysisTask task = aiTaskService.createTask(Constants.AiTaskType.REVIEW_CHECK,
                workspaceId, projectId, reviewId, userId);
        AiReviewCheckStartRespDTO dto = new AiReviewCheckStartRespDTO();
        dto.setTaskId(task.getId());
        return dto;
    }

    @Override
    public AiTaskRespDTO getCheckResult(UUID userId, UUID projectId, UUID reviewId) {
        requireInitiator(projectId, reviewId, userId);
        return aiTaskService.getLatestTaskByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, reviewId, projectId);
    }

    private TestReview requireInitiator(UUID projectId, UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        // 归属活动项目校验（SEC-014）：跨项目按不存在处理，不泄露评审存在性
        if (review == null || !review.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        if (!review.getInitiatorId().equals(userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        return review;
    }
}
