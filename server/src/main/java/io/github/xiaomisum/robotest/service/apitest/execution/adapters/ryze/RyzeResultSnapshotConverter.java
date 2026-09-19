package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.ryze.Result;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPRequest;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.result.ExtractorResult;
import io.github.xiaomisum.ryze.result.VariableRecord;
import io.github.xiaomisum.ryze.support.ExceptionGroup;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ryze 结果树 → 可持久化 JSON 快照（测试报告详细设计 2.3.3：ryze_snapshot 保存平台自有字段模型之外
 * 的完整执行结果树，供结果回溯与转换问题定位）。
 * <p>
 * 树形快照由 {@link ResultTreeWalker} 遍历生成（接口测试域重构方案 04 §3.1.2），
 * 保留结果树层级（suite.children / sample 的 assertions.extractors / 各级 preprocessors.postprocessors
 * 与变量增量），字段与 ryze {result.md} 表述一致；响应体按完整字节写入不截断。仅序列化不做还原。
 * 与数据集快照（{@link SnapshotVisitor}）共享 toHeaderMap 助手，不重复实现。
 */
public final class RyzeResultSnapshotConverter {

    private RyzeResultSnapshotConverter() {
    }

    /**
     * 结果树序列化入口。
     *
     * @param result 顶层执行结果（场景执行取顶层 TestSuiteResult；定时任务取整包顶层 TestSuiteResult）
     * @return 树形 JSON 快照，入参为 null 时返回 null
     */
    public static Map<String, Object> toSnapshot(Result result) {
        if (result == null) {
            return null;
        }
        Map<Result, Map<String, Object>> nodes = new LinkedHashMap<>();
        List<Result> order = new ArrayList<>();
        ResultTreeWalker.walk(result, new ResultVisitor() {
            @Override
            public void visitSuite(TestSuiteResult suite) {
                nodes.put(suite, commonNode(suite));
                order.add(suite);
            }

            @Override
            public void visitSample(SampleResult sample) {
                nodes.put(sample, sampleNode(sample));
                order.add(sample);
            }

            @Override
            public void visitFailure(Result failed) {
                nodes.put(failed, commonNode(failed));
                order.add(failed);
            }
        });
        // children 在遍历后统一挂接：保持公共字段在前、children 在后的键序（黄金文件字节门禁）
        for (Result node : order) {
            if (node instanceof TestSuiteResult suite) {
                nodes.get(suite).put("children", linkedChildren(nodes, suite));
            }
        }
        return nodes.get(result);
    }

    private static List<Map<String, Object>> linkedChildren(
            Map<Result, Map<String, Object>> nodes, TestSuiteResult suite) {
        List<Map<String, Object>> children = new ArrayList<>();
        if (suite.getChildren() == null) {
            return children;
        }
        for (Result child : suite.getChildren()) {
            children.add(nodes.get(child));
        }
        return children;
    }

    /** 单个结果节点：公共字段 + 子类扩展字段（TestSuiteResult/children、SampleResult/取样快照） */
    private static Map<String, Object> toNode(Result node) {
        Map<String, Object> snapshot = commonNode(node);
        if (node instanceof TestSuiteResult suite) {
            snapshot.put("children", toNodes(suite.getChildren()));
        } else if (node instanceof SampleResult sample) {
            extendSample(snapshot, sample);
        }
        return snapshot;
    }

