package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.service.apitest.execution.ReportEntryVisitor;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.ResultMapper;
import io.github.xiaomisum.ryze.Result;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Ryze 结果树 → {@link MappedResult} 平台投影（端口 {@link ResultMapper} 实现，04 §3.1.1）。
 * <p>
 * 编排层经由本端口取得零 ryze 的结果模型；快照细节复用 {@link SnapshotVisitor}/{@link ReportEntryVisitor}，
 * treeSnapshot 仅挂载根节点（整树 JSON，{@link RyzeResultSnapshotConverter#toSnapshot}）。
 * 静态 {@link #map(Result, int)} 供黄金/单测直接调用。
 */
@Component
public class RyzeResultMapper implements ResultMapper {

    private final int maxResponseBodyChars;

    public RyzeResultMapper(ApiTestProperties properties) {
        this.maxResponseBodyChars = properties.getDebug().getMaxResponseBodyChars();
    }

    @Override
    public MappedResult map(Result result) {
        return map(result, maxResponseBodyChars);
    }

    @Override
    public String status(Result result) {
        return RyzeResultAdapter.resolveStepStatus(result);
    }

    @Override
    public String error(Result result) {
        return result == null ? null : RyzeResultAdapter.errorMessage(result);
    }

    /** 整树投影静态入口（root 挂载 treeSnapshot），{@code maxChars} 传入拼接来自 ApiTestProperties 的截断阈值 */
    public static MappedResult map(Result result, int maxChars) {
        return map(result, maxChars, true);
    }

    private static MappedResult map(Result result, int maxChars, boolean root) {
        if (result == null) {
            return null;
        }
        boolean isSuite = result instanceof TestSuiteResult;
        boolean isSample = result instanceof SampleResult;
        String status = RyzeResultAdapter.resolveStepStatus(result);
        Long elapsed = ReportEntryVisitor.elapsedMillis(result.getStartTime(), result.getEndTime());
        Map<String, Object> request = null;
        Map<String, Object> response = null;
        Integer responseStatus = null;
        Map<String, Object> responseHeaders = null;
        String fullResponseBody = null;
        Integer responseSize = 0;
        List<Map<String, Object>> assertions = List.of();
        List<Map<String, Object>> extractors = List.of();
        Throwable error = result.getThrowable();
        if (isSample) {
            SampleResult sample = (SampleResult) result;
            if (sample.getThrowable() != null) {
                error = sample.getThrowable();
            }
            if (sample.getResponse() instanceof RealHTTPResponse raw) {
                responseStatus = raw.status();
                responseHeaders = SnapshotVisitor.toHeaderMap(raw.headers());
                fullResponseBody = SnapshotVisitor.bytesAsString(raw);
                responseSize = raw.bytes() == null ? 0 : raw.bytes().length;
            }
            request = SnapshotVisitor.requestSnapshot(sample.getRequest());
            response = SnapshotVisitor.responseSnapshot(sample, maxChars);
            assertions = SnapshotVisitor.assertionSnapshots(sample);
            extractors = SnapshotVisitor.extractorSnapshots(sample);
        }
        return new MappedResult(
                result.getId(),
                result.getTitle(),
                status,
                RyzeResultAdapter.errorMessage(error),
                rawThrowableMessage(error),
                result.getStartTime(),
                result.getEndTime(),
                elapsed,
                result.getMetadata(),
                isSuite,
                isSample,
                request,
                response,
                responseStatus,
                responseHeaders,
                fullResponseBody,
                responseSize,
                assertions,
                extractors,
                isSuite ? mapChildren(((TestSuiteResult) result).getChildren(), maxChars) : List.of(),
                mapChildren(result.getPreprocessors(), maxChars),
                mapChildren(result.getPostprocessors(), maxChars),
                root && isSuite ? RyzeResultSnapshotConverter.toSnapshot(result) : null);
    }

    private static List<MappedResult> mapChildren(List<? extends Result> nodes, int maxChars) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream().map(node -> map(node, maxChars, false)).toList();
    }

    /** 原始异常消息（不展开 ExceptionGroup；顶层构建/启动失败兜底用，等价 old ScheduledTaskRunner 的 message 或类简名） */
    private static String rawThrowableMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }
}