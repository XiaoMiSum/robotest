package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.result.ExtractorResult;
import io.github.xiaomisum.ryze.result.VariableRecord;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.robotest.framework.time.UtcTime;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultSnapshotConverter;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** ryze 结果树 → 持久化 JSON 快照（测试报告详细设计 2.3.3） */
class RyzeResultSnapshotConverterTest {

    @Test
    void toSnapshotMapsNullToNull() {
        assertNull(RyzeResultSnapshotConverter.toSnapshot(null));
    }

    @Test
    void toSnapshotPreservesTreeAndSampleDetails() {
        TestSuiteResult top = new TestSuiteResult("场景");
        top.setStatus(TestStatus.passed);
        top.setStartTime(LocalDateTime.of(2026, 9, 10, 10, 0));
        top.setEndTime(LocalDateTime.of(2026, 9, 10, 10, 0, 1));

        DefaultSampleResult sample = new DefaultSampleResult("登录接口");
        sample.setStatus(TestStatus.failed);
        sample.setSampleStartTime(LocalDateTime.of(2026, 9, 10, 10, 0));
        sample.setSampleEndTime(LocalDateTime.of(2026, 9, 10, 10, 0, 1));
        // 响应体完整保留（不截断）：构造字节量超出数据集口径的 1MB 截断阈值
        String big = "x".repeat(1024 * 1024 * 2);
        sample.setResponse(new RealHTTPResponse(big.getBytes(), 502, "HTTP/1.1", "Bad Gateway",
                new BasicHeader("Content-Type", "text/plain")));
        sample.setRequest(SampleResult.DefaultRealRequest.build("GET /api/login".getBytes()));
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
        top.addChild(sample);

        Map<String, Object> snapshot = RyzeResultSnapshotConverter.toSnapshot(top);

        assertEquals("passed", snapshot.get("status"));
        assertEquals(UtcTime.toIsoFromSystemLocal(LocalDateTime.of(2026, 9, 10, 10, 0)),
                snapshot.get("startTime"));
        assertEquals(1, ((List<?>) snapshot.get("children")).size());

        @SuppressWarnings("unchecked")
        Map<String, Object> step = (Map<String, Object>) ((List<Map<String, Object>>) snapshot.get("children")).get(0);
        assertEquals("failed", step.get("status"));
        assertEquals("1.0 s", step.get("duration"));
        assertNotNull(step.get("request"));
        assertNotNull(step.get("response"));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) step.get("response");
        assertEquals(502, response.get("status"));
        assertEquals("Bad Gateway", response.get("message"));
        // 响应体不截断：完整字节保留
        assertEquals(big, response.get("body"));
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) response.get("headers");
        assertEquals("text/plain", headers.get("Content-Type"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assertions = (List<Map<String, Object>>) step.get("assertions");
        assertEquals(1, assertions.size());
        assertEquals("failed", assertions.get(0).get("status"));
        assertEquals("$.code", assertions.get(0).get("field"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extractors = (List<Map<String, Object>>) step.get("extractors");
        assertEquals("token", extractors.get(0).get("refName"));
        assertEquals("abc", extractors.get(0).get("value"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> variables = (List<Map<String, Object>>) step.get("variables");
        assertEquals("token", variables.get(0).get("name"));
        assertEquals("abc", variables.get(0).get("value"));
    }

    @Test
    void toSnapshotKeepsThrowableTypeAndMessageOnly() {
        TestSuiteResult top = new TestSuiteResult("异常场景");
        RuntimeException boom = new RuntimeException("连接被拒绝");
        top.setThrowable(boom);

        Map<String, Object> snapshot = RyzeResultSnapshotConverter.toSnapshot(top);

        @SuppressWarnings("unchecked")
        Map<String, Object> throwable = (Map<String, Object>) snapshot.get("throwable");
        assertEquals(RuntimeException.class.getName(), throwable.get("type"));
        assertEquals("连接被拒绝", throwable.get("message"));
        // 不序列化堆栈，避免快照膨胀
        assertNull(throwable.get("stackTrace"));
    }

    @Test
    void toSnapshotExpandsExceptionGroupAndSuppressed() {
        TestSuiteResult top = new TestSuiteResult("提取器失败");
        RuntimeException boom = new RuntimeException("拦截器链执行异常");
        boom.addSuppressed(new RuntimeException("连接被拒绝"));
        top.setThrowable(boom);
        top.addChild(sampleWithExceptionGroup());

        Map<String, Object> snapshot = RyzeResultSnapshotConverter.toSnapshot(top);

        @SuppressWarnings("unchecked")
        Map<String, Object> throwable = (Map<String, Object>) snapshot.get("throwable");
        assertEquals(RuntimeException.class.getName(), throwable.get("type"));
        assertEquals("拦截器链执行异常", throwable.get("message"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suppressedList = (List<Map<String, Object>>) throwable.get("suppressed");
        assertEquals(1, suppressedList.size());
        assertEquals("连接被拒绝", suppressedList.get(0).get("message"));
        // 普通异常不携带 exceptions 字段
        assertNull(throwable.get("exceptions"));

        @SuppressWarnings("unchecked")
        Map<String, Object> step = (Map<String, Object>) ((List<Map<String, Object>>) snapshot.get("children")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> stepThrowable = (Map<String, Object>) step.get("throwable");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exceptions = (List<Map<String, Object>>) stepThrowable.get("exceptions");
        assertEquals(2, exceptions.size());
        assertEquals("未提取到数据且无默认值，表达式: $.token", exceptions.get(0).get("message"));
        assertEquals("对第 2 个字段取值失败", exceptions.get(1).get("message"));
        assertNull(exceptions.get(0).get("exceptions"));
    }

    private DefaultSampleResult sampleWithExceptionGroup() {
        DefaultSampleResult sample = new DefaultSampleResult("登录接口");
        sample.setStatus(TestStatus.broken);
        sample.setThrowable(new io.github.xiaomisum.ryze.support.ExceptionGroup("提取器执行失败", List.of(
                new IllegalArgumentException("未提取到数据且无默认值，表达式: $.token"),
                new RuntimeException("对第 2 个字段取值失败"))));
        return sample;
    }
}