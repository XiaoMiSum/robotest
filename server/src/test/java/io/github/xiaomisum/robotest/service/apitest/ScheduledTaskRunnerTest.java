package io.github.xiaomisum.robotest.service.apitest;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvironmentSnapshotProvider;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;

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
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.service.apitest.execution.SceneExecutionService;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.SceneSuiteBuilder;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.SuiteBuilder;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 定时任务执行留痕：测试计划单一大 Suite 执行聚合、接口同步、失败跳过与异常边界（定时任务详细设计 4.1/4.3/4.4） */
@ExtendWith(MockitoExtension.class)
class ScheduledTaskRunnerTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SCENE_ID = UUID.randomUUID();
    private static final UUID ENV_ID = UUID.randomUUID();
    private static final String OPENAPI_URL = "https://example.com/v3/api-docs";

    @Mock
    private SceneExecutionService sceneExecutionService;
    @Mock
    private EnvironmentSnapshotProvider environmentSnapshotFactory;
    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiScheduledTaskMapper taskMapper;
    @Mock
    private ApiScheduledTaskExecutionMapper executionMapper;
    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiReportMapper reportMapper;
    @Mock
    private ProjectModuleMapper moduleMapper;

    private TestPlanTaskHandler testPlanHandler;
    private ApiSyncTaskHandler apiSyncHandler;
    private ScheduledTaskRunner runner;

    @BeforeEach
    void setUp() {
        testPlanHandler = new TestPlanTaskHandler();
        inject(testPlanHandler, "sceneExecutionService", sceneExecutionService);
        inject(testPlanHandler, "environmentSnapshotFactory", environmentSnapshotFactory);
        inject(testPlanHandler, "sceneMapper", sceneMapper);
        inject(testPlanHandler, "moduleMapper", moduleMapper);
        inject(testPlanHandler, "taskMapper", taskMapper);
        inject(testPlanHandler, "executionMapper", executionMapper);
        inject(testPlanHandler, "executionRecordMapper", executionRecordMapper);
        inject(testPlanHandler, "reportMapper", reportMapper);
        inject(testPlanHandler, "suiteBuilder", new SceneSuiteBuilder());

        apiSyncHandler = new ApiSyncTaskHandler();
        inject(apiSyncHandler, "taskMapper", taskMapper);
        inject(apiSyncHandler, "executionMapper", executionMapper);

        runner = new ScheduledTaskRunner();
        inject(runner, "testPlanHandler", testPlanHandler);
        inject(runner, "apiSyncHandler", apiSyncHandler);
        inject(runner, "taskMapper", taskMapper);
        inject(runner, "executionMapper", executionMapper);
    }

    @Test
    void writeSkippedRecordsSkippedStatusWithoutTouchingLastExecution() {
        runner.writeSkipped(syncTask(), "scheduled");

        ArgumentCaptor<ApiScheduledTaskExecution> captor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(captor.capture());
        assertEquals("skipped", captor.getValue().getStatus());
        assertEquals("scheduled", captor.getValue().getTriggerType());
        verify(taskMapper, never()).updateById(any(ApiScheduledTask.class));
    }

    @Test
    void runSyncRethrowRecordsFailureThenRethrows() {
        ApiScheduledTask task = syncTask();
        inject(apiSyncHandler, "apiInterfaceImportService",
                mockImportService(new ServiceException(1000017012, "HTTP 500")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> runner.runSyncRethrow(task, UUID.randomUUID(), "manual"));
        assertEquals(1000017012, ex.getCode().intValue());

        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper, times(2)).updateById(taskCaptor.capture());
        assertEquals("running", taskCaptor.getAllValues().get(0).getLastExecutionStatus());
        assertEquals("failed", taskCaptor.getAllValues().get(1).getLastExecutionStatus());

        ArgumentCaptor<ApiScheduledTaskExecution> captor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("HTTP 500", captor.getValue().getErrorMessage());
    }

    @Test
    void runTaskSwallowsSyncFailureAndMarksLastExecution() {
        inject(apiSyncHandler, "apiInterfaceImportService",
                mockImportService(new ServiceException(1000017601, "文档不可达")));

        assertDoesNotThrow(() -> runner.runTask(syncTask(), "scheduled"));

        ArgumentCaptor<ApiScheduledTaskExecution> captor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertTrue(captor.getValue().getErrorMessage().contains("文档不可达"));

        ArgumentCaptor<ApiScheduledTask> taskCaptor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper, times(2)).updateById(taskCaptor.capture());
        assertEquals("running", taskCaptor.getAllValues().get(0).getLastExecutionStatus());
        assertEquals("failed", taskCaptor.getAllValues().get(1).getLastExecutionStatus());
        assertNull(taskCaptor.getAllValues().get(1).getEnabled());
    }

    @Test
    void runTestPlanAggregatesBigSuiteSuccess() throws Exception {
        ApiScheduledTask task = testPlanTask("all", null);
        ApiScene scene = sceneWithSteps(SCENE_ID, 2);
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(scene));
        when(environmentSnapshotFactory.resolve(PROJECT_ID, ENV_ID))
                .thenReturn(EnvSnapshot.empty());
        when(sceneExecutionService.startSuite(any(), eq(PROJECT_ID)))
                .thenReturn(mappedTop(topSuiteResult(sceneSubSuite())));
        when(sceneExecutionService.buildSceneDataset(eq(scene), any(), any(), any()))
                .thenReturn(snapshot("success", 2, 0));

        runner.runTask(task, "scheduled");

        verify(sceneExecutionService).startSuite(any(), eq(PROJECT_ID));
        verify(sceneExecutionService, never()).execute(any(), any(), any(), any(), any());

        ArgumentCaptor<ApiReport> suiteCaptor = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(suiteCaptor.capture());
        ApiReport suite = suiteCaptor.getValue();
        assertEquals("suite", suite.getReportType());
        assertEquals("success", suite.getStatus());
        assertEquals(TASK_ID, suite.getExternalId());
        assertTrue(suite.getName().startsWith("冒烟任务"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dataset = (Map<String, Object>) suite.getResult();
        assertEquals(1, ((List<?>) dataset.get("scenes")).size());
        assertEquals(List.of(), dataset.get("preprocessors"));
        assertEquals(List.of(), dataset.get("postprocessors"));

        assertNotNull(suite.getRyzeSnapshot());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snapshotChildren = (List<Map<String, Object>>) suite.getRyzeSnapshot().get("children");
        assertEquals(1, snapshotChildren.size());
        assertEquals(SCENE_ID.toString(), snapshotChildren.get(0).get("id"));

        ArgumentCaptor<ApiExecutionRecord> recCaptor = ArgumentCaptor.forClass(ApiExecutionRecord.class);
        verify(executionRecordMapper).insert(recCaptor.capture());
        assertEquals(SCENE_ID, recCaptor.getValue().getSceneId());
        assertEquals(suite.getId(), recCaptor.getValue().getReportId());
        assertEquals("success", recCaptor.getValue().getStatus());
        assertEquals("schedule", recCaptor.getValue().getSource());
        assertEquals("scheduled", recCaptor.getValue().getTriggerType());

        ArgumentCaptor<ApiScheduledTaskExecution> taskRecCaptor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(taskRecCaptor.capture());
        assertEquals("success", taskRecCaptor.getValue().getStatus());
        assertEquals(suite.getId(), taskRecCaptor.getValue().getReportId());

        AssertionCaptor taskCaptor = AssertionCaptor.capture(taskMapper);
        assertEquals("success", taskCaptor.last().getLastExecutionStatus());
    }

    @Test
    void runTestPlanMarksFailedOnSceneFailure() throws Exception {
        ApiScheduledTask task = testPlanTask("all", null);
        ApiScene scene = sceneWithSteps(SCENE_ID, 1);
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(scene));
        when(environmentSnapshotFactory.resolve(PROJECT_ID, ENV_ID))
                .thenReturn(EnvSnapshot.empty());
        when(sceneExecutionService.startSuite(any(), eq(PROJECT_ID)))
                .thenReturn(mappedTop(topSuiteResult(sceneSubSuite())));
        when(sceneExecutionService.buildSceneDataset(eq(scene), any(), any(), any()))
                .thenReturn(snapshot("failed", 1, 1));

        runner.runTask(task, "scheduled");

        ArgumentCaptor<ApiReport> suiteCaptor = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(suiteCaptor.capture());
        assertEquals("failed", suiteCaptor.getValue().getStatus());

        ArgumentCaptor<ApiExecutionRecord> recCaptor = ArgumentCaptor.forClass(ApiExecutionRecord.class);
        verify(executionRecordMapper).insert(recCaptor.capture());
        assertEquals("failed", recCaptor.getValue().getStatus());

        AssertionCaptor taskCaptor = AssertionCaptor.capture(taskMapper);
        assertEquals("failed", taskCaptor.last().getLastExecutionStatus());
    }

    @Test
    void runTestPlanTopLevelThrowableWritesSingleFailedWithoutReport() throws Exception {
        ApiScheduledTask task = testPlanTask("all", null);
        ApiScene scene = sceneWithSteps(SCENE_ID, 1);
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(scene));
        when(environmentSnapshotFactory.resolve(PROJECT_ID, ENV_ID))
                .thenReturn(EnvSnapshot.empty());
        TestSuiteResult top = topSuiteResult(sceneSubSuite());
        top.setThrowable(new RuntimeException("数据库连接池耗尽"));
        when(sceneExecutionService.startSuite(any(), eq(PROJECT_ID))).thenReturn(mappedTop(top));

        runner.runTask(task, "scheduled");

        verify(reportMapper, never()).insert(any(ApiReport.class));
        verify(executionRecordMapper, never()).insert(any(ApiExecutionRecord.class));
        ArgumentCaptor<ApiScheduledTaskExecution> captor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertNull(captor.getValue().getReportId());
        assertTrue(captor.getValue().getErrorMessage().contains("数据库连接池耗尽"));
        AssertionCaptor taskCaptor = AssertionCaptor.capture(taskMapper);
        assertEquals("failed", taskCaptor.last().getLastExecutionStatus());
    }

    @Test
    void runTestPlanSkipsSceneWithoutEnabledSteps() throws Exception {
        ApiScheduledTask task = testPlanTask("all", null);
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(sceneWithSteps(SCENE_ID, 0)));

        runner.runTask(task, "scheduled");

        verify(sceneExecutionService, never()).startSuite(any(), any());
        ArgumentCaptor<ApiScheduledTaskExecution> recordCaptor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(recordCaptor.capture());
        assertEquals("skipped", recordCaptor.getValue().getStatus());
        assertTrue(recordCaptor.getValue().getErrorMessage().contains("不可执行"));
        AssertionCaptor taskCaptor = AssertionCaptor.capture(taskMapper);
        assertEquals("failed", taskCaptor.last().getLastExecutionStatus());
    }

    @Test
    void runTestPlanSkipsDraftScene() throws Exception {
        ApiScheduledTask task = testPlanTask("all", null);
        ApiScene scene = sceneWithSteps(SCENE_ID, 2);
        scene.setStatus("draft");
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(scene));

        runner.runTask(task, "scheduled");

        verify(sceneExecutionService, never()).startSuite(any(), any());
        ArgumentCaptor<ApiScheduledTaskExecution> recordCaptor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(recordCaptor.capture());
        assertEquals("skipped", recordCaptor.getValue().getStatus());
        assertTrue(recordCaptor.getValue().getErrorMessage().contains("草稿"));
        AssertionCaptor taskCaptor = AssertionCaptor.capture(taskMapper);
        assertEquals("failed", taskCaptor.last().getLastExecutionStatus());
    }

    @Test
    void runTestPlanEmptyScopeWritesSkippedAndFailed() throws Exception {
        ApiScheduledTask task = testPlanTask("all", null);
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of());

        runner.runTask(task, "scheduled");

        verify(sceneExecutionService, never()).startSuite(any(), any());
        ArgumentCaptor<ApiScheduledTaskExecution> recordCaptor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(recordCaptor.capture());
        assertEquals("skipped", recordCaptor.getValue().getStatus());
        assertTrue(recordCaptor.getValue().getErrorMessage().contains("未圈选到可执行场景"));
    }

    @Test
    void runTestPlanMountsEnvAtTopLevelAndSceneOnlyVariablesInSubSuite() throws Exception {
        ApiScheduledTask task = testPlanTask("all", null);
        List<Map<String, Object>> sceneVars = List.of(Map.of("name", "sceneVar", "value", "s1"));
        ApiScene scene = sceneWithSteps(SCENE_ID, 1);
        scene.setVariables(sceneVars);
        when(sceneMapper.listByProject(PROJECT_ID)).thenReturn(List.of(scene));
        when(environmentSnapshotFactory.resolve(PROJECT_ID, ENV_ID)).thenReturn(envWithContent());
        when(sceneExecutionService.startSuite(any(), eq(PROJECT_ID)))
                .thenReturn(mappedTop(topSuiteResult(sceneSubSuite())));
        when(sceneExecutionService.buildSceneDataset(eq(scene), any(), any(), any()))
                .thenReturn(snapshot("success", 1, 0));

        runner.runTask(task, "scheduled");

        ArgumentCaptor<Map<String, Object>> suiteCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(sceneExecutionService).startSuite(suiteCaptor.capture(), eq(PROJECT_ID));
        Map<String, Object> root = suiteCaptor.getValue();

        Map<?, ?> rootVars = (Map<?, ?>) root.get("variables");
        assertEquals("env-value", rootVars.get("envVar"));
        assertEquals(1, ((List<?>) root.get("preprocessors")).size());
        assertEquals(1, ((List<?>) root.get("postprocessors")).size());
        assertEquals(1, ((List<?>) root.get("configelements")).size());
        assertEquals(TASK_ID.toString(), root.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> child = (Map<String, Object>) ((List<Map<String, Object>>) root.get("children")).get(0);
        Map<?, ?> childVars = (Map<?, ?>) child.get("variables");
        assertEquals("s1", childVars.get("sceneVar"));
        assertFalse(childVars.containsKey("envVar"));
        assertEquals(SCENE_ID.toString(), child.get("id"));
    }

    private ApiInterfaceImportService mockImportService(Exception ex) {
        ApiInterfaceImportService svc = org.mockito.Mockito.mock(ApiInterfaceImportService.class);
        when(svc.importUrl(eq(PROJECT_ID), any(UUID.class), eq(OPENAPI_URL), any())).thenThrow(ex);
        return svc;
    }

    private EnvSnapshot envWithContent() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("envVar", "env-value");
        Map<String, Object> pre = Map.of(
                "testclass", "http",
                "config", Map.of("path", "/pre"),
                "enabled", true,
                "sortOrder", 1,
                "extractors", List.of(Map.of(
                        "source", "json_field", "expression", "$.code",
                        "variableName", "_pre_1", "enabled", true, "description", "")));
        Map<String, Object> post = Map.of(
                "testclass", "http",
                "config", Map.of("path", "/post"),
                "enabled", true,
                "sortOrder", 2);
        List<Map<String, Object>> httpConfigs = List.of(
                Map.of("name", "默认", "refName", "default-http", "baseUrl", "http://env.example.com", "isDefault", true));
        return new EnvSnapshot(
                "测试环境", variables, List.of(pre), List.of(post), httpConfigs, List.of());
    }

    private ApiScheduledTask syncTask() {
        ApiScheduledTask task = new ApiScheduledTask();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setTaskType("import_swagger");
        task.setOpenapiUrl(OPENAPI_URL);
        task.setEnabled(true);
        return task;
    }

    private ApiScheduledTask testPlanTask(String scope, List<UUID> sceneIds) {
        ApiScheduledTask task = new ApiScheduledTask();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setTaskType("scene_execute");
        task.setName("冒烟任务");
        task.setExecutionScope(scope);
        task.setSceneIds(sceneIds);
        task.setEnvironmentId(ENV_ID);
        task.setEnabled(true);
        return task;
    }

    private ApiScene sceneWithSteps(UUID sceneId, int enabledSteps) {
        ApiScene scene = new ApiScene();
        scene.setId(sceneId);
        scene.setProjectId(PROJECT_ID);
        scene.setStatus("published");
        ArrayList<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < enabledSteps; i++) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("id", UUID.randomUUID());
            step.put("enabled", true);
            step.put("name", "步骤" + (i + 1));
            step.put("requestConfig", Map.of("url", "https://example.com"));
            step.put("validators", List.of());
            step.put("extractors", List.of());
            step.put("variables", List.of());
            steps.add(step);
        }
        scene.setSteps(steps);
        return scene;
    }

    private TestSuiteResult topSuiteResult(TestSuiteResult sceneSubSuite) {
        TestSuiteResult top = new TestSuiteResult("冒烟任务");
        top.addChild(sceneSubSuite);
        return top;
    }

    private MappedResult mappedTop(TestSuiteResult top) {
        return RyzeResultMapper.map(top, new ApiTestProperties().getDebug().getMaxResponseBodyChars());
    }

    private TestSuiteResult sceneSubSuite() {
        TestSuiteResult sub = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        sub.setStatus(io.github.xiaomisum.ryze.TestStatus.passed);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sceneId", SCENE_ID.toString());
        sub.setMetadata(meta);
        return sub;
    }

    private SceneExecutionService.SceneDatasetSnapshot snapshot(String status, int passed, int failed) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", passed + failed);
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("skipped", 0);
        summary.put("durationMs", 12L);
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("sceneId", SCENE_ID.toString());
        dataset.put("sceneName", "登录链路");
        dataset.put("status", status);
        dataset.put("summary", summary);
        dataset.put("environmentName", "测试环境");
        dataset.put("executedAt", "2026-08-25T10:00:00");
        dataset.put("steps", List.of(Map.of("stepId", "s1", "name", "登录", "status", "success")));
        return new SceneExecutionService.SceneDatasetSnapshot(dataset, status, passed, failed, 0, 12L);
    }

    private static final class AssertionCaptor {
        private final ArgumentCaptor<ApiScheduledTask> captor;

        private AssertionCaptor(ArgumentCaptor<ApiScheduledTask> captor) {
            this.captor = captor;
        }

        static AssertionCaptor capture(ApiScheduledTaskMapper mapper) {
            ArgumentCaptor<ApiScheduledTask> c = ArgumentCaptor.forClass(ApiScheduledTask.class);
            verify(mapper, times(2)).updateById(c.capture());
            return new AssertionCaptor(c);
        }

        ApiScheduledTask last() {
            return captor.getAllValues().get(captor.getAllValues().size() - 1);
        }
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
