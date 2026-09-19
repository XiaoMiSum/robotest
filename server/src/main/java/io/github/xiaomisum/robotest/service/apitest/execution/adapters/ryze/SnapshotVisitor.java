package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.ryze.protocol.http.RealHTTPRequest;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.result.ExtractorResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;
import org.apache.hc.core5.http.Header;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result → 数据集快照映射（接口测试域重构方案 04 §3.1.2，测试报告详细设计 2.3.1）。
 * 承载报表 result.steps[].request/response/assertions/extractors 与处理器明细的快照形状：
 * 响应体按 {@code maxResponseBodyChars} 截断、断言 status 空判保护；与 {@link RyzeResultSnapshotConverter}
 * 的树形快照（不截断、含 version/message）共享底层助手而不重复实现（04 §4.1 步骤 2 合并评审）。
 * 纯函数零 IO：映射不依赖任何环境状态。
 */
public final class SnapshotVisitor {

    private SnapshotVisitor() {
    }

    /** 请求快照：取引擎实际发出的请求（样本真实 URL/合并头/渲染体） */
    public static Map<String, Object> requestSnapshot(SampleResult.RealRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (request instanceof RealHTTPRequest http) {
            snapshot.put("url", http.getUrl());
            snapshot.put("method", http.getMethod());
            snapshot.put("query", http.getQuery());
            snapshot.put("version", http.getVersion());
            snapshot.put("headers", toHeaderMap(http.getHeaders()));
            snapshot.put("body", bytesAsString(http.getBody()));
        }
        snapshot.put("format", request.getFormat());
        return snapshot;
    }

    /** 响应快照：状态码/头/体（数据集形状：不含 version/message、body 截断），空响应返回 null */
    public static Map<String, Object> responseSnapshot(SampleResult sample, int maxResponseBodyChars) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (sample.getResponse() instanceof RealHTTPResponse response) {
            snapshot.put("status", response.status());
            snapshot.put("headers", toHeaderMap(response.headers()));
            snapshot.put("body", truncate(bytesAsString(response), maxResponseBodyChars));
            snapshot.put("format", response.format());
        }
        return snapshot.isEmpty() ? null : snapshot;
    }

    /** 断言明细（AssertionResult → {field, rule, expected, actual, status, message}） */
    public static List<Map<String, Object>> assertionSnapshots(SampleResult sample) {
        if (sample.getAssertions() == null || sample.getAssertions().isEmpty()) {
            return List.of();
        }
        return sample.getAssertions().stream().map(SnapshotVisitor::toAssertion).toList();
    }

    /** 提取器明细（ExtractorResult → {refName, field, value, defaultValue, message}） */
    public static List<Map<String, Object>> extractorSnapshots(SampleResult sample) {
        if (sample.getExtractors() == null || sample.getExtractors().isEmpty()) {
            return List.of();
        }
        return sample.getExtractors().stream().map(SnapshotVisitor::toExtractor).toList();
    }

    private static Map<String, Object> toAssertion(AssertionResult assertion) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("field", assertion.getField());
        snapshot.put("rule", assertion.getRule());
        snapshot.put("expected", assertion.getExpected());
        snapshot.put("actual", assertion.getActual());
        snapshot.put("status", assertion.getStatus() == null ? null : assertion.getStatus().name());
        snapshot.put("message", assertion.getMessage());
        return snapshot;
    }

    private static Map<String, Object> toExtractor(ExtractorResult extractor) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("refName", extractor.getRefName());
        snapshot.put("field", extractor.getField());
        snapshot.put("value", extractor.getValue());
        snapshot.put("defaultValue", extractor.isDefaultValue());
        snapshot.put("message", extractor.getMessage());
        return snapshot;
    }

    /** 请求/响应头：同名 header 合并为逗号分隔（与树形快照口径一致，供两处复用） */
    static Map<String, Object> toHeaderMap(List<Header> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Header header : headers) {
            map.merge(header.getName(), header.getValue(), (a, b) -> a + ", " + b);
        }
        return map;
    }

    /** 响应体直读（UTF-8）；读取出错时回退 format 文本，保证报告仍可读 */
    static String bytesAsString(RealHTTPResponse response) {
        try {
            return response.bytesAsString();
        } catch (Exception ex) {
            return response.format();
        }
    }

    static String bytesAsString(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }
}