package io.github.xiaomisum.robotest.framework.mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mock 定义读取端口（反腐层）。
 * framework/mock 仅依赖此接口，不直接持有业务层 Mapper/Entity。
 * 适配器实现在 service/apitest/mock 包内。
 */
public interface MockDefinitionReader {

    /** 精确匹配：按 method + path 查询已启用的 Mock 定义 */
    List<MockDefinitionSnapshot> findEnabledForMatch(String method, String path);

    /** 通配符匹配：按 method 查询 path 为 '*' 的已启用 Mock 定义 */
    List<MockDefinitionSnapshot> findEnabledWildcards(String method);

    /** 加载接口响应示例（仅取 responseExample 字段） */
    Map<String, Object> loadResponseExample(UUID interfaceId);

    /** 异步记录访问日志 */
    void logAccessAsync(UUID mockId, UUID projectId, String method, String path,
                        Map<String, String> requestHeaders, String requestBody,
                        int status, String responseBody, int durationMs, String clientIp);

    /** 异步递增命中计数 */
    void incrementHitAsync(UUID mockId);

    /**
     * Mock 定义快照（filter 所需的最小字段集）。
     * 匹配字段 + 响应构建字段 + 日志字段，避免 framework 层引用业务实体。
     */
    record MockDefinitionSnapshot(
            UUID id,
            UUID projectId,
            UUID interfaceId,
            String method,
            String path,
            List<Map<String, Object>> matchRules,
            Integer delayMs,
            Integer responseStatus,
            Map<String, Object> responseHeaders,
            String responseBody,
            String responseBodyType,
            Boolean followApi
    ) {}
}
