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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiReviewConclusionTaskHandler（06 §5.2 自动路径）：verdict 来自评审域判定（非 LLM）；
 * 完整生成链路失败自动重试 1 次（有界）；结果覆盖式落库。网关内部已自带 schema 校验失败重试，本处理器不重复。
 */
@ExtendWith(MockitoExtension.class)
class AiReviewConclusionTaskHandlerTest {

    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private AiAnalysisTaskMapper aiTaskMapper;
    @Mock
    private TestReviewMapper testReviewMapper;
    @Mock
    private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    @Mock
    private AiReviewSummaryServiceImpl summaryService;

    @InjectMocks
    private AiReviewConclusionTaskHandler handler;

    private final UUID reviewId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private AiAnalysisTask buildTask() {
        AiAnalysisTask task = new AiAnalysisTask();
        task.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        task.setType(Constants.AiTaskType.REVIEW_CONCLUSION);
        task.setTargetId(reviewId);
        task.setWorkspaceId(workspaceId);
        task.setProjectId(projectId);
        task.setCreatedBy(userId);
        task.setStatus(Constants.AiTaskStatus.RUNNING);
        return task;
    }

    private TestReview buildReview() {
        TestReview review = new TestReview();
        review.setId(reviewId);
        review.setProjectId(projectId);
        review.setInitiatorId(userId);
        review.setTitle("登录模块评审");
        return review;
    }

    private AiReviewConclusionRespDTO buildOutput() {
        AiReviewConclusionRespDTO out = new AiReviewConclusionRespDTO();
        out.setReason("登录失败场景未处理，结论依据为 1 个不通过用例。");
        out.setKeyFindings(List.of("缺少验证码错误提示"));
        return out;
    }

    @Test
    void type_returnsReviewConclusion() {
        assertEquals(Constants.AiTaskType.REVIEW_CONCLUSION, handler.type());
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_success_writesOverwriteResult() {
        AiAnalysisTask task = buildTask();
        TestReview review = buildReview();
        TestReviewNodeSnapshot failNode = new TestReviewNodeSnapshot();
        failNode.setId(UUID.randomUUID());
        failNode.setType(Constants.NodeType.CASE);
        failNode.setLastMark(Constants.ReviewMark.FAIL);
        List<TestReviewNodeSnapshot> caseNodes = List.of(failNode);
        AiReviewSummaryRespDTO.Statistics stats = new AiReviewSummaryRespDTO.Statistics();
        stats.setTotalCases(1);
        stats.setFailCount(1);
        AiReviewConclusionRespDTO out = buildOutput();

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);
        when(reviewNodeSnapshotMapper.listAssociatedByReviewId(reviewId, Constants.NodeType.CASE)).thenReturn(caseNodes);
        when(summaryService.computeStatistics(reviewId, caseNodes)).thenReturn(stats);
        when(summaryService.buildBusinessData(review, stats, caseNodes)).thenReturn("【评审信息】标题：登录模块评审");
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.REVIEW_CONCLUSION),
                anyString(), anyString(), any(), eq(AiReviewConclusionRespDTO.class), any())).thenReturn(out);

        Map<String, Object> result = handler.execute(task);

        assertEquals("FAIL", result.get("verdict"));
        assertEquals(out.getReason(), result.get("reason"));
        assertEquals(out.getKeyFindings(), result.get("keyFindings"));
        assertEquals(stats, result.get("statistics"));
        verify(aiTaskMapper).markSuccessIfRunning(eq(task.getId()), anyString());
        verify(aiTaskMapper).deleteSuccessExcept(Constants.AiTaskType.REVIEW_CONCLUSION, reviewId, task.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_firstAttemptFails_retriesOnceAndSucceeds() {
        AiAnalysisTask task = buildTask();
        TestReview review = buildReview();
        List<TestReviewNodeSnapshot> caseNodes = List.of();
        AiReviewSummaryRespDTO.Statistics stats = new AiReviewSummaryRespDTO.Statistics();
        AiReviewConclusionRespDTO out = buildOutput();

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);
        when(reviewNodeSnapshotMapper.listAssociatedByReviewId(reviewId, Constants.NodeType.CASE)).thenReturn(caseNodes);
        when(summaryService.computeStatistics(reviewId, caseNodes)).thenReturn(stats);
        when(summaryService.buildBusinessData(review, stats, caseNodes)).thenReturn("数据");
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.REVIEW_CONCLUSION),
                anyString(), anyString(), any(), eq(AiReviewConclusionRespDTO.class), any()))
                .thenThrow(new RuntimeException("provider 抖动"))
                .thenReturn(out);

        Map<String, Object> result = handler.execute(task);

        assertEquals("INCONCLUSIVE", result.get("verdict"));
        verify(aiGatewayService, times(2)).completeStructured(any(), eq(AiFunctionType.REVIEW_CONCLUSION),
                anyString(), anyString(), any(), eq(AiReviewConclusionRespDTO.class), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_allAttemptsFail_throwsBounded() {
        AiAnalysisTask task = buildTask();
        TestReview review = buildReview();
        List<TestReviewNodeSnapshot> caseNodes = List.of();
        AiReviewSummaryRespDTO.Statistics stats = new AiReviewSummaryRespDTO.Statistics();

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);
        when(reviewNodeSnapshotMapper.listAssociatedByReviewId(reviewId, Constants.NodeType.CASE)).thenReturn(caseNodes);
        when(summaryService.computeStatistics(reviewId, caseNodes)).thenReturn(stats);
        when(summaryService.buildBusinessData(review, stats, caseNodes)).thenReturn("数据");
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.REVIEW_CONCLUSION),
                anyString(), anyString(), any(), eq(AiReviewConclusionRespDTO.class), any()))
                .thenThrow(new RuntimeException("LLM 持续故障"));

        assertThrows(RuntimeException.class, () -> handler.execute(task));

        verify(aiGatewayService, times(AiReviewConclusionTaskHandler.MAX_ATTEMPTS))
                .completeStructured(any(), eq(AiFunctionType.REVIEW_CONCLUSION),
                        anyString(), anyString(), any(), eq(AiReviewConclusionRespDTO.class), any());
        verify(aiTaskMapper, never()).markSuccessIfRunning(any(), anyString());
    }

    @Test
    void execute_reviewMissing_throwsBusinessException() {
        AiAnalysisTask task = buildTask();
        when(testReviewMapper.selectById(reviewId)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> handler.execute(task));
        assertEquals(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND.msg(), ex.getMessage());
    }
}