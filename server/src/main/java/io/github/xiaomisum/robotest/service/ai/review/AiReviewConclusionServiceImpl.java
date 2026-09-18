package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiReviewConclusionReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewConclusionRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewSummaryRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewNodeSnapshotMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.provider.OpenAiCompatProvider;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewConclusionEvaluator;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * AI 评审结论手动路径（06 §5.2）：SSE 同步生成，verdict/statistics 帧即时返回（不依赖 LLM），
 * LLM 只产出 reason/keyFindings；结构校验失败由网关带错自动重试 1 次（与自动路径同口径）。
 */
@Service
public class AiReviewConclusionServiceImpl implements AiReviewConclusionService {

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
    @Resource
    private AiOutputValidator aiOutputValidator;
    @Resource
    private AiTaskService aiTaskService;

    @Override
    public SseEmitter generateConclusion(UUID userId, UUID workspaceId, UUID projectId, UUID reviewId,
            AiReviewConclusionReqDTO reqDTO) {
        TestReview review = requireInitiator(reviewId, userId);
        // 仅「已完成」评审可生成结论（评审进行中无终局判定）
        if (!Constants.Status.COMPLETED.equals(review.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TARGET_STATE_INVALID);
        }
        // 同评审同时只允许一个进行中的结论生成（6005）
        if (!aiTaskMapper.lockInProgress(Constants.AiTaskType.REVIEW_CONCLUSION, reviewId, null).isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_DUPLICATE);
        }

        List<TestReviewNodeSnapshot> caseNodes = reviewNodeSnapshotMapper.listAssociatedByReviewId(reviewId,
                Constants.NodeType.CASE);
        AiReviewSummaryRespDTO.Statistics statistics = summaryService.computeStatistics(reviewId, caseNodes);
        String verdict = ReviewConclusionEvaluator.evaluate(caseNodes).verdict().getCode();
        String businessData = summaryService.buildBusinessData(review, statistics, caseNodes)
                + "\n【结论判定】" + verdict;

        // 落库 running 任务（同步 SSE，不进 executor 队列）；并发被 6005 拦截，此处可安全 insert
        AiAnalysisTask task = new AiAnalysisTask();
        task.setWorkspaceId(workspaceId);
        task.setProjectId(projectId);
        task.setType(Constants.AiTaskType.REVIEW_CONCLUSION);
        task.setTargetId(reviewId);
        task.setStatus(Constants.AiTaskStatus.RUNNING);
        task.setProgress(0);
        task.setCreatedBy(userId);
        aiTaskMapper.insert(task);
        UUID taskId = task.getId();

        UUID modelId = reqDTO == null ? null : reqDTO.getModelId();
        AiCallContext context = new AiCallContext(userId, workspaceId, projectId, modelId);
        SseEmitter emitter = aiGatewayService.stream(context, AiFunctionType.REVIEW_CONCLUSION,
                ReviewConclusionSupport.TASK_INSTRUCTION, businessData, ChatCallOptions.json(),
                conclusionPrelude(statistics, verdict), doneAssembler(reviewId, taskId, statistics, verdict));

        // 失败/断开时清理进行中记录（成功路径已在 doneAssembler 置 success，此处仅对 running 生效）
        emitter.onCompletion(() -> aiTaskMapper.markFailedIfRunning(taskId, "生成未完成"));
        emitter.onError(e -> aiTaskMapper.markFailedIfRunning(taskId, "生成异常中断"));
        return emitter;
    }

    @Override
    public AiTaskRespDTO getConclusion(UUID userId, UUID projectId, UUID reviewId) {
        requireInitiator(reviewId, userId);
        return aiTaskService.getLatestTaskByTypeAndTarget(Constants.AiTaskType.REVIEW_CONCLUSION, reviewId, projectId);
    }

    /** 连接建立后即发 statistics + verdict 帧（均 SQL/评审域精确计算，不依赖 LLM，06 §5.2） */
    private Consumer<SseEmitter> conclusionPrelude(AiReviewSummaryRespDTO.Statistics statistics, String verdict) {
        return emitter -> {
            try {
                emitter.send(SseEmitter.event().name("statistics").data(statistics, MediaType.APPLICATION_JSON));
                emitter.send(SseEmitter.event().name("verdict").data(verdict, MediaType.TEXT_PLAIN));
            } catch (Exception e) {
                // 客户端已断开：交由网关后续边界感知取消，无需在此处理
                throw new OpenAiCompatProvider.StreamCancelledException();
            }
        };
    }

    /**
     * done 帧组装：JSON 结构化校验（parseAndValidate 抛 OutputValidationException 由网关带错重试 1 次），
     * verdict/statistics 以评审域判定覆盖（LLM 不生成），覆盖式落库。
     */
    private Function<String, Object> doneAssembler(UUID reviewId, UUID taskId,
            AiReviewSummaryRespDTO.Statistics statistics, String verdict) {
        return fullContent -> {
            AiReviewConclusionRespDTO out = aiOutputValidator.parseAndValidate(fullContent,
                    AiReviewConclusionRespDTO.class, ReviewConclusionSupport::assertOutput);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("verdict", verdict);
            result.put("reason", out.getReason());
            result.put("keyFindings", out.getKeyFindings());
            result.put("statistics", statistics);
            // 先置 success 再清理旧 success，保证任意时刻至多一条成功的评审结论（覆盖语义，06 §5.2）
            aiTaskMapper.markSuccessIfRunning(taskId, JsonUtils.toJsonString(result));
            aiTaskMapper.deleteSuccessExcept(Constants.AiTaskType.REVIEW_CONCLUSION, reviewId, taskId);
            return result;
        };
    }

    private TestReview requireInitiator(UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        if (!review.getInitiatorId().equals(userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        return review;
    }
}