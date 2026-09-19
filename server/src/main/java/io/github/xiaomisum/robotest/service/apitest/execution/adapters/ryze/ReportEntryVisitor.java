package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.ryze.Result;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Result 树 → 报表条目映射（接口测试域重构方案 04 §3.1.2：toProcessorEntries/toReportEntry/extractChildOutcome）。
 * 步骤/处理器执行明细采用数据集快照形状，快照细节委托 {@link SnapshotVisitor}；
 * 仅剩编排语义（scene 步骤 map 字段、停止运行连线逻辑）留在 SceneExecutionServiceImpl。
 */
public final class ReportEntryVisitor {

    private ReportEntryVisitor() {
    }

    /** 单步骤结果切片：状态/响应/耗时/子结果；request/response/assertions/extractors 为数据集快照 */
    public record StepOutcome(String status, Integer responseStatus, Map<String, Object> responseHeaders,
            String responseBody, String errorMessage, Long elapsedMs, Map<String, Object> request,
            List<Result> sampleResults,
            Map<String, Object> response, List<Map<String, Object>> assertions,
            List<Map<String, Object>> extractors, Result rootResult) {

        /** 无数据集快照的构造（超时/异常/步骤级结果聚合场景），根结果为空 */
        public StepOutcome(String status, Integer responseStatus, Map<String, Object> responseHeaders,
                String responseBody, String errorMessage, Long elapsedMs,
                List<Result> sampleResults) {
            this(status, responseStatus, responseHeaders, responseBody, errorMessage, elapsedMs, null, sampleResults,
                    null, List.of(), List.of(), null);
        }
    }

    /** 步骤规格解析结果：errorMessage 非空表示无法执行 */
    public record ResolvedSpec(SceneRyzeConverter.StepSpec spec, String errorMessage) {
    }

    /** 单个子结果 → 步骤结果切片（SampleResult 取响应摘要与数据集快照，非 SampleResult 仅状态/错误） */
    public static StepOutcome extractChildOutcome(Result childResult, int maxResponseBodyChars) {
        Long elapsed = elapsedMillis(childResult.getStartTime(), childResult.getEndTime());
        Throwable error = childResult.getThrowable();
        if (childResult instanceof SampleResult sample) {
            Integer responseStatus = null;
            Map<String, Object> responseHeaders = null;
            String responseBody = null;
            if (sample.getResponse() instanceof RealHTTPResponse response) {
                responseStatus = response.status();
                responseHeaders = SnapshotVisitor.toHeaderMap(response.headers());
                responseBody = SnapshotVisitor.bytesAsString(response);
            }
            Throwable sampleError = sample.getThrowable() != null ? sample.getThrowable() : error;
            return new StepOutcome(RyzeResultAdapter.resolveStepStatus(sample), responseStatus, responseHeaders,
                    responseBody, RyzeResultAdapter.errorMessage(sampleError), elapsed,
                    SnapshotVisitor.requestSnapshot(sample.getRequest()), List.of(),
                    SnapshotVisitor.responseSnapshot(sample, maxResponseBodyChars),
                    SnapshotVisitor.assertionSnapshots(sample), SnapshotVisitor.extractorSnapshots(sample), null);
        }
        return new StepOutcome(RyzeResultAdapter.resolveStepStatus(childResult), null, null, null,
                RyzeResultAdapter.errorMessage(error), elapsed, List.of());
    }

    /**
     * 前置/后置处理器结果节点 → 处理器执行明细（形状同步骤元素，测试报告详细设计 2.3）。
     * 处理器即 SampleResult，携带 request/response/assertions/extractors，可直接复用快照构建。
     */
    public static List<Map<String, Object>> processorEntries(List<Result> nodes, int maxResponseBodyChars) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Result node : nodes) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", node.getTitle());
            entry.put("status", RyzeResultAdapter.resolveStepStatus(node));
            if (node instanceof SampleResult sample) {
                Throwable sampleError = sample.getThrowable() != null ? sample.getThrowable() : node.getThrowable();
                Long elapsed = elapsedMillis(sample.getStartTime(), sample.getEndTime());
                entry.put("type", sample.getRequest() == null ? null : "HTTP");
                entry.put("durationMs", elapsed);
                entry.put("request", SnapshotVisitor.requestSnapshot(sample.getRequest()));
                entry.put("response", SnapshotVisitor.responseSnapshot(sample, maxResponseBodyChars));
                entry.put("assertions", SnapshotVisitor.assertionSnapshots(sample));
                entry.put("extractors", SnapshotVisitor.extractorSnapshots(sample));
                entry.put("errorMessage", RyzeResultAdapter.errorMessage(sampleError));
            } else {
                entry.put("errorMessage", RyzeResultAdapter.errorMessage(node.getThrowable()));
            }
            entries.add(entry);
        }
        return entries;
    }

    /** 步骤执行结果 → 报表步骤条目（stepId/name 取自平台场景步骤 map，快照取自 outcome） */
    public static Map<String, Object> reportEntry(Map<String, Object> step, ResolvedSpec spec, StepOutcome outcome) {
        Map<String, Object> entry = new LinkedHashMap<>();
        UUID stepId = SceneStepUtil.getUUID(step, "id");
        entry.put("stepId", stepId != null ? stepId.toString() : null);
        entry.put("name", SceneStepUtil.getString(step, "name", null));
        entry.put("type", "HTTP");
        entry.put("status", outcome.status());
        if (spec.spec() != null) {
            // 请求快照优先取引擎实际发出的请求（SampleResult 真实 URL/合并头/渲染体）；
            // 超时/未产出时回退解析后配置，保证报告仍有请求信息可看
            Map<String, Object> request = outcome.request() != null ? outcome.request()
                    : configRequest(spec.spec().requestConfig());
            entry.put("request", request);
        }
        entry.put("response", outcome.response());
        entry.put("assertions", outcome.assertions());
        entry.put("extractors", outcome.extractors());
        entry.put("errorMessage", outcome.errorMessage());
        entry.put("durationMs", outcome.elapsedMs());
        return entry;
    }

    /** 结果节点耗时：任一时间缺失（异常/中止执行未记录起止）返回 null，避免 Duration.between NPE */
    public static Long elapsedMillis(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? null : Duration.between(start, end).toMillis();
    }

    /** 步骤配置直译请求快照：仅做回退用（无样本时可读），与旧报告口径一致 */
    private static Map<String, Object> configRequest(Map<String, Object> config) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", config.getOrDefault("method", "GET"));
        request.put("url", config.get("url"));
        request.put("headers", config.get("headers"));
        request.put("body", config.get("body"));
        return request;
    }
}