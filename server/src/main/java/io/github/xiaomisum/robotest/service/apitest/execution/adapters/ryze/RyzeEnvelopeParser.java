package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.ryze.Result;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.result.ExtractorResult;
import io.github.xiaomisum.ryze.result.VariableRecord;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结果信封解析与结果树物化（GitLab 集成详细设计 4.2 第 4 步 / 4.6，WP-7.1）。
 * <p>
 * 信封 {@code result} 由接入方侧 fastjson2 无类型标识序列化（{@code JSON.toJSONString(result)}），
 * {@code children} / {@code request} / {@code response} 均为抽象或接口类型，无法整体反序列化回
 * Ryze 对象——这里按形状递归物化：有 {@code children} 的节点为 {@link TestSuiteResult}，
 * 其余为 {@link DefaultSampleResult}；公共字段经 setter 还原，断言 / 提取器 / 变量经
 * {@link JsonUtils} 转具体类型。渲染仍走既有 {@link RyzeResultMapper}，
 * 不为 CI 结果另写解析器（详设 4.2 第 4 步）。
 * <p>
 * 对 {@code result} 内缺失或类型变化的字段一律容错（详设 4.6 版本差异行）：
 * {@code throwable} 只还原消息（类名在序列化中不可靠，断言失败判别走断言记录）、
 * 时间解析兼容 {@code yyyy-MM-dd HH:mm:ss} 与 ISO 两种格式、单字段解析失败跳过而不整批失败。
 */
public final class RyzeEnvelopeParser {

    private static final String MISSING = "结果信封缺失或无法解析";

    private RyzeEnvelopeParser() {
    }

    /**
     * 解析信封 JSON。
     *
     * @throws xyz.migoo.framework.common.exception.ServiceException 1000017715，JSON 非法或契约字段缺失
     */
    public static RyzeEnvelope parse(String json) {
        Map<String, Object> root;
        try {
            root = JsonUtils.parseObject(json, Map.class);
        } catch (Exception e) {
            throw invalid("JSON 解析失败");
        }
        if (root == null) {
            throw invalid("内容为空");
        }
        String testClass = text(root.get("testClass"));
        String testMethod = text(root.get("testMethod"));
        if (testClass == null || testMethod == null) {
            throw invalid("缺少 testClass / testMethod");
        }
        if (!(root.get("result") instanceof Map<?, ?> node)) {
            throw invalid("缺少 result 结果树");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tree = (Map<String, Object>) node;
        return new RyzeEnvelope(testClass, testMethod, tree);
    }

    /** 信封结果树 → Ryze 结果对象（根节点按形状物化，子树递归） */
    public static Result materialize(RyzeEnvelope envelope) {
        return node(envelope.result());
    }

    // ========== 形状驱动物化 ==========

    private static Result node(Map<String, Object> node) {
        String id = text(node.get("id"));
        String title = text(node.get("title"));
        if (node.get("children") instanceof List<?> children) {
            TestSuiteResult suite = new TestSuiteResult(id, title);
            populate(suite, node);
            for (Object child : children) {
                if (child instanceof Map<?, ?> c) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> childNode = (Map<String, Object>) c;
                    Result materialized = node(childNode);
                    if (materialized != null) {
                        suite.addChild(materialized);
                    }
                }
            }
            return suite;
        }
        DefaultSampleResult sample = new DefaultSampleResult(id, title);
        populate(sample, node);
        materializeSample(sample, node);
        return sample;
    }

