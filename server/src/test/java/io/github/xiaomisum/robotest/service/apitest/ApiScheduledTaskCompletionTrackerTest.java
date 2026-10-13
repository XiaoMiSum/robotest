package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 混合任务完成判据（WP-8.1，BR-1.28）：平台组全部终态且批次内所有流水线终态方可裁定任务终态。
 * <p>核心不变量：判据只按库中状态裁定（无内存态、重启自愈）；平台组终态与失败取本触发的套件报告、
 * 终态任务执行记录与平台组空标记记录；历史触发的批次不参与判据；裁定幂等（任务非 running 一律不写）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiScheduledTaskCompletionTrackerTest {

    @Mock
    private ApiScheduledTaskMapper taskMapper;
    @Mock
    private ApiScheduledTaskExecutionMapper executionMapper;
    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiReportMapper reportMapper;

    private final List<ApiScheduledTaskExecution> taskRecords = new ArrayList<>();

    private ApiScheduledTask task;
    private ApiScheduledTaskCompletionTracker tracker;

    @BeforeEach
    void setUp() {
        taskRecords.clear();
        task = new ApiScheduledTask();
        task.setId(UUID.randomUUID());
        task.setProjectId(UUID.randomUUID());
        task.setTaskType("scene_execute");
        task.setLastExecutionStatus("running");
        task.setLastExecutionAt(LocalDateTime.now().minusSeconds(10));

        lenient().when(taskMapper.selectById(task.getId())).thenReturn(task);
        lenient().when(executionMapper.insert(any(ApiScheduledTaskExecution.class))).thenAnswer(inv -> {
            taskRecords.add(inv.getArgument(0));
            return 1;
        });
        lenient().when(executionRecordMapper.listBatchRecordsByTask(any())).thenReturn(List.of());
        lenient().when(reportMapper.selectLatestSuiteReportSince(any(), any(), any())).thenReturn(null);
        lenient().when(executionMapper.countTerminalSince(any(), any())).thenReturn(0L);
        lenient().when(executionMapper.countFailedSince(any(), any())).thenReturn(0L);
        lenient().when(sceneMapper.selectByIds(any())).thenReturn(List.of());
        tracker = new ApiScheduledTaskCompletionTracker(taskMapper, executionMapper,
                executionRecordMapper, sceneMapper, reportMapper);
    }

    @Test
    void finalizesSuccessWhenBatchAndPlatformReportSucceed() {
        ApiScene ciScene = scene("查询订单");
        when(executionRecordMapper.listBatchRecordsByTask(task.getId()))
                .thenReturn(List.of(batchRecord(ciScene.getId(), "success", null)));
        when(reportMapper.selectLatestSuiteReportSince(any(), any(), any()))
                .thenReturn(suiteReport(0));
        when(sceneMapper.selectByIds(any())).thenReturn(List.of(ciScene));

        assertTrue(tracker.tryFinalize(task.getId()));

        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertEquals("success", taskCaptor.getValue().getLastExecutionStatus());
        assertEquals(1, taskRecords.size());
        ApiScheduledTaskExecution record = taskRecords.get(0);
        assertEquals("success", record.getStatus());
        assertEquals("场景「查询订单」执行完成", record.getErrorMessage());
        assertEquals("scheduled", record.getTriggerType());
    }

    @Test
    void keepsRunningWhenBatchNotTerminal() {
        when(executionRecordMapper.listBatchRecordsByTask(task.getId()))
                .thenReturn(List.of(batchRecord(UUID.randomUUID(), "running", null)));

        assertFalse(tracker.tryFinalize(task.getId()));

        verify(taskMapper, never()).updateById(any(ApiScheduledTask.class));
        assertEquals(0, taskRecords.size());
    }

    @Test
    void finalizesFailedWhenBatchFailedAndTruncatesMessage() {
        ApiScene ciScene = scene("下单");
        String longMessage = "x".repeat(3000);
        when(executionRecordMapper.listBatchRecordsByTask(task.getId()))
                .thenReturn(List.of(batchRecord(ciScene.getId(), "failed", longMessage)));
        when(sceneMapper.selectByIds(any())).thenReturn(List.of(ciScene));
        // 平台组已结束（终态任务执行记录在案），失败来自流水线侧
        when(executionMapper.countTerminalSince(any(), any())).thenReturn(1L);

        assertTrue(tracker.tryFinalize(task.getId()));

        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertEquals("failed", taskCaptor.getValue().getLastExecutionStatus());
        assertEquals(1, taskRecords.size());
        assertEquals("failed", taskRecords.get(0).getStatus());
        assertEquals(2000, taskRecords.get(0).getErrorMessage().length());
        // 平台侧无报告时按无失败计，终局只看流水线侧
        assertNull(taskRecords.get(0).getReportId());
    }

    @Test
    void finalizesFailedWhenPlatformReportHasFailedScenes() {
        when(executionRecordMapper.listBatchRecordsByTask(task.getId()))
                .thenReturn(List.of(batchRecord(UUID.randomUUID(), "success", null)));
        when(reportMapper.selectLatestSuiteReportSince(any(), any(), any()))
                .thenReturn(suiteReport(2));

        assertTrue(tracker.tryFinalize(task.getId()));

        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertEquals("failed", taskCaptor.getValue().getLastExecutionStatus());
    }

    @Test
    void keepsRunningWhenPlatformGroupStillExecuting() {
        // 批次已终态但平台组尚无任何产出（报告与终态记录均未写）→ 平台组仍在执行，判据不满足
        when(executionRecordMapper.listBatchRecordsByTask(task.getId()))
                .thenReturn(List.of(batchRecord(UUID.randomUUID(), "success", null)));

        assertFalse(tracker.tryFinalize(task.getId()));

        verify(taskMapper, never()).updateById(any(ApiScheduledTask.class));
        assertEquals(0, taskRecords.size());
    }

    @Test
    void finalizesFailedWhenPlatformCrashedWithoutReport() {
        // 平台组崩溃（startSuite 异常 / 顶层 throwable）只写失败记录不产报告：终态与失败均由失败记录体现
        when(executionRecordMapper.listBatchRecordsByTask(task.getId()))
                .thenReturn(List.of(batchRecord(UUID.randomUUID(), "success", null)));
        when(executionMapper.countTerminalSince(any(), any())).thenReturn(1L);
        when(executionMapper.countFailedSince(any(), any())).thenReturn(1L);

        assertTrue(tracker.tryFinalize(task.getId()));

        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertEquals("failed", taskCaptor.getValue().getLastExecutionStatus());
    }

    @Test
    void ignoresWhenTaskNotRunning() {
        task.setLastExecutionStatus("success");

        assertFalse(tracker.tryFinalize(task.getId()));

        verify(taskMapper, never()).updateById(any(ApiScheduledTask.class));
    }

    @Test
    void ignoresWhenTaskHasNoBatchRecords() {
        assertFalse(tracker.tryFinalize(task.getId()));

        verify(taskMapper, never()).updateById(any(ApiScheduledTask.class));
    }

    @Test
    void ignoresBatchFrozenBeforeCurrentRunning() {
        // 历史触发的批次（冻结早于本次 running 时刻）不参与当前触发的判据
        ApiExecutionRecord stale = batchRecord(UUID.randomUUID(), "failed", null);
        stale.setCreatedAt(task.getLastExecutionAt().minusMinutes(5));
        when(executionRecordMapper.listBatchRecordsByTask(task.getId())).thenReturn(List.of(stale));

        assertFalse(tracker.tryFinalize(task.getId()));

        verify(taskMapper, never()).updateById(any(ApiScheduledTask.class));
    }

    @Test
    void scanFinalizesEachRunningTaskAndSurvivesSingleTaskFailure() {
        ApiScheduledTask broken = new ApiScheduledTask();
        broken.setId(UUID.randomUUID());
        broken.setTaskType("scene_execute");
        broken.setLastExecutionStatus("running");
        broken.setLastExecutionAt(LocalDateTime.now());
        when(taskMapper.listRunningTestPlan()).thenReturn(List.of(broken, task));
        when(executionRecordMapper.listBatchRecordsByTask(broken.getId()))
                .thenThrow(new IllegalStateException("db down"));
        when(executionRecordMapper.listBatchRecordsByTask(task.getId()))
                .thenReturn(List.of(batchRecord(UUID.randomUUID(), "success", null)));
        // 平台组已结束（套件报告在案）且无失败，任务整体成功
        when(reportMapper.selectLatestSuiteReportSince(any(), any(), any()))
                .thenReturn(suiteReport(0));

        tracker.scan();

        verify(taskMapper).updateById(any(ApiScheduledTask.class));
    }

    @Test
    void scanSkipsRoundWhenTaskQueryFails() {
        when(taskMapper.listRunningTestPlan()).thenThrow(new IllegalStateException("db down"));

        tracker.scan();

        verify(taskMapper, never()).updateById(any(ApiScheduledTask.class));
    }

    private ApiScene scene(String name) {
        ApiScene scene = new ApiScene();
        scene.setId(UUID.randomUUID());
        scene.setName(name);
        return scene;
    }

    private ApiExecutionRecord batchRecord(UUID sceneId, String status, String errorMessage) {
        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(task.getProjectId());
        record.setSceneId(sceneId);
        record.setBatchId(UUID.randomUUID().toString());
        record.setScheduledTaskId(task.getId());
        record.setStatus(status);
        record.setErrorMessage(errorMessage);
        record.setTriggerType("scheduled");
        record.setCreatedAt(task.getLastExecutionAt().plusSeconds(2));
        return record;
    }

    private ApiReport suiteReport(int failedScenes) {
        ApiReport report = new ApiReport();
        report.setId(UUID.randomUUID());
        report.setSummary(Map.of("failedScenes", failedScenes));
        return report;
    }
}
