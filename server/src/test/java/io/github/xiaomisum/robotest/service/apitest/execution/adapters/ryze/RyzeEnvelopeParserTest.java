package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import com.alibaba.fastjson2.JSON;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.ryze.Result;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPRequest;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.result.VariableRecord;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 信封解析器金样往返测试（GitLab 集成详细设计 4.6 导出文件契约）。
 * <p>信封由接入方侧 fastjson2 无类型标识序列化，这里用同一 fastjson2 真实序列化 Ryze 结果树
 * 产生金样，经 {@link RyzeEnvelopeParser} 物化回 Ryze 对象并走 {@link RyzeResultMapper} 渲染，
 * 验证解析器与平台既有渲染链路兼容；另覆盖 HTTP 请求形状与契约缺失的容错分支。
 */
class RyzeEnvelopeParserTest {

    private static final String TEST_CLASS = "com.example.api.OrderApiTests";
    private static final String TEST_METHOD = "createOrder";

    /** 构造 Ryze 结果树：套件 + 一个带断言 / 请求响应的采样器 */
    private static TestSuiteResult goldenTree() {
        TestSuiteResult suite = new TestSuiteResult("suite-id", "订单接口套件");
        suite.setStatus(TestStatus.passed);
        suite.setStartTime(LocalDateTime.of(2026, 10, 13, 10, 0, 0));
        suite.setEndTime(LocalDateTime.of(2026, 10, 13, 10, 0, 5));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("gitlab_ci", true);
        metadata.put("sourceRef", TEST_CLASS + "#" + TEST_METHOD);
        suite.setMetadata(metadata);
        suite.setVariables(List.of(new VariableRecord("orderId", "1", 1)));

        DefaultSampleResult sample = new DefaultSampleResult("sample-id", "创建订单");
        sample.setStatus(TestStatus.broken);
        sample.setSampleStartTime(LocalDateTime.of(2026, 10, 13, 10, 0, 1));
        sample.setSampleEndTime(LocalDateTime.of(2026, 10, 13, 10, 0, 2));
        sample.setRequest(SampleResult.DefaultRealRequest
                .build("{\"name\":\"apple\"}".getBytes(StandardCharsets.UTF_8)));
        sample.setResponse(new RealHTTPResponse(
                "{\"code\":0}".getBytes(StandardCharsets.UTF_8), 200, "HTTP/1.1", "OK",
                new BasicHeader("Content-Type", "application/json")));

        AssertionResult assertion = new AssertionResult();
        assertion.setField("code");
        assertion.setRule("equals");
        assertion.setExpected("0");
        assertion.setActual("500");
        assertion.setStatus(TestStatus.failed);
        assertion.setMessage("期望 0 实际 500");
        sample.setAssertions(List.of(assertion));

        sample.setThrowable(new RuntimeException("断言失败：code 期望 0 实际 500"));
        suite.addChild(sample);
        return suite;
    }