    /** 公共字段（Result 基类）：状态 / 起止时间 / 元数据 / 变量 / 前后置处理器 */
    private static void populate(Result result, Map<String, Object> node) {
        result.setStatus(status(node.get("status")));
        result.setStartTime(time(node.get("startTime")));
        result.setEndTime(time(node.get("endTime")));
        result.setRejectBy(text(node.get("rejectBy")));
        if (node.get("metadata") instanceof Map<?, ?> meta) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            meta.forEach((k, v) -> {
                if (k != null) {
                    metadata.put(String.valueOf(k), v);
                }
            });
            result.setMetadata(metadata);
        }
        List<VariableRecord> variables = typedList(node.get("variables"), VariableRecord.class);
        if (variables != null) {
            result.setVariables(variables);
        }
        List<Result> pre = resultList(node.get("preprocessors"));
        if (pre != null) {
            result.setPreprocessors(pre);
        }
        List<Result> post = resultList(node.get("postprocessors"));
        if (post != null) {
            result.setPostprocessors(post);
        }
        Throwable throwable = throwable(node.get("throwable"));
        if (throwable != null) {
            result.setThrowable(throwable);
        }
    }

    /** 采样器字段：采样起止 / 断言 / 提取器 / 请求 / 响应 */
    private static void materializeSample(DefaultSampleResult sample, Map<String, Object> node) {
        sample.setSampleStartTime(time(node.get("sampleStartTime")));
        sample.setSampleEndTime(time(node.get("sampleEndTime")));
        List<AssertionResult> assertions = typedList(node.get("assertions"), AssertionResult.class);
        if (assertions != null) {
            sample.setAssertions(assertions);
        }
        List<ExtractorResult> extractors = typedList(node.get("extractors"), ExtractorResult.class);
        if (extractors != null) {
            sample.setExtractors(extractors);
        }
        if (node.get("request") instanceof Map<?, ?> req) {
            sample.setRequest(request(req));
        }
        if (node.get("response") instanceof Map<?, ?> resp) {
            sample.setResponse(response(resp));
        }
    }

    /**
     * 请求物化：带 {@code url}/{@code method} 的按 {@link RecoveredHttpRequest} 还原（报告需
     * 请求行 / 头 / 体），否则退化为字节载体；都缺时按 {@code format} 可读文本兜底。
     */
    private static SampleResult.RealRequest request(Map<?, ?> req) {
        byte[] body = bytes(req.get("bytes") != null ? req.get("bytes") : req.get("body"));
        String url = text(req.get("url"));
        String method = text(req.get("method"));
        if (url != null && method != null) {
            return new RecoveredHttpRequest(url, method, text(req.get("query")), body,
                    text(req.get("version")), headers(req.get("headers")));
        }
        if (body != null) {
            return SampleResult.DefaultRealRequest.build(body);
        }
        String format = text(req.get("format"));
        return format == null ? null : SampleResult.DefaultRealRequest
                .build(format.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 响应物化：带 {@code status} 的按 {@link RealHTTPResponse} 还原（报告需状态码与响应头），
     * 否则退化为字节载体（响应体仍可读，状态码字段缺省）。
     */
    private static SampleResult.RealResponse response(Map<?, ?> resp) {
        byte[] bytes = bytes(resp.get("bytes") != null ? resp.get("bytes") : resp.get("body"));
        Integer status = integer(resp.get("status"));
        if (status == null) {
            if (bytes != null) {
                return SampleResult.DefaultRealResponse.build(bytes);
            }
            // fastjson2 对无字节载体的响应只留 format（即体文本），据此兜底
            String format = text(resp.get("format"));
            return format == null ? null
                    : SampleResult.DefaultRealResponse.build(format.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new RealHTTPResponse(bytes == null ? new byte[0] : bytes, status,
                text(resp.get("version")), text(resp.get("message")),
                headers(resp.get("headers")).toArray(Header[]::new));
    }

    /** 响应头 / 请求头列表（fastjson2 getter 驱动，形状为 {@code [{name, value}]}） */
    private static List<Header> headers(Object value) {
        List<Header> headers = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> h) {
                    String name = text(h.get("name"));
                    if (name != null) {
                        headers.add(new BasicHeader(name, text(h.get("value"))));
                    }
                }
            }
        }
        return headers;
    }

    // ========== 字段还原 ==========

    private static List<Result> resultList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Result> results = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childNode = (Map<String, Object>) m;
                results.add(node(childNode));
            }
        }
        return results;
    }

    /** 具体类型列表（断言 / 提取器 / 变量）：经 JsonUtils 逐条转换，单条失败跳过不整批失败 */
    private static <T> List<T> typedList(Object value, Class<T> type) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<T> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            try {
                out.add(JsonUtils.parseObject(JsonUtils.toJsonString(item), type));
            } catch (Exception ignored) {
                // 容错：字段结构差异只丢该条明细，不使整信封解析失败
            }
        }
        return out;
    }

    /** throwable 只还原消息：序列化产物无类名，断言失败判别走断言记录（RyzeResultAdapter） */
    private static Throwable throwable(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> m) {
            String message = text(m.get("message"));
            return message == null ? null : new RuntimeException(message);
        }
        String message = text(value);
        return message == null ? null : new RuntimeException(message);
    }

    private static TestStatus status(Object value) {
        String raw = text(value);
        if (raw == null) {
            return TestStatus.passed;
        }
        try {
            return TestStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return TestStatus.passed;
        }
    }

    /** 时间还原：fastjson2 默认 {@code yyyy-MM-dd HH:mm:ss[.SSS]}，兼容 ISO 的 {@code T} 分隔 */
    private static LocalDateTime time(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        String raw = text(value);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 字节还原：fastjson2 对 {@code getBody()} 返回的 {@code byte[]} 产出数字数组（实测金样），
     * 对部分场景产出 Base64 字符串——两种形状都兼容，均失败视为缺省。
     */
    private static byte[] bytes(Object value) {
        if (value instanceof byte[] raw) {
            return raw;
        }
        if (value instanceof Collection<?> list) {
            byte[] out = new byte[list.size()];
            int i = 0;
            for (Object item : list) {
                if (!(item instanceof Number n)) {
                    return null;
                }
                out[i++] = n.byteValue();
            }
            return out;
        }
        String raw = text(value);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer integer(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            String raw = text(value);
            return raw == null ? null : Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String raw = value.toString().trim();
        return raw.isEmpty() ? null : raw;
    }

    private static RuntimeException invalid(String reason) {
        return ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_ENVELOPE_INVALID.code(),
                MISSING + "：" + reason);
    }
}
