package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 套件报告合并（WP-7.2，GitLab 集成详设 4.5）。
 * <p>核心不变量：平台组已首写按 sceneId 幂等追加；纯 CI 任务无首写报告由本入口创建；
 * 无结果记录以 failed 占位数据集计入；每次合并全量重算 summary 与 status；二次更新不产生空洞。
 */
@ExtendWith(MockitoExtension.class)
class ApiSuiteReportAppenderTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID S1 = UUID.randomUUID();
    private static final UUID S2 = UUID.randomUUID();
    private static final UUID S3 = UUID.randomUUID();
    private static final LocalDateTime FROZEN_AT = LocalDateTime.of(2026, 10, 13, 10, 0, 0);

    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiReportMapper reportMapper;
    @Mock
    private ApiScheduledTaskMapper scheduledTaskMapper;
    @Mock
    private ApiSceneMapper sceneMapper;

    private ApiSuiteReportAppender appender;

    @BeforeEach
    void setUp() {
        appender = new ApiSuiteReportAppender(executionRecordMapper, reportMapper,
                scheduledTaskMapper, sceneMapper);
        lenient().when(scheduledTaskMapper.selectById(TASK_ID)).thenReturn(task());
    }

    // ========== 单场景执行：不适用套件 ==========

    @Test
    void batchWithoutTaskSkipsSuiteMerge() {
        ApiExecutionRecord record = record(S1, "com.example.A#m", null);
        record.setScheduledTaskId(null);

        appender.append(List.of(record));

        verifyNoInteractions(reportMapper);
        verifyNoInteractions(sceneMapper);
    }

    // ========== 纯 CI 任务：首写 ==========

    @Test
    @SuppressWarnings("unchecked")
    void createsSuiteWhenNoExistingReport() {
        ApiExecutionRecord withReport = record(S1, "com.example.A#m1", UUID.randomUUID());
        ApiExecutionRecord withoutReport = record(S2, "com.example.A#m2", null);
        when(reportMapper.selectLatestSuiteReportSince(TASK_ID, "schedule", FROZEN_AT)).thenReturn(null);
        when(reportMapper.selectById(withReport.getReportId())).thenReturn(sceneReport(withReport.getSceneId()));
        when(sceneMapper.selectByIds(any())).thenReturn(List.of(scene(S2, "查询订单")));

        appender.append(List.of(withReport, withoutReport));

        ArgumentCaptor<ApiReport> inserted = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(inserted.capture());
        ApiReport suite = inserted.getValue();
        assertEquals("suite", suite.getReportType());
        assertEquals(TASK_ID, suite.getExternalId());
        assertEquals("schedule", suite.getSource());
        assertEquals("platform", suite.getExecutionMode());
        assertEquals(PROJECT_ID, suite.getProjectId());
        assertEquals("下单任务-2026-10-13 10:00:00", suite.getName());
        // 无结果记录以 failed 占位计入 → 整体 failed
        assertEquals("failed", suite.getStatus());

        Map<String, Object> result = suite.getResult();
        List<?> scenes = (List<?>) result.get("scenes");
        assertEquals(2, scenes.size());
        assertNull(result.get("environmentName"));
        assertEquals(List.of(), result.get("preprocessors"));

        // 场景数据集：有报告复用 result，无结果占位（failed、steps 空、汇总全 0）
        Map<String, Object> first = (Map<String, Object>) scenes.get(0);
        assertEquals("gitlab", first.get("execEngine"));
        assertEquals("success", first.get("status"));
        Map<String, Object> second = (Map<String, Object>) scenes.get(1);
        assertEquals("failed", second.get("status"));
        assertEquals("查询订单", second.get("sceneName"));

        assertEquals(2, suite.getSummary().get("totalScenes"));
        assertEquals(1, suite.getSummary().get("passedScenes"));
        assertEquals(1, suite.getSummary().get("failedScenes"));
        // 批次耗时口径：取场景数据集 durationMs 最大值
        assertEquals(320L, suite.getSummary().get("durationMs"));
    }

    // ========== 混合任务：追加更新 ==========

    @Test
    @SuppressWarnings("unchecked")
    void appendsIntoExistingSuiteByIdempotentSceneId() {
        ApiReport existing = suiteWithPlatformScenes();
        ApiExecutionRecord s1Recovered = record(S1, "com.example.A#m1", UUID.randomUUID());
        ApiExecutionRecord s3Missing = record(S3, "com.example.A#m3", null);
        when(reportMapper.selectLatestSuiteReportSince(TASK_ID, "schedule", FROZEN_AT)).thenReturn(existing);
        when(reportMapper.selectById(s1Recovered.getReportId())).thenReturn(sceneReport(S1));

        appender.append(List.of(s1Recovered, s3Missing));

        // C11：载体仅携带本次变更三列
        ArgumentCaptor<ApiReport> updated = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).updateById(updated.capture());
        ApiReport update = updated.getValue();
        assertEquals(existing.getId(), update.getId());
        assertNull(update.getSource());
        assertNull(update.getExternalId());
        assertNull(update.getName());

        List<Object> scenes = (List<Object>) update.getResult().get("scenes");
        assertEquals(3, scenes.size());
        // S1 平台数据集被 gitlab 数据集原位替换；S2 平台数据集保留；S3 failed 占位追加
        assertEquals("gitlab", ((Map<String, Object>) scenes.get(0)).get("execEngine"));
        assertEquals("platform", ((Map<String, Object>) scenes.get(1)).get("execEngine"));
        assertEquals(S3.toString(), ((Map<String, Object>) scenes.get(2)).get("sceneId"));

        assertEquals("failed", update.getStatus());
        assertEquals(3, update.getSummary().get("totalScenes"));
        assertEquals(1, update.getSummary().get("passedScenes"));
        assertEquals(2, update.getSummary().get("failedScenes"));
        // 步数：S1 gitlab 1 步 + S2 平台 3 步 + S3 占位 0 步
        assertEquals(4, update.getSummary().get("totalSteps"));
        assertEquals(2, update.getSummary().get("failedSteps"));
        // 混合任务追加保留平台组首写的触发总耗时
        assertEquals(1234L, update.getSummary().get("durationMs"));
        verify(reportMapper, never()).insert(any(ApiReport.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatedAppendIsIdempotent() {
        ApiReport existing = suiteWithPlatformScenes();
        ApiExecutionRecord s1Recovered = record(S1, "com.example.A#m1", UUID.randomUUID());
        when(reportMapper.selectLatestSuiteReportSince(TASK_ID, "schedule", FROZEN_AT)).thenReturn(existing);
        when(reportMapper.selectById(s1Recovered.getReportId())).thenReturn(sceneReport(S1));

        appender.append(List.of(s1Recovered));

        ArgumentCaptor<ApiReport> updated = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).updateById(updated.capture());
        ApiReport firstPass = updated.getValue();

        // 第二次到达：以第一次更新后的库态再合并，场景不重复、汇总不变
        ApiReport reloaded = new ApiReport();
        reloaded.setId(firstPass.getId());
        reloaded.setSummary(firstPass.getSummary());
        reloaded.setResult(firstPass.getResult());
        when(reportMapper.selectLatestSuiteReportSince(TASK_ID, "schedule", FROZEN_AT)).thenReturn(reloaded);

        appender.append(List.of(s1Recovered));

        verify(reportMapper, times(2)).updateById(updated.capture());
        List<Object> scenes = (List<Object>) updated.getValue().getResult().get("scenes");
        assertEquals(2, scenes.size());
        assertEquals(2, updated.getValue().getSummary().get("totalScenes"));
    }

    @Test
    void allPassingCiScenesRestoreSuiteSuccess() {
        ApiReport existing = suiteWithPlatformScenes();
        ApiExecutionRecord s1Recovered = record(S1, "com.example.A#m1", UUID.randomUUID());
        ApiExecutionRecord s2Recovered = record(S2, "com.example.A#m2", UUID.randomUUID());
        when(reportMapper.selectLatestSuiteReportSince(TASK_ID, "schedule", FROZEN_AT)).thenReturn(existing);
        when(reportMapper.selectById(any())).thenReturn(sceneReport(S2));

        appender.append(List.of(s1Recovered, s2Recovered));

        // 平台组失败场景的回收结果翻盘：全量重算后整体回到 success
        ArgumentCaptor<ApiReport> updated = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).updateById(updated.capture());
        assertEquals("success", updated.getValue().getStatus());
        assertEquals(0, updated.getValue().getSummary().get("failedScenes"));
        assertEquals(2, updated.getValue().getSummary().get("passedScenes"));
    }

    // ========== 夹具 ==========

    private static ApiExecutionRecord record(UUID sceneId, String sourceRef, UUID reportId) {
        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(PROJECT_ID);
        record.setSceneId(sceneId);
        record.setSourceRef(sourceRef);
        record.setReportId(reportId);
        record.setScheduledTaskId(TASK_ID);
        record.setStatus("success");
        record.setCreatedAt(FROZEN_AT);
        return record;
    }

    private static ApiScene scene(UUID id, String name) {
        ApiScene scene = new ApiScene();
        scene.setId(id);
        scene.setName(name);
        return scene;
    }

    private static ApiScheduledTask task() {
        ApiScheduledTask task = new ApiScheduledTask();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setName("下单任务");
        return task;
    }

    /** CI 引擎场景级报告（result 即 2.3.1 场景数据集）：success、1 步通过、耗时 320ms */
    private static ApiReport sceneReport(UUID sceneId) {
        ApiReport report = new ApiReport();
        report.setId(UUID.randomUUID());
        Map<String, Object> result = sceneDataset(sceneId, "success", 1, 1, 0, 320L);
        result.put("execEngine", "gitlab");
        report.setResult(result);
        return report;
    }

    /** 既有套件报告（平台组首写）：S1 success（1 步）、S2 failed（3 步 2 失败 1 跳过）、durationMs 1234 */
    private ApiReport suiteWithPlatformScenes() {
        ApiReport suite = new ApiReport();
        suite.setId(UUID.randomUUID());
        suite.setReportType("suite");
        suite.setExternalId(TASK_ID);
        suite.setSource("schedule");
        suite.setStatus("failed");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalScenes", 2);
        summary.put("passedScenes", 1);
        summary.put("failedScenes", 1);
        summary.put("totalSteps", 4);
        summary.put("passedSteps", 1);
        summary.put("failedSteps", 2);
        summary.put("skippedSteps", 1);
        summary.put("durationMs", 1234L);
        suite.setSummary(summary);

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("reportType", "suite");
        dataset.put("taskId", TASK_ID.toString());
        dataset.put("taskName", "下单任务");
        dataset.put("source", "schedule");
        dataset.put("status", "failed");
        dataset.put("summary", summary);
        dataset.put("scenes", List.of(
                sceneDataset(S1, "success", 1, 1, 0, 100L),
                sceneDataset(S2, "failed", 3, 0, 2, 200L)));
        suite.setResult(dataset);
        return suite;
    }

    private static Map<String, Object> sceneDataset(UUID sceneId, String status,
            int total, int passed, int failed, long durationMs) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("skipped", Math.max(0, total - passed - failed));
        summary.put("durationMs", durationMs);

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("sceneId", sceneId.toString());
        dataset.put("sceneName", "场景-" + sceneId.toString().substring(0, 8));
        dataset.put("status", status);
        dataset.put("execEngine", "platform");
        dataset.put("summary", summary);
        dataset.put("environmentName", null);
        dataset.put("steps", List.of());
        return dataset;
    }
}
