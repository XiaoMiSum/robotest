package io.github.xiaomisum.robotest.service.ai.review;


import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiReviewSummaryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewSummaryRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewModuleSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewRecord;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewNodeSnapshotMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewRecordMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReviewSummaryServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID DOC_A = UUID.randomUUID();
    private static final UUID DOC_B = UUID.randomUUID();

    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private AiAnalysisTaskMapper aiTaskMapper;
    @Mock
    private TestReviewMapper testReviewMapper;
    @Mock
    private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    @Mock
    private TestReviewModuleSnapshotMapper reviewModuleSnapshotMapper;
    @Mock
    private TestReviewRecordMapper reviewRecordMapper;
    @Mock
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Captor
    private ArgumentCaptor<String> businessDataCaptor;
    @Captor
    private ArgumentCaptor<Consumer<SseEmitter>> preludeCaptor;
    @Captor
    private ArgumentCaptor<Function<String, Object>> doneAssemblerCaptor;

    @InjectMocks
    private AiReviewSummaryServiceImpl service;

    private TestReview review(String status) {
        TestReview review = new TestReview();
        review.setId(REVIEW_ID);
        review.setProjectId(PROJECT_ID);
        review.setTitle("登录模块评审");
        review.setInitiatorId(USER_ID);
        review.setStatus(status);
        return review;
    }

    private TestReviewNodeSnapshot caseNode(String mark, UUID docSnapshotId, String title) {
        TestReviewNodeSnapshot node = new TestReviewNodeSnapshot();
        node.setId(UUID.randomUUID());
        node.setReviewId(REVIEW_ID);
        node.setType(Constants.NodeType.CASE);
        node.setLastMark(mark);
        node.setDocumentSnapshotId(docSnapshotId);
        node.setTitle(title);
        node.setIsAssociated(true);
        return node;
    }

    private TestReviewModuleSnapshot docSnapshot(UUID id, String name) {
        TestReviewModuleSnapshot m = new TestReviewModuleSnapshot();
        m.setId(id);
        m.setReviewId(REVIEW_ID);
        m.setType(Constants.ModuleType.DOCUMENT);
        m.setName(name);
        return m;
    }

    private AiReviewSummaryReqDTO req() {
        return new AiReviewSummaryReqDTO();
    }

    // ==================== 前置校验 ====================

    @Test
    void reviewNotFound_throws() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(null);
        assertThrows(ServiceException.class,
                () -> service.generateSummary(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req()));
    }

    @Test
    void notInitiator_throws() {
        TestReview review = review(Constants.Status.COMPLETED);
        review.setInitiatorId(UUID.randomUUID());
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review);
        assertThrows(ServiceException.class,
                () -> service.generateSummary(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req()));
    }

    @Test
    void notCompleted_throws() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.IN_PROGRESS));
        assertThrows(ServiceException.class,
                () -> service.generateSummary(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req()));
    }

    @Test
    void duplicateInProgress_throws() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.COMPLETED));
        when(aiTaskMapper.lockInProgress(Constants.AiTaskType.REVIEW_SUMMARY, REVIEW_ID, null))
                .thenReturn(List.of(new AiAnalysisTask()));
        assertThrows(ServiceException.class,
                () -> service.generateSummary(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req()));
    }

    // ==================== 统计计算 ====================

    @Test
    void computeStatistics_buckets_passRate_and_failByDocument() {
        when(reviewModuleSnapshotMapper.listByReviewId(REVIEW_ID))
                .thenReturn(List.of(docSnapshot(DOC_A, "登录用例集"), docSnapshot(DOC_B, "支付用例集")));
        List<TestReviewNodeSnapshot> nodes = List.of(
                caseNode(Constants.ReviewMark.PASS, DOC_A, "登录成功"),
                caseNode(Constants.ReviewMark.PASS, DOC_A, "记住登录"),
                caseNode(Constants.ReviewMark.FAIL, DOC_A, "密码错误提示"),
                caseNode(Constants.ReviewMark.FAIL, DOC_B, "支付超时"),
                caseNode(Constants.ReviewMark.FAIL, DOC_B, "余额不足"),
                caseNode(null, DOC_B, "待评审用例"));

        AiReviewSummaryRespDTO.Statistics stats = service.computeStatistics(REVIEW_ID, nodes);

        assertEquals(6, stats.getTotalCases());
        assertEquals(2, stats.getPassCount());
        assertEquals(3, stats.getFailCount());
        assertEquals(1, stats.getPendingCount());
        assertEquals(33.33, stats.getPassRate());
        // 按 fail 数降序：支付用例集(2) 在前，登录用例集(1) 在后
        assertEquals(2, stats.getFailByDocument().size());
        assertEquals("支付用例集", stats.getFailByDocument().get(0).getDocumentName());
        assertEquals(2, stats.getFailByDocument().get(0).getFailCount());
    }

    @Test
    void computeStatistics_emptyNodes_zeroPassRate() {
        when(reviewModuleSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of());
        AiReviewSummaryRespDTO.Statistics stats = service.computeStatistics(REVIEW_ID, List.of());
        assertEquals(0, stats.getTotalCases());
        assertEquals(0.0, stats.getPassRate());
        assertTrue(stats.getFailByDocument().isEmpty());
    }

    // ==================== 生成链路 ====================

    private void stubHappyPath() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.COMPLETED));
        when(aiTaskMapper.lockInProgress(Constants.AiTaskType.REVIEW_SUMMARY, REVIEW_ID, null))
                .thenReturn(List.of());
        when(reviewModuleSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(docSnapshot(DOC_A, "登录用例集")));
        // insert 时回填任务 id（模拟 MyBatis-Plus ASSIGN_UUID）
        when(aiTaskMapper.insert(any(AiAnalysisTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, AiAnalysisTask.class).setId(TASK_ID);
            return 1;
        });
    }

    @Test
    void generateSummary_buildsFailSamplesContext() {
        stubHappyPath();
        TestReviewNodeSnapshot failNode = caseNode(Constants.ReviewMark.FAIL, DOC_A, "密码错误提示");
        when(reviewNodeSnapshotMapper.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(List.of(failNode));
        TestReviewRecord comment = new TestReviewRecord();
        comment.setReviewId(REVIEW_ID);
        comment.setSnapshotNodeId(failNode.getId());
        comment.setOperationType(Constants.ReviewOperation.COMMENT);
        comment.setComment("缺少错误码校验");
        comment.setCreatedAt(LocalDateTime.now());
        when(reviewRecordMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(comment));
        when(aiGatewayService.stream(any(), eq(AiFunctionType.REVIEW_SUMMARY), any(),
                businessDataCaptor.capture(), any(), any(), any())).thenReturn(new SseEmitter());

        service.generateSummary(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req());

        String businessData = businessDataCaptor.getValue();
        assertTrue(businessData.contains("登录模块评审"));
        assertTrue(businessData.contains("密码错误提示"));
        assertTrue(businessData.contains("缺少错误码校验"));
        verify(aiTaskMapper).insert(any(AiAnalysisTask.class));
    }

    @Test
    void generateSummary_doneAssembler_persistsAndOverwrites() {
        stubHappyPath();
        when(reviewNodeSnapshotMapper.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(List.of(caseNode(Constants.ReviewMark.PASS, DOC_A, "登录成功")));
        lenient().when(reviewRecordMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of());
        when(aiGatewayService.stream(any(), any(), any(), any(), any(), any(),
                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());

        service.generateSummary(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req());
        Object done = doneAssemblerCaptor.getValue().apply("## 评审总结\n通过率良好");

        assertTrue(done instanceof Map<?, ?>);
        Map<?, ?> map = (Map<?, ?>) done;
        assertEquals("## 评审总结\n通过率良好", map.get("summaryMarkdown"));
        verify(aiTaskMapper).markSuccessIfRunning(eq(TASK_ID), any());
        // 覆盖语义：逻辑删除该评审此前的 success 记录
        verify(aiTaskMapper).deleteSuccessExcept(Constants.AiTaskType.REVIEW_SUMMARY, REVIEW_ID, TASK_ID);
    }

    @Test
    void generateSummary_doneAssembler_truncatesOverLongMarkdown() {
        stubHappyPath();
        when(reviewNodeSnapshotMapper.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(List.of());
        lenient().when(reviewRecordMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of());
        when(aiGatewayService.stream(any(), any(), any(), any(), any(), any(),
                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());

        service.generateSummary(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req());
        Map<?, ?> map = (Map<?, ?>) doneAssemblerCaptor.getValue().apply("x".repeat(9000));

        String markdown = (String) map.get("summaryMarkdown");
        assertTrue(markdown.startsWith("x".repeat(AiReviewSummaryServiceImpl.SUMMARY_MAX_LENGTH)));
        assertTrue(markdown.endsWith("（内容超长已截断）"));
    }

    @Test
    void generateSummary_preludeSendsStatisticsFrame() throws Exception {
        stubHappyPath();
        when(reviewNodeSnapshotMapper.listAssociatedByReviewId(REVIEW_ID, Constants.NodeType.CASE))
                .thenReturn(List.of());
        lenient().when(reviewRecordMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of());
        when(aiGatewayService.stream(any(), any(), any(), any(), any(),
                preludeCaptor.capture(), any())).thenReturn(new SseEmitter());

        service.generateSummary(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, req());
        SseEmitter mockEmitter = org.mockito.Mockito.mock(SseEmitter.class);
        preludeCaptor.getValue().accept(mockEmitter);

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    // ==================== 查询 ====================

    @Test
    void getSummary_noSuccessRecord_returnsNull() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.COMPLETED));
        when(aiTaskMapper.findLatestSuccessByTypeAndTarget(Constants.AiTaskType.REVIEW_SUMMARY, REVIEW_ID))
                .thenReturn(null);
        assertNull(service.getSummary(REVIEW_ID, USER_ID));
    }

    @Test
    void getSummary_convertsResultAndSetsGeneratedAt() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.COMPLETED));
        AiAnalysisTask task = new AiAnalysisTask();
        task.setId(TASK_ID);
        task.setResult(Map.of("summaryMarkdown", "## 总结"));
        LocalDateTime updatedAt = LocalDateTime.now();
        task.setUpdatedAt(updatedAt);
        when(aiTaskMapper.findLatestSuccessByTypeAndTarget(Constants.AiTaskType.REVIEW_SUMMARY, REVIEW_ID))
                .thenReturn(task);

        AiReviewSummaryRespDTO result = service.getSummary(REVIEW_ID, USER_ID);

        assertEquals(updatedAt, result.getGeneratedAt());
    }
}