    /** 按接入方侧口径组装信封 JSON（{ testClass, testMethod, result }，fastjson2 序列化） */
    private static String goldenEnvelopeJson() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("testClass", TEST_CLASS);
        envelope.put("testMethod", TEST_METHOD);
        envelope.put("result", goldenTree());
        return JSON.toJSONString(envelope);
    }

    private static RyzeEnvelope roundTrip() {
        return RyzeEnvelopeParser.parse(goldenEnvelopeJson());
    }

    @Test
    void parseReadsContractFields() {
        RyzeEnvelope envelope = roundTrip();

        assertEquals(TEST_CLASS, envelope.testClass());
        assertEquals(TEST_METHOD, envelope.testMethod());
        assertEquals(TEST_CLASS + "#" + TEST_METHOD, envelope.sourceRef());
        assertNotNull(envelope.result());
    }

    @Test
    void materializeRestoresSuiteTree() {
        Result root = RyzeEnvelopeParser.materialize(roundTrip());

        assertInstanceOf(TestSuiteResult.class, root);
        assertEquals("订单接口套件", root.getTitle());
        assertEquals(TestStatus.passed, root.getStatus());
        assertNotNull(root.getStartTime());
        assertEquals(Boolean.TRUE, root.getMetadata().get("gitlab_ci"));
        assertEquals(1, root.getVariables().size());
        assertEquals("orderId", root.getVariables().get(0).getName());
        assertEquals(1, ((TestSuiteResult) root).getChildren().size());
    }

    @Test
    void materializeRestoresSampleDetails() {
        TestSuiteResult suite = (TestSuiteResult) RyzeEnvelopeParser.materialize(roundTrip());
        SampleResult sample = (SampleResult) suite.getChildren().get(0);

        assertEquals("创建订单", sample.getTitle());
        assertEquals(TestStatus.broken, sample.getStatus());
        assertNotNull(sample.getSampleStartTime());
        assertNotNull(sample.getSampleEndTime());
        assertNotNull(sample.getRequest());
        assertEquals("{\"name\":\"apple\"}", new String(sample.getRequest().bytes(),
                StandardCharsets.UTF_8));
        RealHTTPResponse response = assertInstanceOf(RealHTTPResponse.class, sample.getResponse());
        assertEquals(200, response.getStatus());
        assertEquals("HTTP/1.1", response.getVersion());
        assertEquals("{\"code\":0}", new String(response.getBody(), StandardCharsets.UTF_8));
        assertFalse(response.getHeaders().isEmpty());
        assertEquals("application/json", response.getHeaders().get(0).getValue());
        assertEquals(1, sample.getAssertions().size());
        AssertionResult assertion = sample.getAssertions().get(0);
        assertEquals("code", assertion.getField());
        assertEquals(TestStatus.failed, assertion.getStatus());
        assertNotNull(sample.getThrowable());
        assertTrue(sample.getThrowable().getMessage().contains("断言失败"));
    }

    /** HTTP 请求形状（url/method/头）→ RecoveredHttpRequest，渲染走既有 RealHTTPRequest 分支 */
    @Test
    void materializeRestoresHttpRequest() {
        String json = "{\"testClass\":\"a.b.C\",\"testMethod\":\"m\",\"result\":{"
                + "\"title\":\"s\",\"children\":[{\"title\":\"call\",\"request\":{"
                + "\"url\":\"http://api.example.com/orders\",\"method\":\"POST\","
                + "\"query\":\"trace=1\",\"body\":\"eyJuYW1lIjoiYXBwbGUifQ==\","
                + "\"version\":\"HTTP/1.1\",\"headers\":["
                + "{\"name\":\"Content-Type\",\"value\":\"application/json\"}]}}]}}";

        TestSuiteResult suite = (TestSuiteResult) RyzeEnvelopeParser.materialize(
                RyzeEnvelopeParser.parse(json));
        SampleResult sample = (SampleResult) suite.getChildren().get(0);

        RealHTTPRequest request = assertInstanceOf(RealHTTPRequest.class, sample.getRequest());
        assertEquals("http://api.example.com/orders", request.getUrl());
        assertEquals("POST", request.getMethod());
        assertEquals("trace=1", request.getQuery());
        assertEquals("{\"name\":\"apple\"}", new String(request.getBody(), StandardCharsets.UTF_8));
        assertFalse(request.getHeaders().isEmpty());

        MappedResult mapped = RyzeResultMapper.map(suite, 1000);
        assertEquals("http://api.example.com/orders", mapped.children().get(0)
                .request().get("url"));
    }

    @Test
    void materializedTreeRendersThroughPlatformMapper() {
        Result root = RyzeEnvelopeParser.materialize(roundTrip());

        MappedResult mapped = RyzeResultMapper.map(root, 1000);

        assertEquals("订单接口套件", mapped.title());
        assertEquals("success", mapped.status());
        MappedResult child = mapped.children().get(0);
        // broken + 断言失败 → failed（RyzeResultAdapter 断言失败兼容判别）
        assertEquals("failed", child.status());
        assertEquals(1, child.assertions().size());
        assertNotNull(mapped.treeSnapshot());
    }

    @Test
    void parseRejectsInvalidJson() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> RyzeEnvelopeParser.parse("not-a-json"));

        assertEquals(1000017715, ex.getCode());
    }

    @Test
    void parseRejectsMissingContractFields() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> RyzeEnvelopeParser.parse("{\"testClass\":\"a.b.C\"}"));

        assertEquals(1000017715, ex.getCode());
        assertTrue(ex.getMessage().contains("testMethod"));
    }

    @Test
    void parseRejectsMissingResultTree() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> RyzeEnvelopeParser.parse(
                        "{\"testClass\":\"a.b.C\",\"testMethod\":\"m\",\"result\":\"x\"}"));

        assertEquals(1000017715, ex.getCode());
    }

    @Test
    void materializeToleratesMissingOptionalFields() {
        // result 只有契约最少字段：不因缺状态 / 时间 / 请求响应而失败（详设 4.6 容错口径）
        String json = "{\"testClass\":\"a.b.C\",\"testMethod\":\"m\",\"result\":{\"title\":\"n\"}}";

        Result root = RyzeEnvelopeParser.materialize(RyzeEnvelopeParser.parse(json));

        assertInstanceOf(DefaultSampleResult.class, root);
        assertNull(root.getStartTime());
        assertEquals(TestStatus.passed, root.getStatus());
        assertNotNull(RyzeResultMapper.map(root, 1000));
    }

    @Test
    void materializeEmptyChildrenIsSuite() {
        String json = "{\"testClass\":\"a.b.C\",\"testMethod\":\"m\","
                + "\"result\":{\"children\":[]}}";

        Result root = RyzeEnvelopeParser.materialize(RyzeEnvelopeParser.parse(json));

        assertInstanceOf(TestSuiteResult.class, root);
        assertTrue(((TestSuiteResult) root).getChildren().isEmpty());
    }
}
