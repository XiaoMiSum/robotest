package io.github.xiaomisum.robotest.service.apitest.execution;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;

import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneExecuteReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.service.apitest.CustomFunctionRuntime;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.SuiteBuilder;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.SuiteRunner;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.result.ExtractorResult;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import xyz.migoo.framework.common.exception.ServiceException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 场景手动执行：source 落库默认 scene，显式传入（定时任务/流水线）按传入值（场景侧约定 3.6.1、基础设施 2.1.3）；doRun 编排状态机 */
@ExtendWith(MockitoExtension.class)
class SceneExecutionLauncherTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SCENE_ID = UUID.randomUUID();

    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiReportMapper reportMapper;
    @Mock
    private CustomFunctionRuntime functionRuntime;
    @Mock
    private SuiteBuilder suiteBuilder;
    @Mock
    private SuiteRunner suiteRunner;
    @Mock
    private ThreadPoolTaskExecutor apiTestExecutor;

    private SceneExecutionLauncher launcher;

    @BeforeEach
    void setUp() {
        launcher = new SceneExecutionLauncher();
        inject("projectAccessGuard", projectAccessGuard);
        inject("sceneMapper", sceneMapper);
        inject("executionRecordMapper", executionRecordMapper);
        inject("reportMapper", reportMapper);
        inject("functionRuntime", functionRuntime);
        inject("apiTestExecutor", apiTestExecutor);
        inject("suiteRunner", suiteRunner);
        inject("suiteBuilder", suiteBuilder);
        inject("properties", new ApiTestProperties());
    }

    @Test
    void executeDefaultsRecordSourceToSceneForScenePageRun() {
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(sceneWithSteps());
        doNothing().when(projectAccessGuard).requireProjectMember(PROJECT_ID, WORKSPACE_ID, USER_ID);

        launcher.execute(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, new ApiSceneExecuteReqDTO());

        ArgumentCaptor<ApiExecutionRecord> captor = ArgumentCaptor.forClass(ApiExecutionRecord.class);
        verify(executionRecordMapper).insert(captor.capture());
        assertEquals("scene", captor.getValue().getSource());
        // 场景页手动执行不携带 triggerType，落库 manual
        assertEquals("manual", captor.getValue().getTriggerType());
    }

    @Test
    void executePropagatesExplicitSourceForScheduledLaunch() {
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(sceneWithSteps());
        doNothing().when(projectAccessGuard).requireProjectMember(PROJECT_ID, WORKSPACE_ID, USER_ID);
        ApiSceneExecuteReqDTO req = new ApiSceneExecuteReqDTO();
        req.setSource("schedule");
        req.setTriggerType("scheduled");

        launcher.execute(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, req);

        ArgumentCaptor<ApiExecutionRecord> captor = ArgumentCaptor.forClass(ApiExecutionRecord.class);
        verify(executionRecordMapper).insert(captor.capture());
        assertEquals("schedule", captor.getValue().getSource());
        assertEquals("scheduled", captor.getValue().getTriggerType());
    }

    @Test
    void executeRejectsSceneWithoutSteps() {
        ApiScene scene = new ApiScene();
        scene.setProjectId(PROJECT_ID);
        scene.setSteps(null);
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(scene);
        doNothing().when(projectAccessGuard).requireProjectMember(PROJECT_ID, WORKSPACE_ID, USER_ID);

        assertThrows(ServiceException.class,
                () -> launcher.execute(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, new ApiSceneExecuteReqDTO()));
    }

    @Test
    void engineTimeoutWritesFailedReportWithPerStepFallback() throws Exception {
        // 引擎侧超时（submit 的 Future.get 抛 TimeoutException）时套件无子结果：
        // 步骤不得被静默丢弃，须落 error 条目并附引擎错误信息，报告不得写成"空成功"
        // 永不完成的 Future：get(timeout) 必然超时，模拟引擎侧挂起直到守卫超时
        CompletableFuture<MappedResult> timeoutFuture = new CompletableFuture<>();
        doReturn(timeoutFuture).when(apiTestExecutor).submit(any(java.util.concurrent.Callable.class));
        doNothing().when(functionRuntime).prepareSuite(any(), any());
        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(PROJECT_ID);
        record.setExecutionMode("platform");
        record.setSource("scene");
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        scene.setProjectId(PROJECT_ID);
        scene.setName("引擎超时场景");

        invokeDoRun(record, scene, sceneWithTwoEnabledSteps());

        ArgumentCaptor<ApiReport> reportCaptor = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(reportCaptor.capture());
        ApiReport report = reportCaptor.getValue();
        assertEquals("failed", report.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> dataset = (Map<String, Object>) report.getResult();
        List<Map<String, Object>> steps = (List<Map<String, Object>>) dataset.get("steps");
        assertEquals(2, steps.size());
        Map<String, Object> first = steps.get(0);
        Map<String, Object> second = steps.get(1);
        assertEquals("error", first.get("status"));
        assertNotNull(first.get("errorMessage"));
        assertTrue(first.get("errorMessage").toString().contains("超时"));
        // 失败语义固定停止运行：首个漏检步骤 error，后续步骤 skipped
        assertEquals("skipped", second.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) report.getSummary();
        assertEquals(2, summary.get("total"));
        assertEquals(1, summary.get("failed"));
        assertEquals(1, summary.get("skipped"));

        ArgumentCaptor<ApiExecutionRecord> recordCaptor = ArgumentCaptor.forClass(ApiExecutionRecord.class);
        verify(executionRecordMapper).updateById(recordCaptor.capture());
        assertEquals("error", recordCaptor.getValue().getStatus());
        assertTrue(recordCaptor.getValue().getErrorMessage().contains("超时"));
    }

    @Test
    void successExecutionWritesRyzeSnapshotTree() throws Exception {
        // 引擎正常返回结果树时：ryze_snapshot 落完整执行树（测试报告详细设计 2.3.3），
        // 根节点含 id/title，子节点为步骤 SampleResult
        TestSuiteResult suiteResult = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suiteResult.setStatus(TestStatus.passed);
        suiteResult.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        suiteResult.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0, 1));
        DefaultSampleResult sample = new DefaultSampleResult("步骤一");
        sample.setStatus(TestStatus.passed);
        sample.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        sample.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0, 1));
        suiteResult.addChild(sample);
        DefaultSampleResult second = new DefaultSampleResult("步骤二");
        second.setStatus(TestStatus.passed);
        second.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        second.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0, 1));
        suiteResult.addChild(second);
        CompletableFuture<MappedResult> doneFuture =
                CompletableFuture.completedFuture(RyzeResultMapper.map(suiteResult, 0));
        doReturn(doneFuture).when(apiTestExecutor).submit(any(java.util.concurrent.Callable.class));
        doNothing().when(functionRuntime).prepareSuite(any(), any());
        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(PROJECT_ID);
        record.setExecutionMode("platform");
        record.setSource("scene");
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        scene.setProjectId(PROJECT_ID);
        scene.setName("快照场景");

        invokeDoRun(record, scene, sceneWithTwoEnabledSteps());

        ArgumentCaptor<ApiReport> reportCaptor = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(reportCaptor.capture());
        ApiReport report = reportCaptor.getValue();
        assertNotNull(report.getRyzeSnapshot(), "成功执行必须落 ryze_snapshot");
        assertEquals(SCENE_ID.toString(), report.getRyzeSnapshot().get("id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children =
                (List<Map<String, Object>>) report.getRyzeSnapshot().get("children");
        assertEquals(2, children.size());
        assertEquals("步骤一", children.get(0).get("title"));

        // 场景数据集：steps 沿用引擎样本口径（无样本时回退步骤配置），处理器列表固定为数组
        Map<String, Object> dataset = (Map<String, Object>) report.getResult();
        List<Map<String, Object>> steps = (List<Map<String, Object>>) dataset.get("steps");
        assertEquals(2, steps.size());
        Map<String, Object> firstStep = steps.get(0);
        assertEquals("success", firstStep.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> stepRequest = (Map<String, Object>) firstStep.get("request");
        assertEquals("GET", stepRequest.get("method"));
        assertEquals("http://localhost:1/a", stepRequest.get("url"));
        assertEquals(List.of(), dataset.get("preprocessors"));
        assertEquals(List.of(), dataset.get("postprocessors"));
    }

    @Test
    void assertionFailureStepMapsToFailedAndPartialReport() throws Exception {
        // 断言失败：采样器 broken + AssertionError + 失败断言记录 → 步骤映射 failed，报告 partial
        TestSuiteResult suiteResult = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suiteResult.setStatus(TestStatus.failed);
        suiteResult.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        suiteResult.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 1));
        DefaultSampleResult sample = new DefaultSampleResult("步骤一");
        sample.setStatus(TestStatus.broken);
        sample.setThrowable(new AssertionError("期望 0 实际 1"));
        sample.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        sample.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 1));
        AssertionResult assertion = new AssertionResult();
        assertion.setField("$.code");
        assertion.setExpected(0);
        assertion.setActual(1);
        assertion.setStatus(TestStatus.failed);
        assertion.setMessage("期望 0 实际 1");
        sample.addAssertion(assertion);
        suiteResult.addChild(sample);
        doReturn(CompletableFuture.completedFuture(RyzeResultMapper.map(suiteResult, 0)))
                .when(apiTestExecutor).submit(any(java.util.concurrent.Callable.class));
        doNothing().when(functionRuntime).prepareSuite(any(), any());

        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(PROJECT_ID);
        record.setExecutionMode("platform");
        record.setSource("scene");
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        scene.setProjectId(PROJECT_ID);
        scene.setName("断言失败场景");
        scene.setSteps(List.of(Map.of("id", "s1", "name", "步骤一", "enabled", true,
                "requestConfig", Map.of("method", "GET", "url", "http://localhost:1/a"))));

        invokeDoRun(record, scene, scene.getSteps());

        ArgumentCaptor<ApiReport> reportCaptor = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(reportCaptor.capture());
        ApiReport report = reportCaptor.getValue();
        assertEquals("partial", report.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> dataset = (Map<String, Object>) report.getResult();
        List<Map<String, Object>> steps = (List<Map<String, Object>>) dataset.get("steps");
        assertEquals(1, steps.size());
        assertEquals("failed", steps.get(0).get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) report.getSummary();
        assertEquals(1, summary.get("failed"));
    }

    @Test
    void extractorExceptionGroupExpandsMessageInReport() throws Exception {
        // 多个提取器异常聚合为 ExceptionGroup：步骤 error，errorMessage 展开子异常明细
        TestSuiteResult suiteResult = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suiteResult.setStatus(TestStatus.broken);
        suiteResult.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        suiteResult.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 1));
        DefaultSampleResult sample = new DefaultSampleResult("步骤一");
        sample.setStatus(TestStatus.broken);
        sample.setThrowable(new io.github.xiaomisum.ryze.support.ExceptionGroup("提取器执行失败", List.of(
                new IllegalArgumentException("未提取到数据且无默认值，表达式: $.token"))));
        sample.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        sample.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 1));
        ExtractorResult extractor = new ExtractorResult();
        extractor.setRefName("token");
        extractor.setField("$.token");
        extractor.setMessage("未提取到数据且无默认值，表达式: $.token");
        sample.addExtractor(extractor);
        suiteResult.addChild(sample);
        doReturn(CompletableFuture.completedFuture(RyzeResultMapper.map(suiteResult, 0)))
                .when(apiTestExecutor).submit(any(java.util.concurrent.Callable.class));
        doNothing().when(functionRuntime).prepareSuite(any(), any());

        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(PROJECT_ID);
        record.setExecutionMode("platform");
        record.setSource("scene");
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        scene.setProjectId(PROJECT_ID);
        scene.setName("提取器失败场景");
        scene.setSteps(List.of(Map.of("id", "s1", "name", "步骤一", "enabled", true,
                "requestConfig", Map.of("method", "GET", "url", "http://localhost:1/a"))));

        invokeDoRun(record, scene, scene.getSteps());

        ArgumentCaptor<ApiReport> reportCaptor = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).insert(reportCaptor.capture());
        ApiReport report = reportCaptor.getValue();
        assertEquals("failed", report.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> dataset = (Map<String, Object>) report.getResult();
        List<Map<String, Object>> steps = (List<Map<String, Object>>) dataset.get("steps");
        assertEquals(1, steps.size());
        assertEquals("error", steps.get(0).get("status"));
        assertEquals("提取器执行失败：未提取到数据且无默认值，表达式: $.token",
                steps.get(0).get("errorMessage"));
    }

    private List<Map<String, Object>> sceneWithTwoEnabledSteps() {
        return List.of(
                Map.of("id", "s1", "name", "步骤一", "enabled", true,
                        "requestConfig", Map.of("method", "GET", "url", "http://localhost:1/a")),
                Map.of("id", "s2", "name", "步骤二", "enabled", true,
                        "requestConfig", Map.of("method", "POST", "url", "http://localhost:1/b")));
    }

    private void invokeDoRun(ApiExecutionRecord record, ApiScene scene, List<Map<String, Object>> steps)
            throws Exception {
        Class<?> ctxClass = Class.forName(
                "io.github.xiaomisum.robotest.service.apitest.execution.SceneExecutionLauncher$RunContext");
        Constructor<?> ctor = ctxClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object ctx = ctor.newInstance(record, scene, steps, List.of(), EnvSnapshot.empty(),
                List.of());
        Method doRun = launcher.getClass().getDeclaredMethod("doRun", ctxClass, AtomicBoolean.class);
        doRun.setAccessible(true);
        doRun.invoke(launcher, ctx, new AtomicBoolean(false));
    }

    private ApiScene sceneWithSteps() {
        ApiScene scene = new ApiScene();
        scene.setProjectId(PROJECT_ID);
        scene.setSteps(List.of(Map.of("id", "s1", "name", "登录", "enabled", true)));
        return scene;
    }

    private void inject(String field, Object value) {
        try {
            var f = launcher.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(launcher, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}