    /** 公共字段：id/title/status/起止/异常/元数据/变量增量/前后置处理器（处理器多级以完整节点递归） */
    private static Map<String, Object> commonNode(Result node) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", node.getId());
        snapshot.put("title", node.getTitle());
        snapshot.put("status", node.getStatus().name());
        snapshot.put("startTime", toIso(node.getStartTime()));
        snapshot.put("endTime", toIso(node.getEndTime()));
        snapshot.put("throwable", toThrowable(node.getThrowable()));
        snapshot.put("rejectBy", node.getRejectBy());
        snapshot.put("metadata", node.getMetadata());
        snapshot.put("variables", toVariables(node.getVariables()));
        snapshot.put("preprocessors", toNodes(node.getPreprocessors()));
        snapshot.put("postprocessors", toNodes(node.getPostprocessors()));
        return snapshot;
    }

    private static void extendSample(Map<String, Object> snapshot, SampleResult sample) {
        snapshot.put("sampleStartTime", toIso(sample.getSampleStartTime()));
        snapshot.put("sampleEndTime", toIso(sample.getSampleEndTime()));
        snapshot.put("duration", sample.getDuration());
        snapshot.put("request", toRequest(sample.getRequest()));
        snapshot.put("response", toResponse(sample.getResponse()));
        snapshot.put("assertions", toAssertions(sample.getAssertions()));
        snapshot.put("extractors", toExtractors(sample.getExtractors()));
    }

    /** SampleResult 叶节点：公共字段 + 取样快照字段（无 children） */
    private static Map<String, Object> sampleNode(SampleResult sample) {
        Map<String, Object> snapshot = commonNode(sample);
        extendSample(snapshot, sample);
        return snapshot;
    }

    private static List<Map<String, Object>> toNodes(List<? extends Result> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Result node : nodes) {
            list.add(toNode(node));
        }
        return list;
    }

    /** 异常仅留档类型与消息（堆栈过大，且此类异常多暴露于报告 errorMessage）；ExceptionGroup 展开子异常，链包装保留 suppressed */
    private static Map<String, Object> toThrowable(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", throwable.getClass().getName());
        snapshot.put("message", throwable.getMessage());
        if (throwable instanceof ExceptionGroup group) {
            List<Map<String, Object>> exceptions = group.getExceptions().stream()
                    .map(RyzeResultSnapshotConverter::toThrowable).toList();
            snapshot.put("exceptions", exceptions);
        }
        if (throwable.getSuppressed().length > 0) {
            List<Map<String, Object>> suppressed = Arrays.stream(throwable.getSuppressed())
                    .map(RyzeResultSnapshotConverter::toThrowable).toList();
            snapshot.put("suppressed", suppressed);
        }
        return snapshot;
    }

    /** 变量增量（VariableRecord → {name, raw, value}，字段以 ryze result.md 为准） */
    private static List<Map<String, Object>> toVariables(List<VariableRecord> variables) {
        if (variables == null || variables.isEmpty()) {
            return List.of();
        }
        return variables.stream().map(variable -> {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", variable.getName());
            snapshot.put("raw", variable.getRaw());
            snapshot.put("value", variable.getValue());
            return snapshot;
        }).toList();
    }

    /** 请求快照：HTTP 完整字段；其余协议保留 format 可读文本（结果树序列化说明：getter 驱动） */
    private static Map<String, Object> toRequest(SampleResult.RealRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (request instanceof RealHTTPRequest http) {
            snapshot.put("url", http.getUrl());
            snapshot.put("method", http.getMethod());
            snapshot.put("query", http.getQuery());
            snapshot.put("version", http.getVersion());
            snapshot.put("headers", SnapshotVisitor.toHeaderMap(http.getHeaders()));
            snapshot.put("body", bytesAsString(http.getBody()));
        }
        snapshot.put("format", request.getFormat());
        return snapshot;
    }

    /** 响应快照：HTTP 完整字段（响应体不截断）；其余协议保留 status/format。 */
    private static Map<String, Object> toResponse(SampleResult.RealResponse response) {
        if (response == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", response.getStatus());
        if (response instanceof RealHTTPResponse http) {
            snapshot.put("version", http.getVersion());
            snapshot.put("message", http.getMessage());
            snapshot.put("headers", SnapshotVisitor.toHeaderMap(http.getHeaders()));
            snapshot.put("body", bytesAsString(http.getBody()));
        }
        snapshot.put("format", response.getFormat());
        return snapshot;
    }

    private static List<Map<String, Object>> toAssertions(List<AssertionResult> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            return List.of();
        }
        return assertions.stream().map(assertion -> {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("field", assertion.getField());
            snapshot.put("rule", assertion.getRule());
            snapshot.put("expected", assertion.getExpected());
            snapshot.put("actual", assertion.getActual());
            snapshot.put("status", assertion.getStatus().name());
            snapshot.put("message", assertion.getMessage());
            return snapshot;
        }).toList();
    }

    private static List<Map<String, Object>> toExtractors(List<ExtractorResult> extractors) {
        if (extractors == null || extractors.isEmpty()) {
            return List.of();
        }
        return extractors.stream().map(extractor -> {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("refName", extractor.getRefName());
            snapshot.put("field", extractor.getField());
            snapshot.put("value", extractor.getValue());
            snapshot.put("defaultValue", extractor.isDefaultValue());
            snapshot.put("message", extractor.getMessage());
            return snapshot;
        }).toList();
    }

    private static String bytesAsString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes);
    }

    private static String toIso(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}