package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.SceneExecutionService;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.SceneSuiteBuilder;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvironmentSnapshotProvider;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时任务引擎分组（WP-8.1，GitLab 集成详设 4.4、定时任务详设 27 §2）。
 * <p>核心不变量：先分组后过滤（BR-1.27）；流水线组冻结一条批次后触发（BR-1.23）；
 * 触发失败不拦平台组；混合任务任务终态交完成判据裁定（BR-1.28）；平台组行为不变。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestPlanTaskHandlerTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ENV_ID = UUID.randomUUID();

    @Mock
    private SceneExecutionService sceneExecutionService;
    @Mock
    private EnvironmentSnapshotProvider environmentSnapshotFactory;
    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ProjectModuleMapper moduleMapper;
    @Mock
    private ApiScheduledTaskMapper taskMapper;
    @Mock
    private ApiScheduledTaskExecutionMapper executionMapper;
    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiReportMapper reportMapper;
    @Mock
    private ApiGitRepoMapper gitRepoMapper;
    @Mock
    private ApiGitBatchService apiGitBatchService;
    @Mock
    private ApiScheduledTaskCompletionTracker completionTracker;

    private ApiGitRepo repo;
    private TestPlanTaskHandler handler;

    @BeforeEach
    void setUp() {
        repo = new ApiGitRepo();
        repo.setId(UUID.randomUUID());
        repo.setProjectId(PROJECT_ID);
        repo.setImportMode("gitlab_ci");
        repo.setEnabled(true);

        handler = new TestPlanTaskHandler();
        inject(handler, "sceneExecutionService", sceneExecutionService);
        inject(handler, "environmentSnapshotFactory", environmentSnapshotFactory);
        inject(handler, "sceneMapper", sceneMapper);
        inject(handler, "moduleMapper", moduleMapper);
        inject(handler, "taskMapper", taskMapper);
        inject(handler, "executionMapper", executionMapper);
        inject(handler, "executionRecordMapper", executionRecordMapper);
        inject(handler, "reportMapper", reportMapper);
        inject(handler, "suiteBuilder", new SceneSuiteBuilder());
        inject(handler, "gitRepoMapper", gitRepoMapper);
        inject(handler, "apiGitBatchService", apiGitBatchService);
        inject(handler, "completionTracker", completionTracker);

        lenient().when(environmentSnapshotFactory.resolve(PROJECT_ID, ENV_ID)).thenReturn(EnvSnapshot.empty());
        lenient().when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(repo);
    }

    @Test
    void mixedTriggerFreezesCiBatchThenRunsPlatformGroupAndDefersFinalStatus() throws Exception {
        ApiScene platformScene = platformScene("平台场景");
        ApiScene ciScene = ciScene("流水线场景");
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(platformScene, ciScene));
        when(apiGitBatchService.trigger(any(), anyList(), any(), anyString(), anyString()))
                .thenReturn(new ApiGitBatchService.Result("batch-1", UUID.randomUUID()));
        stubPlatformSuccess(platformScene);

        handler.executeTask(testPlanTask(), ProjectAccessGuard.SYSTEM_OPERATOR_ID, "scheduled", LocalDateTime.now());

        // 流水线组：整组冻结为一条批次，票据带任务 ID、触发类型与来源（BR-1.23）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ApiScene>> ciCaptor = ArgumentCaptor.forClass(List.class);
        verify(apiGitBatchService).trigger(eq(repo), ciCaptor.capture(), eq(TASK_ID),
                eq("scheduled"), eq("schedule"));
        assertEquals(List.of(ciScene), ciCaptor.getValue());
        // 平台组：套件只组装平台场景，经 startSuite 一次执行，流水线场景不进平台链路
        verify(sceneExecutionService, never()).execute(any(), any(), any(), any(), any());
        ArgumentCaptor<Map<String, Object>> suiteCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sceneExecutionService).startSuite(suiteCaptor.capture(), eq(PROJECT_ID));
        assertEquals(1, ((List<?>) suiteCaptor.getValue().get("children")).size());
        // 平台组任务记录照旧即时写；任务终态不落，交完成判据裁定（BR-1.28）
        ArgumentCaptor<ApiScheduledTaskExecution> recordCaptor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(recordCaptor.capture());
        assertEquals("success", recordCaptor.getValue().getStatus());
        assertTrue(recordCaptor.getValue().getErrorMessage().contains("平台场景"));
        verify(completionTracker).tryFinalize(TASK_ID);
        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertEquals("running", taskCaptor.getValue().getLastExecutionStatus());
    }

    @Test
    void ciRepoDisabledWritesFailedRecordsWithoutTriggering() throws Exception {
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(ciScene("流水线场景")));
        repo.setEnabled(false);

        handler.executeTask(testPlanTask(), ProjectAccessGuard.SYSTEM_OPERATOR_ID, "scheduled", LocalDateTime.now());

        verify(apiGitBatchService, never()).trigger(any(), anyList(), any(), anyString(), anyString());
        verify(sceneExecutionService, never()).startSuite(any(), any());
        ArgumentCaptor<ApiScheduledTaskExecution> recordCaptor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(recordCaptor.capture());
        assertEquals("failed", recordCaptor.getValue().getStatus());
        assertTrue(recordCaptor.getValue().getErrorMessage().contains("1000017707"));
        verifyLastTaskStatus("failed");
    }

    @Test
    void ciDraftSceneSkippedAndPlatformGroupStillRuns() throws Exception {
        ApiScene platformScene = platformScene("平台场景");
        ApiScene draftCiScene = ciScene("草稿流水线场景");
        draftCiScene.setStatus("draft");
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(platformScene, draftCiScene));
        stubPlatformSuccess(platformScene);

        handler.executeTask(testPlanTask(), ProjectAccessGuard.SYSTEM_OPERATOR_ID, "scheduled", LocalDateTime.now());

        // 草稿流水线场景留痕跳过，不冻结批次；平台组照常执行并直接终局
        verify(apiGitBatchService, never()).trigger(any(), anyList(), any(), anyString(), anyString());
        verify(sceneExecutionService).startSuite(any(), eq(PROJECT_ID));
        verify(completionTracker, never()).tryFinalize(any());
        verifyLastTaskStatus("success");
    }

    @Test
    void platformSceneWithoutEnabledStepsDoesNotSkipCiGroup() throws Exception {
        // BR-1.27：「无启用步骤」只是平台组的不可执行原因，流水线组照常触发
        ApiScene emptyPlatformScene = platformScene("空步骤平台场景");
        emptyPlatformScene.setSteps(List.of());
        ApiScene ciScene = ciScene("流水线场景");
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(emptyPlatformScene, ciScene));
        when(apiGitBatchService.trigger(any(), anyList(), any(), anyString(), anyString()))
                .thenReturn(new ApiGitBatchService.Result("batch-1", UUID.randomUUID()));

        handler.executeTask(testPlanTask(), ProjectAccessGuard.SYSTEM_OPERATOR_ID, "scheduled", LocalDateTime.now());

        verify(apiGitBatchService).trigger(eq(repo), anyList(), eq(TASK_ID), eq("scheduled"), eq("schedule"));
        verify(sceneExecutionService, never()).startSuite(any(), any());
        verify(completionTracker).tryFinalize(TASK_ID);
        // 平台组无可用场景：空步骤场景照旧 skipped 留痕，另写终态标记记录（判据识别平台侧已结束）
        ArgumentCaptor<ApiScheduledTaskExecution> recordCaptor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper, times(2)).insert(recordCaptor.capture());
        assertTrue(recordCaptor.getAllValues().stream().anyMatch(r ->
                "success".equals(r.getStatus()) && r.getErrorMessage().contains("平台组无可用场景")));
        // 平台侧无失败留痕，任务终态交完成判据
        verify(taskMapper, times(1)).updateById(any(ApiScheduledTask.class));
    }

    @Test
    void ciRepoDisabledWithPlatformScenesFailsTask() throws Exception {
        // 仓库禁用的流水线场景写 failed 留痕且无批次：平台组照常执行后，任务终局须一并计流水线侧失败
        ApiScene platformScene = platformScene("平台场景");
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(platformScene, ciScene("流水线场景")));
        repo.setEnabled(false);
        stubPlatformSuccess(platformScene);

        handler.executeTask(testPlanTask(), ProjectAccessGuard.SYSTEM_OPERATOR_ID, "scheduled", LocalDateTime.now());

        verify(apiGitBatchService, never()).trigger(any(), anyList(), any(), anyString(), anyString());
        verify(sceneExecutionService).startSuite(any(), eq(PROJECT_ID));
        verify(completionTracker, never()).tryFinalize(any());
        verifyLastTaskStatus("failed");
    }

    @Test
    void triggerFailureDoesNotBlockPlatformGroup() throws Exception {
        ApiScene platformScene = platformScene("平台场景");
        ApiScene ciScene = ciScene("流水线场景");
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(platformScene, ciScene));
        when(apiGitBatchService.trigger(any(), anyList(), any(), anyString(), anyString()))
                .thenThrow(new ServiceException(1000017704, "流水线触发失败"));
        stubPlatformSuccess(platformScene);

        handler.executeTask(testPlanTask(), ProjectAccessGuard.SYSTEM_OPERATOR_ID, "scheduled", LocalDateTime.now());

        // 触发失败整批已置 error（终态），平台组照常执行，终局由完成判据裁定
        verify(sceneExecutionService).startSuite(any(), eq(PROJECT_ID));
        verify(completionTracker).tryFinalize(TASK_ID);
        verify(taskMapper, times(1)).updateById(any(ApiScheduledTask.class));
    }

    @Test
    void platformSuiteFailureWithPendingBatchKeepsTaskRunning() throws Exception {
        ApiScene platformScene = platformScene("平台场景");
        ApiScene ciScene = ciScene("流水线场景");
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(platformScene, ciScene));
        when(apiGitBatchService.trigger(any(), anyList(), any(), anyString(), anyString()))
                .thenReturn(new ApiGitBatchService.Result("batch-1", UUID.randomUUID()));
        when(sceneExecutionService.startSuite(any(), eq(PROJECT_ID))).thenThrow(new IllegalStateException("引擎拒绝"));

        handler.executeTask(testPlanTask(), ProjectAccessGuard.SYSTEM_OPERATOR_ID, "scheduled", LocalDateTime.now());

        // 平台组失败留痕但不抢写任务终态：批次仍在跑，任务保持 running 待判据裁定
        ArgumentCaptor<ApiScheduledTaskExecution> recordCaptor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(recordCaptor.capture());
        assertEquals("failed", recordCaptor.getValue().getStatus());
        assertTrue(recordCaptor.getValue().getErrorMessage().contains("引擎拒绝"));
        verify(completionTracker).tryFinalize(TASK_ID);
        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertEquals("running", taskCaptor.getValue().getLastExecutionStatus());
    }

    @Test
    void platformOnlyTaskKeepsLegacyBehaviorWithoutGitLabInteraction() throws Exception {
        ApiScene platformScene = platformScene("平台场景");
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(platformScene));
        stubPlatformSuccess(platformScene);

        handler.executeTask(testPlanTask(), ProjectAccessGuard.SYSTEM_OPERATOR_ID, "scheduled", LocalDateTime.now());

        verify(gitRepoMapper, never()).selectByProject(any());
        verify(apiGitBatchService, never()).trigger(any(), anyList(), any(), anyString(), anyString());
        verify(completionTracker, never()).tryFinalize(any());
        verifyLastTaskStatus("success");
    }

    @Test
    void allGroupsEmptyMarksFailedWithoutAggregateRecord() {
        ApiScene draftScene = platformScene("草稿平台场景");
        draftScene.setStatus("draft");
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(draftScene));

        handler.executeTask(testPlanTask(), ProjectAccessGuard.SYSTEM_OPERATOR_ID, "scheduled", LocalDateTime.now());

        // 与分组前一致：逐场景 skipped 留痕 + 任务 failed，不追加汇总记录
        ArgumentCaptor<ApiScheduledTaskExecution> recordCaptor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(recordCaptor.capture());
        assertEquals("skipped", recordCaptor.getValue().getStatus());
        verifyLastTaskStatus("failed");
    }

    // ==================== 测试脚手架 ====================

    private void stubPlatformSuccess(ApiScene platformScene) throws Exception {
        when(sceneExecutionService.startSuite(any(), eq(PROJECT_ID)))
                .thenReturn(mappedTop(platformScene));
        when(sceneExecutionService.buildSceneDataset(eq(platformScene), any(), any(), any()))
                .thenReturn(successSnapshot(platformScene));
    }

    private void verifyLastTaskStatus(String expected) {
        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper, times(2)).updateById(taskCaptor.capture());
        assertEquals(expected, taskCaptor.getValue().getLastExecutionStatus());
    }

    private ApiScheduledTask testPlanTask() {
        ApiScheduledTask task = new ApiScheduledTask();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setTaskType("scene_execute");
        task.setName("混合任务");
        task.setExecutionScope("all");
        task.setEnvironmentId(ENV_ID);
        task.setEnabled(true);
        return task;
    }

    private ApiScene platformScene(String name) {
        ApiScene scene = new ApiScene();
        scene.setId(UUID.randomUUID());
        scene.setProjectId(PROJECT_ID);
        scene.setName(name);
        scene.setStatus("published");
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", UUID.randomUUID());
        step.put("enabled", true);
        step.put("name", "步骤1");
        step.put("requestConfig", Map.of("url", "https://example.com"));
        step.put("validators", List.of());
        step.put("extractors", List.of());
        step.put("variables", List.of());
        scene.setSteps(new ArrayList<>(List.of(step)));
        return scene;
    }

    private ApiScene ciScene(String name) {
        ApiScene scene = new ApiScene();
        scene.setId(UUID.randomUUID());
        scene.setProjectId(PROJECT_ID);
        scene.setName(name);
        scene.setStatus("published");
        // gitlab_ci 场景 steps 恒为空（BR-1.10），不参与平台组过滤
        scene.setSteps(List.of());
        scene.setSourceType("gitlab");
        scene.setRepoId(repo.getId());
        scene.setSourceRef("com.example.qa.OrderTest#" + name);
        return scene;
    }

    private MappedResult mappedTop(ApiScene scene) {
        TestSuiteResult sub = new TestSuiteResult(scene.getId().toString(), scene.getName());
        sub.setStatus(io.github.xiaomisum.ryze.TestStatus.passed);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sceneId", scene.getId().toString());
        sub.setMetadata(meta);
        TestSuiteResult top = new TestSuiteResult("混合任务");
        top.addChild(sub);
        return RyzeResultMapper.map(top, new ApiTestProperties().getDebug().getMaxResponseBodyChars());
    }

    private SceneExecutionService.SceneDatasetSnapshot successSnapshot(ApiScene scene) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", 1);
        summary.put("passed", 1);
        summary.put("failed", 0);
        summary.put("skipped", 0);
        summary.put("durationMs", 12L);
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("sceneId", scene.getId().toString());
        dataset.put("sceneName", scene.getName());
        dataset.put("status", "success");
        dataset.put("summary", summary);
        dataset.put("steps", List.of());
        return new SceneExecutionService.SceneDatasetSnapshot(dataset, "success", 1, 0, 0, 12L);
    }

    private static void inject(Object target, String fieldName, Object value) {
        try {
            var f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
