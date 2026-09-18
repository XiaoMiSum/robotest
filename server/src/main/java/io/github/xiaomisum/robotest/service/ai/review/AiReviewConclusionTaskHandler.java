package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewConclusionRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewSummaryRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewNodeSnapshotMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskHandler;
import io.github.xiaomisum.robotest.service.domain.review.ReviewConclusionEvaluator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * review_conclusion 任务处理器（06 §5.2 自动路径）：
 * 统计（SQL）→ verdict（评审域判定）→ LLM 生成 reason/keyFindings → 校验 → 覆盖式落库。
 * 完整生成链路失败自动重试 1 次（有界，防 LLM 持续故障空转）。
 */
@Component
public class AiReviewConclusionTaskHandler implements AiTaskHandler {

    public static final String TYPE = Constants.AiTaskType.REVIEW_CONCLUSION;

    /** 完整生成尝试次数上限（含首次）：LLM 校验失败自动重试 1 次，仍失败抛异常由任务框架置 failed */
    static final int MAX_ATTEMPTS = 2;

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private AiAnalysisTaskMapper aiTaskMapper;
    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    @Resource
    private AiReviewSummaryServiceImpl summaryService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, Object> execute(AiAnalysisTask task) {
        UUID reviewId = task.getTargetId();
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        List<TestReviewNodeSnapshot> caseNodes = reviewNodeSnapshotMapper.listAssociatedByReviewId(reviewId,
                Constants.NodeType.CASE);
        String verdict = ReviewConclusionEvaluator.evaluate(caseNodes).verdict().getCode();

        AiReviewSummaryRespDTO.Statistics statistics = summaryService.computeStatistics(reviewId, caseNodes);
        String businessData = summaryService.buildBusinessData(review, statistics, caseNodes)
                + "\n【结论判定】" + verdict;

        RuntimeException lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return generate(task, review, statistics, caseNodes, businessData, verdict);
            } catch (RuntimeException e) {
                lastError = e;
            }
        }
        throw lastError;
    }

    private Map<String, Object> generate(AiAnalysisTask task, TestReview review,
            AiReviewSummaryRespDTO.Statistics statistics, List<TestReviewNodeSnapshot> caseNodes,
            String businessData, String verdict) {
        AiCallContext context = new AiCallContext(task.getCreatedBy(), task.getWorkspaceId(), task.getProjectId());
        AiReviewConclusionRespDTO out = aiGatewayService.completeStructured(context, AiFunctionType.REVIEW_CONCLUSION,
                ReviewConclusionSupport.TASK_INSTRUCTION, businessData, ChatCallOptions.json(),
                AiReviewConclusionRespDTO.class, ReviewConclusionSupport::assertOutput);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verdict", verdict);
        result.put("reason", out.getReason());
        result.put("keyFindings", out.getKeyFindings());
        result.put("statistics", statistics);

        // 先置 success 再清理旧 success，保证任意时刻至多一条成功的评审结论（覆盖语义，06 §5.2）
        aiTaskMapper.markSuccessIfRunning(task.getId(), JsonUtils.toJsonString(result));
        aiTaskMapper.deleteSuccessExcept(TYPE, review.getId(), task.getId());
        return result;
    }
}