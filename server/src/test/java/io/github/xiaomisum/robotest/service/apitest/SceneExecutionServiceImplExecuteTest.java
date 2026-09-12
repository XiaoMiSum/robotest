package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneExecuteReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.result.ExtractorResult;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;
import org.apache.hc.core5.http.message.BasicHeader;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 场景手动执行：source 落库默认 scene，显式传入（定时任务/流水线）按传入值（场景侧约定 3.6.1、基础设施 2.1.3） */
@ExtendWith(MockitoExtension.class)
class SceneExecutionServiceImplExecuteTest {

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
    private ThreadPoolTaskExecutor apiTestExecutor;

    private SceneExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SceneExecutionServiceImpl();
        inject("projectAccessGuard", projectAccessGuard);
        inject("sceneMapper", sceneMapper);
        inject("executionRecordMapper", executionRecordMapper);
        inject("reportMapper", reportMapper);
        inject("functionRuntime", functionRuntime);
        inject("apiTestExecutor", apiTestExecutor);
        inject("properties", new ApiTestProperties());
    }

    @Test
    void executeDefaultsRecordSourceToSceneForScenePageRun() {
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(sceneWithSteps());
        doNothing().when(projectAccessGuard).requireProjectMember(PROJECT_ID, WORKSPACE_ID, USER_ID);

        service.execute(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, new ApiSceneExecuteReqDTO());

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

        service.execute(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, req);

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
                () -> service.execute(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, new ApiSceneExecuteReqDTO()));
    }

    @Test
    void engineTimeoutWritesFailedReportWithPerStepFallback() throws Exception {
        // 引擎侧超时（submit 的 Future.get 抛 TimeoutException）时套件无子结果：
        // 步骤不得被静默丢弃，须落 error 条目并附引擎错误信息，报告不得写成"空成功"
        // 永不完成的 Future：get(timeout) 必然超时，模拟引擎侧挂起直到守卫超时
        CompletableFuture<io.github.xiaomisum.ryze.Result> timeoutFuture = new CompletableFuture<>();
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
        io.github.xiaomisum.ryze.testelement.TestSuiteResult suiteResult =
                new io.github.xiaomisum.ryze.testelement.TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suiteResult.setStatus(io.github.xiaomisum.ryze.TestStatus.passed);
        suiteResult.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        suiteResult.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0, 1));
        io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult sample =
                new io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult("步骤一");
        sample.setStatus(io.github.xiaomisum.ryze.TestStatus.passed);
        sample.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        sample.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0, 1));
        suiteResult.addChild(sample);
        io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult second =
                new io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult("步骤二");
        second.setStatus(io.github.xiaomisum.ryze.TestStatus.passed);
        second.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        second.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0, 1));
        suiteResult.addChild(second);
        CompletableFuture<io.github.xiaomisum.ryze.Result> doneFuture =
                CompletableFuture.completedFuture(suiteResult);
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
    void toProcessorEntriesMapsProcessorSampleToReportShape() {
        DefaultSampleResult pre = new DefaultSampleResult("生成令牌");
        pre.setStatus(TestStatus.passed);
        pre.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        pre.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0, 1));
        pre.setResponse(new RealHTTPResponse("{\"token\":\"abc\"}".getBytes(), 200, "HTTP/1.1", "OK",
                new BasicHeader("Content-Type", "application/json")));
        pre.setRequest(SampleResult.DefaultRealRequest.build("POST /v3/auth/token".getBytes()));
        AssertionResult assertion = new AssertionResult();
        assertion.setField("$.code");
        assertion.setRule("==");
        assertion.setExpected(0);
        assertion.setActual(0);
        assertion.setStatus(TestStatus.passed);
        assertion.setMessage("校验通过");
        pre.addAssertion(assertion);
        ExtractorResult extractor = new ExtractorResult();
        extractor.setRefName("token");
        extractor.setField("$.token");
        extractor.setValue("abc");
        pre.addExtractor(extractor);
        io.github.xiaomisum.ryze.testelement.TestSuiteResult top =
                new io.github.xiaomisum.ryze.testelement.TestSuiteResult("环境");
        top.addPreprocessor(pre);

        List<Map<String, Object>> entries = service.toProcessorEntries(top.getPreprocessors());

        // 处理器明细复用步骤元素形状（测试报告详细设计 2.3）：name/status/request/response/assertions/extractors/durationMs
        assertEquals(1, entries.size());
        Map<String, Object> entry = entries.get(0);
        assertEquals("生成令牌", entry.get("name"));
        assertEquals("success", entry.get("status"));
        assertNotNull(entry.get("request"));
        assertEquals(1000L, entry.get("durationMs"));
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) entry.get("response");
        assertEquals(200, response.get("status"));
        assertEquals(1, ((List<?>) entry.get("assertions")).size());
        assertEquals(1, ((List<?>) entry.get("extractors")).size());
        @SuppressWarnings("unchecked")
        Map<String, Object> extractorSnapshot =
                (Map<String, Object>) ((List<Map<String, Object>>) entry.get("extractors")).get(0);
        assertEquals("token", extractorSnapshot.get("refName"));
        assertEquals("abc", extractorSnapshot.get("value"));
    }

    @Test
    void buildSceneDatasetToleratesNodesWithoutStartAndEndTimes() {
        // 引擎异常/中止产生的节点可能没有起止时间：不得抛 Duration.between NPE，耗时落 null
        io.github.xiaomisum.ryze.testelement.TestSuiteResult sub =
                new io.github.xiaomisum.ryze.testelement.TestSuiteResult(SCENE_ID.toString(), "登录链路");
        sub.setStatus(TestStatus.passed);
        DefaultSampleResult sample = new DefaultSampleResult("步骤一");
        sample.setStatus(TestStatus.passed);
        sub.addChild(sample);
        DefaultSampleResult pre = new DefaultSampleResult("生成令牌");
        pre.setStatus(TestStatus.passed);
        sub.addPreprocessor(pre);
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        scene.setName("登录链路");
        scene.setSteps(List.of(Map.of("id", "s1", "name", "步骤一", "enabled", true,
                "requestConfig", Map.of("method", "GET", "url", "http://localhost:1/a"))));

        SceneExecutionService.SceneDatasetSnapshot snapshot = service.buildSceneDataset(
                scene, DebugRyzeConverter.EnvSnapshot.empty(), sub,
                java.time.LocalDateTime.of(2026, 9, 10, 10, 0));

        List<Map<String, Object>> steps = (List<Map<String, Object>>) snapshot.dataset().get("steps");
        assertEquals(1, steps.size());
        assertNull(steps.get(0).get("durationMs"));
        assertNull(steps.get(0).get("errorMessage"));
        List<Map<String, Object>> preprocessors = (List<Map<String, Object>>)
                snapshot.dataset().get("preprocessors");
        assertEquals(1, preprocessors.size());
        assertNull(preprocessors.get(0).get("durationMs"));
    }

    @Test
    void assertionFailureStepMapsToFailedAndPartialReport() throws Exception {
        // 断言失败：采样器 broken + AssertionError + 失败断言记录 → 步骤映射 failed，报告 partial
        io.github.xiaomisum.ryze.testelement.TestSuiteResult suiteResult =
                new io.github.xiaomisum.ryze.testelement.TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suiteResult.setStatus(io.github.xiaomisum.ryze.TestStatus.failed);
        suiteResult.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        suiteResult.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 1));
        io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult sample =
                new io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult("步骤一");
        sample.setStatus(io.github.xiaomisum.ryze.TestStatus.broken);
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
        doReturn(CompletableFuture.completedFuture(suiteResult))
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
        io.github.xiaomisum.ryze.testelement.TestSuiteResult suiteResult =
                new io.github.xiaomisum.ryze.testelement.TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suiteResult.setStatus(io.github.xiaomisum.ryze.TestStatus.broken);
        suiteResult.setStartTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 0));
        suiteResult.setEndTime(java.time.LocalDateTime.of(2026, 9, 10, 10, 1));
        io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult sample =
                new io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult("步骤一");
        sample.setStatus(io.github.xiaomisum.ryze.TestStatus.broken);
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
        doReturn(CompletableFuture.completedFuture(suiteResult))
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

    @Test
    void buildSceneDatasetMapsProcessorSkipped() {
        // 处理器条件不满足置 skipped：处理器条目保持 skipped，不归 error
        io.github.xiaomisum.ryze.testelement.TestSuiteResult sub =
                new io.github.xiaomisum.ryze.testelement.TestSuiteResult(SCENE_ID.toString(), "登录链路");
        sub.setStatus(TestStatus.passed);
        DefaultSampleResult post = new DefaultSampleResult("后置校验");
        post.setStatus(TestStatus.skipped);
        sub.addPostprocessor(post);
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        scene.setName("登录链路");
        scene.setSteps(List.of(Map.of("id", "s1", "name", "步骤一", "enabled", true,
                "requestConfig", Map.of("method", "GET", "url", "http://localhost:1/a"))));

        SceneExecutionService.SceneDatasetSnapshot snapshot = service.buildSceneDataset(
                scene, DebugRyzeConverter.EnvSnapshot.empty(), sub,
                java.time.LocalDateTime.of(2026, 9, 10, 10, 0));

        List<Map<String, Object>> postprocessors = (List<Map<String, Object>>)
                snapshot.dataset().get("postprocessors");
        assertEquals(1, postprocessors.size());
        assertEquals("skipped", postprocessors.get(0).get("status"));
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
                "io.github.xiaomisum.robotest.service.apitest.SceneExecutionServiceImpl$RunContext");
        Constructor<?> ctor = ctxClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object ctx = ctor.newInstance(record, scene, steps, List.of(), DebugRyzeConverter.EnvSnapshot.empty(),
                List.of());
        Method doRun = service.getClass().getDeclaredMethod("doRun", ctxClass, AtomicBoolean.class);
        doRun.setAccessible(true);
        doRun.invoke(service, ctx, new AtomicBoolean(false));
    }

    private ApiScene sceneWithSteps() {
        ApiScene scene = new ApiScene();
        scene.setProjectId(PROJECT_ID);
        scene.setSteps(List.of(Map.of("id", "s1", "name", "登录", "enabled", true)));
        return scene;
    }

    private void inject(String field, Object value) {
        try {
            var f = service.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(service, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}