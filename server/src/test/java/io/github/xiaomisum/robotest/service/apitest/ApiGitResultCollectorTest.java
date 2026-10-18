package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.service.apitest.ApiGitResultCollector.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CI 结果回收编排（WP-7.1，GitLab 集成详设 4.2 第 4-6 步 / 4.6）。
 * <p>核心不变量：completion 通道暂存缺失只推迟、poll 通道整批失败降级附流水线链接（BR-1.34）；
 * 可用信封逐条条件终态回写，缺信封记录置 skipped 留痕并由批次 summary 对账差值；
 * 报告先插入后占位、占位落空回删；单飞挡同批次并发；批次终态化后暂存被清除。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiGitResultCollectorTest {

    private static final String BASE64_KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String BATCH = "3f6a1c9e-8a4c-4d3e-9a1b-0b2c4d5e6f70";
    private static final String PIPELINE_URL =
            "https://gitlab.example.com/qa/order-api-tests/-/pipelines/88";
    private static final String REF_A = "com.example.api.OrderApiTest#createOrder";
    private static final String REF_B = "com.example.api.OrderApiTest#listByStatus";

    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiGitRepoMapper gitRepoMapper;
    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiReportMapper reportMapper;
    @Mock
    private ApiGitBatchService batchService;
    @Mock
    private ApiSuiteReportAppender appender;

    private ApiGitRepo repo;
    private ApiExecutionRecord recordA;
    private ApiExecutionRecord recordB;
    private ApiGitArtifactStash artifactStash;
    private ApiGitResultCollector collector;

    @BeforeEach
    void setUp() {
        repo = new ApiGitRepo();
        repo.setId(UUID.randomUUID());
        repo.setGitlabBaseUrl("https://gitlab.example.com");
        repo.setRepoPath("qa/order-api-tests");
        repo.setEnabled(true);
        repo.setTokenCipher(SecretCryptoUtil.encrypt(SecretCryptoUtil.parseKey(BASE64_KEY), "glpat-xxxx"));

        recordA = record(REF_A);
        recordB = record(REF_B);
        artifactStash = new ApiGitArtifactStash();

        lenient().when(executionRecordMapper.selectByBatchId(BATCH)).thenReturn(List.of(recordA, recordB));
        lenient().when(gitRepoMapper.selectById(repo.getId())).thenReturn(repo);
        lenient().when(sceneMapper.selectBatchIds(any())).thenReturn(List.of(scene(recordA, "创建订单"),
                scene(recordB, "按状态查询")));
        lenient().when(executionRecordMapper.finishRecord(
                any(), any(), any(), any(), any(), any())).thenReturn(1);
        lenient().when(batchService.finishBatch(anyString(), anyString(), any(), any())).thenReturn(true);

        collector = new ApiGitResultCollector(executionRecordMapper, gitRepoMapper, sceneMapper,
                reportMapper, artifactStash, batchService, appender, new ApiTestProperties(),
                Runnable::run, BASE64_KEY);
    }

    // ========== 可用信封：逐条回写 ==========

    @Test
    @SuppressWarnings("unchecked")
    void matchedEnvelopeWritesReportAndMissingOneIsSkipped() throws Exception {
        artifactStash.put(BATCH, zipOf("__Ryze_Native_Result__/" + REF_A + ".json", envelope(REF_A, "passed")));

        collector.collect(BATCH, Channel.COMPLETION, "success");

        ArgumentCaptor<ApiReport> reports = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(reports.capture());
        ApiReport report = reports.getValue();
        assertEquals("scene", report.getReportType());
        assertEquals("success", report.getStatus());
        assertEquals(recordA.getId(), report.getExecutionRecordId());
        assertEquals(recordA.getSceneId(), report.getExternalId());
        assertEquals("创建订单-2026-10-13 10:00:00", report.getName());
        assertEquals(1, report.getSummary().get("total"));
        assertEquals(1, report.getSummary().get("passed"));
        Map<String, Object> dataset = report.getResult();
        assertEquals("gitlab", dataset.get("execEngine"));
        assertEquals("创建订单", dataset.get("sceneName"));
        assertEquals("success", dataset.get("status"));
        List<?> steps = (List<?>) dataset.get("steps");
        assertEquals(1, steps.size());
        Map<String, Object> step = (Map<String, Object>) steps.get(0);
        assertEquals("success", step.get("status"));
        assertEquals("http://api.example.com/orders", ((Map<?, ?>) step.get("request")).get("url"));

        ArgumentCaptor<Map<String, Object>> summary = ArgumentCaptor.forClass(Map.class);
        verify(executionRecordMapper).finishRecord(eq(recordA.getId()), eq("success"), isNull(),
                eq(report.getId()), any(), summary.capture());
        // 缺信封记录：skipped 留痕 1000017715，不写报告
        verify(executionRecordMapper).finishRecord(eq(recordB.getId()), eq("skipped"),
                contains("1000017715"), isNull(), isNull(), any());
        assertEquals(2, summary.getValue().get("expectedCases"));
        assertEquals(1, summary.getValue().get("actualCases"));
        assertEquals(1, summary.getValue().get("errorFailed"));
        // 回写完成后合并套件报告（WP-7.2）
        verify(appender).append(argThat((List<ApiExecutionRecord> list) -> list.size() == 2));
        assertEquals(PIPELINE_URL, summary.getValue().get("pipelineUrl"));
    }

    @Test
    void failedStepMarksRecordFailedAndReportPartial() throws Exception {
        artifactStash.put(BATCH, zipOf("__Ryze_Native_Result__/" + REF_A + ".json", envelope(REF_A, "failed")));

        collector.collect(BATCH, Channel.COMPLETION, "success");

        verify(executionRecordMapper).finishRecord(eq(recordA.getId()), eq("failed"),
                contains("断言失败"), any(), isNull(), any());
        ArgumentCaptor<ApiReport> reports = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(reports.capture());
        assertEquals("partial", reports.getValue().getStatus());
        assertEquals(1, reports.getValue().getSummary().get("failed"));
    }

    @Test
    void brokenStepMarksRecordErrorAndReportFailed() throws Exception {
        artifactStash.put(BATCH, zipOf("__Ryze_Native_Result__/" + REF_A + ".json", envelope(REF_A, "broken")));

        collector.collect(BATCH, Channel.COMPLETION, "success");

        verify(executionRecordMapper).finishRecord(eq(recordA.getId()), eq("error"),
                any(), any(), any(), any());
        ArgumentCaptor<ApiReport> reports = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(reports.capture());
        assertEquals("failed", reports.getValue().getStatus());
    }

    // ========== 无可用信封：整批失败降级（BR-1.34 / 详设 4.6） ==========

    @Test
    @SuppressWarnings("unchecked")
    void pollWithNoArtifactsDegradesBatchWithPipelineLink() {
        collector.collect(BATCH, Channel.POLL, "success");

        ArgumentCaptor<Map<String, Object>> summary = ArgumentCaptor.forClass(Map.class);
        verify(batchService).finishBatch(eq(BATCH), eq("failed"),
                argThat((String m) -> m.contains("1000017715") && m.contains(PIPELINE_URL)),
                summary.capture());
        assertEquals(2, summary.getValue().get("expectedCases"));
        assertEquals(0, summary.getValue().get("actualCases"));
        assertEquals(2, summary.getValue().get("errorFailed"));
        verify(executionRecordMapper, never()).finishRecord(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(reportMapper);
        // 降级同样入套件（占位数据集），不留报告空洞（WP-7.2）
        verify(appender).append(argThat((List<ApiExecutionRecord> list) -> list.size() == 2));
    }

    @Test
    void completionChannelDefersWhenArtifactsNotReady() {
        // 推送尚未到达（或已重试失败）：暂存为空，completion 只推迟，不消费也不终态化
        collector.collect(BATCH, Channel.COMPLETION, "success");

        // completion 只推迟，收尾交由轮询通道
        verify(batchService, never()).finishBatch(anyString(), anyString(), any(), any());
        verify(executionRecordMapper, never()).finishRecord(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(reportMapper);
        verifyNoInteractions(appender);
    }

    @Test
    void successfulCollectEvictsStashAndDegradeAlsoEvicts() throws Exception {
        artifactStash.put(BATCH, zipOf("__Ryze_Native_Result__/" + REF_A + ".json", envelope(REF_A, "passed")));

        collector.collect(BATCH, Channel.COMPLETION, "success");
        // 批次终态化后暂存被清除，异常残留由 TTL 兜底（详设 4.2 第 4 步）
        assertNull(artifactStash.get(BATCH));

        artifactStash.put(BATCH, zipOf("__Ryze_Native_Result__/" + REF_A + ".json", "not-a-json"));
        collector.collect(BATCH, Channel.POLL, "success");
        assertNull(artifactStash.get(BATCH));
    }

    @Test
    void unparsableEnvelopeDegradesEvenOnCompletionChannel() throws Exception {
        artifactStash.put(BATCH, zipOf("__Ryze_Native_Result__/" + REF_A + ".json", "not-a-json"));

        collector.collect(BATCH, Channel.COMPLETION, "success");

        verify(batchService).finishBatch(eq(BATCH), eq("failed"),
                argThat((String m) -> m.contains("1000017715") && m.contains(PIPELINE_URL)), any());
        verify(executionRecordMapper, never()).finishRecord(any(), any(), any(), any(), any(), any());
    }

    @Test
    void foreignEnvelopesAreNotCountedAsUsable() throws Exception {
        String foreign = "com.example.foreign.OtherTest#smoke";
        artifactStash.put(BATCH, zipOf("__Ryze_Native_Result__/" + foreign + ".json", envelope(foreign, "passed")));

        collector.collect(BATCH, Channel.COMPLETION, "success");

        // 归属判定按批次快照 sourceRef，外来信封不计入本批可用数（详设 4.6）
        verify(batchService).finishBatch(eq(BATCH), eq("failed"), contains("1000017715"), any());
        verify(executionRecordMapper, never()).finishRecord(any(), any(), any(), any(), any(), any());
    }

    // ========== 闸门、单飞与推迟 ==========

    @Test
    void terminalBatchIsSkippedEntirely() {
        recordA.setStatus("success");
        recordB.setStatus("skipped");

        collector.collect(BATCH, Channel.POLL, "success");

        verifyNoInteractions(gitRepoMapper, batchService, reportMapper);
        verify(executionRecordMapper, never()).finishRecord(any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingRepoDefersWithoutTouchingGitLab() {
        when(gitRepoMapper.selectById(repo.getId())).thenReturn(null);

        collector.collect(BATCH, Channel.POLL, "success");

        verifyNoInteractions(batchService, reportMapper);
    }

    @Test
    void claimMissDeletesOrphanReport() throws Exception {
        artifactStash.put(BATCH, zipOf("__Ryze_Native_Result__/" + REF_A + ".json", envelope(REF_A, "passed")));
        when(executionRecordMapper.finishRecord(eq(recordA.getId()), any(), any(), any(), any(), any()))
                .thenReturn(0);

        collector.collect(BATCH, Channel.COMPLETION, "success");

        // 占位落空（另一通道先到）：回删已插入报告，不留孤儿
        ArgumentCaptor<ApiReport> reports = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(reports.capture());
        verify(reportMapper).deleteById(reports.getValue().getId());
    }

    @Test
    void concurrentCollectIsSingleFlight() throws Exception {
        artifactStash.put(BATCH, zipOf("__Ryze_Native_Result__/" + REF_A + ".json", envelope(REF_A, "passed")));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // 以场景装配为阻塞点：此时已持有同批次单飞，第二次进入必须被挡
        when(sceneMapper.selectBatchIds(any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of(scene(recordA, "创建订单"));
        });
        Thread worker = new Thread(() -> collector.collect(BATCH, Channel.POLL, "success"));
        worker.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        collector.collect(BATCH, Channel.POLL, "success");
        release.countDown();
        worker.join(5000);

        verify(sceneMapper, times(1)).selectBatchIds(any());
    }

    @Test
    void asyncEntrySwallowsFailureForPollingFallback() {
        when(executionRecordMapper.selectByBatchId(BATCH)).thenThrow(new RuntimeException("db down"));

        collector.collectAsync(BATCH, Channel.POLL, "success");
    }

    // ========== 构造工具 ==========

    private ApiExecutionRecord record(String sourceRef) {
        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(UUID.randomUUID());
        record.setSceneId(UUID.randomUUID());
        record.setExecutionMode("platform");
        record.setStatus("running");
        record.setTriggerType("manual");
        record.setSource("scene");
        record.setExecutedAt(LocalDateTime.of(2026, 10, 13, 10, 0, 0));
        record.setRepoId(repo.getId());
        record.setBatchId(BATCH);
        record.setSourceRef(sourceRef);
        record.setPipelineIid(88L);
        record.setPipelineUrl(PIPELINE_URL);
        return record;
    }

    private ApiScene scene(ApiExecutionRecord record, String name) {
        ApiScene scene = new ApiScene();
        scene.setId(record.getSceneId());
        scene.setProjectId(record.getProjectId());
        scene.setName(name);
        return scene;
    }

    /** 接入方侧信封（{ testClass, testMethod, result }，字段口径同 RyzeEnvelopeParser 金样） */
    private static String envelope(String sourceRef, String stepStatus) {
        int split = sourceRef.indexOf('#');
        String reason = "failed".equals(stepStatus) ? ",\"throwable\":{\"message\":\"断言失败：code 期望 0\"}" : "";
        return "{\"testClass\":\"" + sourceRef.substring(0, split)
                + "\",\"testMethod\":\"" + sourceRef.substring(split + 1)
                + "\",\"result\":{\"title\":\"suite\",\"status\":\"passed\",\"children\":[{"
                + "\"title\":\"调用接口\",\"status\":\"" + stepStatus + "\"" + reason + ","
                + "\"request\":{\"url\":\"http://api.example.com/orders\",\"method\":\"GET\"}}]}}";
    }

    private static byte[] zipOf(String name, String content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
