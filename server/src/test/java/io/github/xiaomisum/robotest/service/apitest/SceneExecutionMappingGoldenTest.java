package io.github.xiaomisum.robotest.service.apitest;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;

import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.DebugRyzeConverter;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultSnapshotConverter;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.result.ExtractorResult;
import io.github.xiaomisum.ryze.result.VariableRecord;
import io.github.xiaomisum.ryze.support.ExceptionGroup;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.util.JsonUtils;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 执行映射黄金文件：固定场景冻结 Result→报告/快照 JSON 字节（04 重构方案 §4.1 步骤 1）。
 * <p>
 * 仅覆盖纯映射面（buildSceneDataset / toProcessorEntries / toSnapshot），不触异步编排状态机，
 * 入参全部固定可复现（固定 UUID/时间），输出字节稳定。切片 2 将映射迁入 adapters/ryze 后，
 * 本测试原样保留，黄金文件 diff 为 ∅ 即证明拆分未改变语义。
 * 更新/创建方式：mvn -Dgolden.update=true test -Dtest=SceneExecutionMappingGoldenTest
 */
class SceneExecutionMappingGoldenTest {

    private static final String GOLDEN_DIR = "golden/apitest";
    private static final UUID SCENE_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 10, 10, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 10, 10, 0, 1);

    @Test
    void successSceneDatasetGolden() {
        TestSuiteResult suite = suite(TestStatus.passed);
        suite.addChild(httpSample("步骤一", TestStatus.passed, 200, "{\"code\":0,\"token\":\"abc\"}"));
        suite.addChild(httpSample("步骤二", TestStatus.passed, 200, "{\"code\":0}"));

        assertGolden("001-success-dataset", datasetOf(suite));
    }

    @Test
    void assertionFailureSceneDatasetGolden() {
        AssertionResult assertion = new AssertionResult();
        assertion.setField("$.code");
        assertion.setRule("==");
        assertion.setExpected(0);
        assertion.setActual(1);
        assertion.setStatus(TestStatus.failed);
        assertion.setMessage("期望 0 实际 1");
        DefaultSampleResult sample = sample("步骤一", TestStatus.broken);
        sample.setThrowable(new AssertionError("期望 0 实际 1"));
        sample.addAssertion(assertion);

        assertGolden("002-assertion-failure-dataset", datasetOf(suite(TestStatus.failed, sample)));
    }

    @Test
    void extractorExceptionSceneDatasetGolden() {
        ExtractorResult extractor = new ExtractorResult();
        extractor.setRefName("token");
        extractor.setField("$.token");
        extractor.setMessage("未提取到数据且无默认值，表达式: $.token");
        DefaultSampleResult sample = sample("步骤一", TestStatus.broken);
        sample.setThrowable(new ExceptionGroup("提取器执行失败", List.of(
                new IllegalArgumentException("未提取到数据且无默认值，表达式: $.token"))));
        sample.addExtractor(extractor);

        assertGolden("003-extractor-exception-dataset", datasetOf(suite(TestStatus.broken, sample)));
    }

    @Test
    void stopOnFailureSkipsRestGolden() {
        DefaultSampleResult first = sample("步骤一", TestStatus.broken);
        DefaultSampleResult pre = sample("生成令牌", TestStatus.skipped);
        TestSuiteResult suite = suite(TestStatus.broken, first);
        suite.addPreprocessor(pre);

        assertGolden("004-stop-on-failure-dataset", datasetOf(suite));
    }

    @Test
    void engineHaltWithoutChildrenGolden() {
        TestSuiteResult suite = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suite.setStatus(TestStatus.broken);
        suite.setThrowable(new RuntimeException("连接被拒绝"));
        suite.setStartTime(T0);
        suite.setEndTime(T1);

        assertGolden("005-engine-halt-dataset", datasetOf(suite));
    }

    @Test
    void fullSnapshotTreeGolden() {
        DefaultSampleResult sample = new DefaultSampleResult("登录接口");
        sample.setStatus(TestStatus.failed);
        sample.setSampleStartTime(T0);
        sample.setSampleEndTime(T1);
        sample.setResponse(new RealHTTPResponse("{\"code\":1}".getBytes(), 502, "HTTP/1.1", "Bad Gateway",
                new BasicHeader("Content-Type", "application/json")));
        sample.setRequest(SampleResult.DefaultRealRequest.build("POST /api/login".getBytes()));
        AssertionResult assertion = new AssertionResult();
        assertion.setField("$.code");
        assertion.setRule("==");
        assertion.setExpected(0);
        assertion.setActual(1);
        assertion.setStatus(TestStatus.failed);
        assertion.setMessage("期望 0 实际 1");
        sample.addAssertion(assertion);
        ExtractorResult extractor = new ExtractorResult();
        extractor.setRefName("token");
        extractor.setField("$.token");
        extractor.setValue("abc");
        sample.addExtractor(extractor);
        sample.setVariables(List.of(new VariableRecord("token", null, "abc")));

        TestSuiteResult suite = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suite.setStatus(TestStatus.failed);
        suite.setStartTime(T0);
        suite.setEndTime(T1);
        suite.addChild(sample);

        assertGolden("006-full-snapshot-tree", RyzeResultSnapshotConverter.toSnapshot(suite));
    }

    @Test
    void processorEntriesGolden() {
        DefaultSampleResult pre = new DefaultSampleResult("生成令牌");
        pre.setStatus(TestStatus.passed);
        pre.setStartTime(T0);
        pre.setEndTime(T1);
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
        DefaultSampleResult post = new DefaultSampleResult("后置校验");
        post.setStatus(TestStatus.skipped);
        post.setStartTime(T0);
        post.setEndTime(T1);

        List<Map<String, Object>> entries = service().toProcessorEntries(List.of(pre, post));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entries", entries);

        assertGolden("007-processor-entries", payload);
    }

    // ---------- 构造 ----------

    private DefaultSampleResult httpSample(String title, TestStatus status, int code, String body) {
        DefaultSampleResult sample = sample(title, status);
        sample.setResponse(new RealHTTPResponse(body.getBytes(), code, "HTTP/1.1", "OK",
                new BasicHeader("Content-Type", "application/json")));
        sample.setRequest(SampleResult.DefaultRealRequest.build("GET /a?b=c HTTP/1.1".getBytes()));
        return sample;
    }

    /** start/end 时间对齐 T0/T1 */
    private DefaultSampleResult sample(String title, TestStatus status) {
        DefaultSampleResult sample = new DefaultSampleResult(title);
        sample.setStatus(status);
        sample.setStartTime(T0);
        sample.setEndTime(T1);
        return sample;
    }

    private TestSuiteResult suite(TestStatus status, DefaultSampleResult... samples) {
        TestSuiteResult suite = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suite.setStatus(status);
        suite.setStartTime(T0);
        suite.setEndTime(T1);
        for (DefaultSampleResult sample : samples) {
            suite.addChild(sample);
        }
        return suite;
    }

    private Map<String, Object> datasetOf(TestSuiteResult suite) {
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        scene.setName("登录链路");
        scene.setSteps(sceneSteps(2));
        return service().buildSceneDataset(scene, EnvSnapshot.empty(), suite, T0)
                .dataset();
    }

    private List<Map<String, Object>> sceneSteps(int count) {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("id", "s" + i);
            step.put("name", i == 1 ? "步骤一" : "步骤二");
            step.put("enabled", true);
            step.put("requestConfig", Map.of("method", "GET", "url", "http://localhost:1/a"));
            steps.add(step);
        }
        return steps;
    }

    // ---------- 断言与更新 ----------

    private void assertGolden(String name, Object payload) {
        String json = JsonUtils.toJsonString(payload);
        Path golden = Path.of("src/test/resources", GOLDEN_DIR, name + ".json");
        if (Boolean.getBoolean("golden.update")) {
            try {
                Files.createDirectories(golden.getParent());
                Files.writeString(golden, json);
                return;
            } catch (Exception ex) {
                throw new RuntimeException("golden 写入失败: " + golden, ex);
            }
        }
        String expected = readGolden(name);
        assertEquals(expected, json, "黄金文件不一致: " + name);
    }

    private String readGolden(String name) {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream(GOLDEN_DIR + "/" + name + ".json")) {
            if (in == null) {
                fail("黄金文件缺失，先用 -Dgolden.update=true 生成: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException("读取黄金文件失败: " + name, ex);
        }
    }

    private static SceneExecutionServiceImpl service() {
        SceneExecutionServiceImpl service = new SceneExecutionServiceImpl();
        inject(service, "properties", new ApiTestProperties());
        return service;
    }

    private static void inject(SceneExecutionServiceImpl service, String field, Object value) {
        try {
            Field f = SceneExecutionServiceImpl.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(service, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}