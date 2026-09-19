package io.github.xiaomisum.robotest.service.apitest.execution;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.result.ExtractorResult;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 场景数据集/处理器明细纯映射：仅覆盖 buildSceneDataset / toProcessorEntries（黄金面不变式之外的行为边界） */
class SceneDatasetBuilderTest {

    private static final UUID SCENE_ID = UUID.randomUUID();

    private SceneDatasetBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SceneDatasetBuilder();
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
        TestSuiteResult top = new TestSuiteResult("环境");
        top.addPreprocessor(pre);

        List<Map<String, Object>> entries = builder.toProcessorEntries(
                top.getPreprocessors().stream().map(node -> RyzeResultMapper.map(node, 0)).toList());

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
        TestSuiteResult sub = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
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

        SceneExecutionService.SceneDatasetSnapshot snapshot = builder.buildSceneDataset(
                scene, EnvSnapshot.empty(), RyzeResultMapper.map(sub, 0),
                LocalDateTime.of(2026, 9, 10, 10, 0));

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
    void buildSceneDatasetMapsProcessorSkipped() {
        // 处理器条件不满足置 skipped：处理器条目保持 skipped，不归 error
        TestSuiteResult sub = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        sub.setStatus(TestStatus.passed);
        DefaultSampleResult post = new DefaultSampleResult("后置校验");
        post.setStatus(TestStatus.skipped);
        sub.addPostprocessor(post);
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        scene.setName("登录链路");
        scene.setSteps(List.of(Map.of("id", "s1", "name", "步骤一", "enabled", true,
                "requestConfig", Map.of("method", "GET", "url", "http://localhost:1/a"))));

        SceneExecutionService.SceneDatasetSnapshot snapshot = builder.buildSceneDataset(
                scene, EnvSnapshot.empty(), RyzeResultMapper.map(sub, 0),
                LocalDateTime.of(2026, 9, 10, 10, 0));

        List<Map<String, Object>> postprocessors = (List<Map<String, Object>>)
                snapshot.dataset().get("postprocessors");
        assertEquals(1, postprocessors.size());
        assertEquals("skipped", postprocessors.get(0).get("status"));
    }
}