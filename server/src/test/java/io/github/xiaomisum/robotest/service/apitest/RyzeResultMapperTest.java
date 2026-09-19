package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultSnapshotConverter;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeSnapshotCapture;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.SceneSuiteBuilder;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.SceneRyzeConverter;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.StepSpec;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RyzeResultMapper} 平台投影：suite/sample/失败分支与 treeSnapshot 根挂载、前后置处理器映射。
 */
class RyzeResultMapperTest {

    private static final UUID SCENE_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 10, 10, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 10, 10, 0, 1);
    private static final int MAX_CHARS = 128;

    @Test
    void mapsSuiteProjectingChildrenAndTreeSnapshotOnRoot() {
        TestSuiteResult suite = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suite.setStatus(TestStatus.passed);
        suite.setStartTime(T0);
        suite.setEndTime(T1);
        suite.setMetadata(Map.of("sceneId", SCENE_ID.toString()));
        DefaultSampleResult ok = sample("步骤一", TestStatus.passed);
        ok.setResponse(new RealHTTPResponse("{\"code\":0,\"token\":\"abc\"}".getBytes(), 200, "HTTP/1.1", "OK",
                new BasicHeader("Content-Type", "application/json")));
        ok.setRequest(SampleResult.DefaultRealRequest
                .build("GET /a HTTP/1.1".getBytes()));
        DefaultSampleResult pre = sample("生成令牌", TestStatus.passed);
        DefaultSampleResult post = sample("后置校验", TestStatus.skipped);
        suite.addChild(ok);
        suite.addPreprocessor(pre);
        suite.addPostprocessor(post);

        MappedResult root = RyzeResultMapper.map(suite, MAX_CHARS);

        assertTrue(root.suite());
        assertEquals("success", root.status());
        assertEquals(1, root.children().size());
        assertEquals(1, root.preprocessors().size());
        assertEquals(1, root.postprocessors().size());
        assertNotNull(root.treeSnapshot());
        assertEquals("登录链路", root.treeSnapshot().get("title"));
        assertEquals(SCENE_ID.toString(), root.metadata().get("sceneId"));

        MappedResult child = root.children().get(0);
        assertTrue(child.sample());
        assertNull(child.treeSnapshot());
        assertEquals("success", child.status());
        assertEquals(200, child.responseStatus());
        assertEquals("{\"code\":0,\"token\":\"abc\"}", child.fullResponseBody());
        assertNotNull(child.request());
        assertNotNull(child.response());
        assertNotNull(child.responseHeaders());
        assertEquals(1000L, child.elapsedMs());
        assertEquals("skipped", root.postprocessors().get(0).status());
    }

    @Test
    void mapsBrokenAssertionFailureToFailedWithErrorMessage() {
        AssertionResult assertion = new AssertionResult();
        assertion.setField("$.code");
        assertion.setStatus(TestStatus.failed);
        DefaultSampleResult broken = sample("步骤一", TestStatus.broken);
        broken.setThrowable(new AssertionError("期望 0 实际 1"));
        broken.addAssertion(assertion);

        MappedResult mapped = RyzeResultMapper.map(broken, MAX_CHARS);

        assertTrue(mapped.sample());
        assertFalse(mapped.suite());
        assertEquals("failed", mapped.status());
        assertNotNull(mapped.errorMessage());
        assertNotNull(mapped.throwableMessage());
        assertNull(mapped.treeSnapshot());
    }

    @Test
    void mapsEngineHaltWithoutChildren() {
        TestSuiteResult suite = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suite.setStatus(TestStatus.broken);
        suite.setThrowable(new RuntimeException("连接被拒绝"));
        suite.setStartTime(T0);
        suite.setEndTime(T1);

        MappedResult mapped = RyzeResultMapper.map(suite, MAX_CHARS);

        assertEquals("error", mapped.status());
        assertEquals("连接被拒绝", mapped.throwableMessage());
        assertTrue(mapped.children().isEmpty());
        assertNotNull(mapped.treeSnapshot());
    }

    @Test
    void returnsNullForNullResult() {
        assertNull(RyzeResultMapper.map(null, MAX_CHARS));
    }

    @Test
    void portsDelegatesToConverters() {
        TestSuiteResult suite = new TestSuiteResult(SCENE_ID.toString(), "登录链路");
        suite.setStatus(TestStatus.passed);
        suite.setStartTime(T0);
        suite.setEndTime(T1);

        assertEquals(RyzeResultSnapshotConverter.toSnapshot(suite),
                new RyzeSnapshotCapture().capture(suite));

        EnvSnapshot env = EnvSnapshot.empty();
        List<StepSpec> specs = List.of(new StepSpec("step-1", Map.of(), List.of(), List.of()));
        List<Map<String, Object>> perStep = List.of();
        SceneSuiteBuilder builder = new SceneSuiteBuilder();
        assertEquals(SceneRyzeConverter.buildSuite("t", env, Map.of(), perStep, specs, List.of()),
                builder.buildSuite("t", env, Map.of(), perStep, specs, List.of()));
    }

    private DefaultSampleResult sample(String title, TestStatus status) {
        DefaultSampleResult sample = new DefaultSampleResult(title);
        sample.setStatus(status);
        sample.setStartTime(T0);
        sample.setEndTime(T1);
        return sample;
    }
}