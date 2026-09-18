package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
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
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiReviewConclusionServiceImpl（06 §5.2 手动路径）：前置校验（发起人/completed/6005）→
 * statistics+verdict 帧即时返回 → done 帧结构化校验并覆盖式落库；读取走 AiTaskService。
 */
@ExtendWith(MockitoExtension.class)
class AiReviewConclusionServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

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
    @Mock
    private AiOutputValidator aiOutputValidator;
    @Mock
    private AiTaskService aiTaskService;

    @Captor
    private ArgumentCaptor<String> businessDataCaptor;
    @Captor
    private ArgumentCaptor<Consumer<SseEmitter>> preludeCaptor;
    @Captor
    private ArgumentCaptor<Function<String, Object>> doneAssemblerCaptor;

    @InjectMocks
    private AiReviewConclusionServiceImpl service;

    private TestReview review(String status) {
        TestReview review = new TestReview();
        review.setId(REVIEW_ID);
        review.setProjectId(PROJECT_ID);
        review.setTitle("登录模块评审");
        review.setInitiatorId(USER_ID);
        review.setStatus(status);
        return review;
    }

    private TestReviewNodeSnapshot caseNode(String mark) {
        TestReviewNodeSnapshot node = new TestReviewNodeSnapshot();
        node.setId(UUID.randomUUID());
        node.setType(Constants.NodeType.CASE);
        node.setLastMark(mark);
        return node;
    }

    private AiReviewConclusionReqDTO req() {
        return new AiReviewConclusionReqDTO();
    }

    // ==================== 前置校验 ====================

    @Test
    void reviewNotFound_throws() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(null);
        assertThrows(ServiceException.class,
                () -> service.generateConclusion(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req()));
    }

    @Test
    void notInitiator_throws() {
        TestReview review = review(Constants.Status.COMPLETED);
        review.setInitiatorId(UUID.randomUUID());
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review);
        assertThrows(ServiceException.class,
                () -> service.generateConclusion(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req()));
    }

    @Test
    void notCompleted_throws() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.IN_PROGRESS));
        assertThrows(ServiceException.class,
                () -> service.generateConclusion(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req()));
    }

    @Test
    void duplicateInProgress_throws() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.COMPLETED));
        when(aiTaskMapper.lockInProgress(Constants.AiTaskType.REVIEW_CONCLUSION, REVIEW_ID, null))
                .thenReturn(List.of(new AiAnalysisTask()));
        assertThrows(ServiceException.class,
                () -> service.generateConclusion(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req()));
    }

    // ==================== 生成链路 ====================

    private void stubHappyPath() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.COMPLETED));
        when(aiTaskMapper.lockInProgress(Constants.AiTaskType.REVIEW_CONCLUSION, REVIEW_ID, null))
                .thenReturn(List.of());
        when(aiTaskMapper.insert(any(AiAnalysisTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, AiAnalysisTask.class).setId(TASK_ID);
            return 1;
        });
    }

    @Test
    void generateConclusion_buildsBusinessDataWithVerdict() {
        stubHappyPath();
        TestReviewNodeSnapshot passNode = caseNode(Constants.ReviewMark.PASS);
        when(reviewNodeSnapshotMapper.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(List.of(passNode));
        AiReviewSummaryRespDTO.Statistics stats = new AiReviewSummaryRespDTO.Statistics();
        stats.setTotalCases(1);
        stats.setPassCount(1);
        when(summaryService.computeStatistics(REVIEW_ID, List.of(passNode))).thenReturn(stats);
        when(summaryService.buildBusinessData(any(), any(), any())).thenReturn("【评审信息】标题：登录模块评审");
        when(aiGatewayService.stream(any(), eq(AiFunctionType.REVIEW_CONCLUSION), any(),
                businessDataCaptor.capture(), any(), any(), any())).thenReturn(new SseEmitter());

        service.generateConclusion(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req());

        String businessData = businessDataCaptor.getValue();
        assertTrue(businessData.contains("登录模块评审"));
        assertTrue(businessData.contains("【结论判定】PASS"));
        verify(aiTaskMapper).insert(any(AiAnalysisTask.class));
    }

    @Test
    void generateConclusion_doneAssembler_parsesAndOverwrites() {
        stubHappyPath();
        when(reviewNodeSnapshotMapper.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(List.of(caseNode(Constants.ReviewMark.FAIL)));
        AiReviewSummaryRespDTO.Statistics stats = new AiReviewSummaryRespDTO.Statistics();
        stats.setTotalCases(1);
        stats.setFailCount(1);
        when(summaryService.computeStatistics(eq(REVIEW_ID), any())).thenReturn(stats);
        when(summaryService.buildBusinessData(any(), any(), any())).thenReturn("业务数据");
        when(aiGatewayService.stream(any(), any(), any(), any(), any(), any(),
                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());
        AiReviewConclusionRespDTO out = new AiReviewConclusionRespDTO();
        out.setReason("存在不通过用例。");
        out.setKeyFindings(List.of("缺验证码错误提示"));
        when(aiOutputValidator.parseAndValidate(anyString(), eq(AiReviewConclusionRespDTO.class), any()))
                .thenReturn(out);

        service.generateConclusion(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req());
        Object done = doneAssemblerCaptor.getValue().apply("{\"reason\":\"存在不通过用例。\",\"keyFindings\":[]}");

        assertTrue(done instanceof Map<?, ?>);
        Map<?, ?> map = (Map<?, ?>) done;
        assertEquals("FAIL", map.get("verdict"));
        assertEquals("存在不通过用例。", map.get("reason"));
        assertEquals(out.getKeyFindings(), map.get("keyFindings"));
        assertEquals(stats, map.get("statistics"));
        verify(aiTaskMapper).markSuccessIfRunning(eq(TASK_ID), anyString());
        verify(aiTaskMapper).deleteSuccessExcept(Constants.AiTaskType.REVIEW_CONCLUSION, REVIEW_ID, TASK_ID);
    }

    @Test
    void generateConclusion_preludeSendsStatisticsAndVerdictFrames() throws Exception {
        stubHappyPath();
        when(reviewNodeSnapshotMapper.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(List.of(caseNode(Constants.ReviewMark.PASS)));
        AiReviewSummaryRespDTO.Statistics stats = new AiReviewSummaryRespDTO.Statistics();
        stats.setTotalCases(1);
        stats.setPassCount(1);
        when(summaryService.computeStatistics(eq(REVIEW_ID), any())).thenReturn(stats);
        when(summaryService.buildBusinessData(any(), any(), any())).thenReturn("业务数据");
        when(aiGatewayService.stream(any(), any(), any(), any(), any(),
                preludeCaptor.capture(), any())).thenReturn(new SseEmitter());

        service.generateConclusion(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req());

        SseEmitter mockEmitter = org.mockito.Mockito.mock(SseEmitter.class);
        preludeCaptor.getValue().accept(mockEmitter);
        verify(mockEmitter, org.mockito.Mockito.times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    // ==================== 查询 ====================

    @Test
    void getConclusion_delegatesToTaskService() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.COMPLETED));
        AiTaskRespDTO dto = new AiTaskRespDTO();
        dto.setType(Constants.AiTaskType.REVIEW_CONCLUSION);
        when(aiTaskService.getLatestTaskByTypeAndTarget(Constants.AiTaskType.REVIEW_CONCLUSION,
                REVIEW_ID, PROJECT_ID)).thenReturn(dto);

        AiTaskRespDTO result = service.getConclusion(USER_ID, PROJECT_ID, REVIEW_ID);

        assertEquals(Constants.AiTaskType.REVIEW_CONCLUSION, result.getType());
        verify(aiTaskService).getLatestTaskByTypeAndTarget(Constants.AiTaskType.REVIEW_CONCLUSION,
                REVIEW_ID, PROJECT_ID);
    }
}