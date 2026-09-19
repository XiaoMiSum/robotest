package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 引擎结果树的平台投影（端口自定模型，零 ryze）：编排层唯一允许接触的结果形态。
 * <p>
 * 由 {@code adapters/ryze/ResultMapper} 在防腐层内从 Ryze 结果树投影生成，逐节点携带
 * 平台状态/错误、起止时间、元数据、子节点与前后置处理器，采样叶另带报告所需的
 * request/response/断言/提取器快照（响应体已按配置截断）；根节点挂载整树 JSON 快照
 * （{@code treeSnapshot}，供报告 ryze_snapshot 落库）。
 */
public record MappedResult(String id,
        String title,
        String status,
        String errorMessage,
        String throwableMessage,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long elapsedMs,
        Map<String, Object> metadata,
        boolean suite,
        boolean sample,
        Map<String, Object> request,
        Map<String, Object> response,
        Integer responseStatus,
        Map<String, Object> responseHeaders,
        String fullResponseBody,
        List<Map<String, Object>> assertions,
        List<Map<String, Object>> extractors,
        List<MappedResult> children,
        List<MappedResult> preprocessors,
        List<MappedResult> postprocessors,
        Map<String, Object> treeSnapshot) {

    public boolean isSuite() {
        return suite;
    }

    public boolean isSample() {
        return sample;
    }
}