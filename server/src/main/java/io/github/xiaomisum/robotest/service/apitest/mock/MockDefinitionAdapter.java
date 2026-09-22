package io.github.xiaomisum.robotest.service.apitest.mock;

import io.github.xiaomisum.robotest.framework.mock.MockDefinitionReader;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiMockAccessLog;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiMockDefinition;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiMockAccessLogMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiMockDefinitionMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MockDefinitionReader 适配器：将 framework/mock 的端口调用转译为业务层 Mapper 操作。
 */
@Component
public class MockDefinitionAdapter implements MockDefinitionReader {

    private final ApiMockDefinitionMapper mockMapper;
    private final ApiMockAccessLogMapper accessLogMapper;
    private final ApiInterfaceMapper interfaceMapper;

    public MockDefinitionAdapter(ApiMockDefinitionMapper mockMapper,
                                 ApiMockAccessLogMapper accessLogMapper,
                                 ApiInterfaceMapper interfaceMapper) {
        this.mockMapper = mockMapper;
        this.accessLogMapper = accessLogMapper;
        this.interfaceMapper = interfaceMapper;
    }

    @Override
    public List<MockDefinitionSnapshot> findEnabledForMatch(String method, String path) {
        return mockMapper.selectEnabledForMatch(method, path).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public List<MockDefinitionSnapshot> findEnabledWildcards(String method) {
        return mockMapper.selectEnabledWildcards(method).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public Map<String, Object> loadResponseExample(UUID interfaceId) {
        if (interfaceId == null) {
            return null;
        }
        ApiInterface apiInterface = interfaceMapper.selectById(interfaceId);
        return apiInterface == null ? null : apiInterface.getResponseExample();
    }

    @Override
    public void logAccessAsync(UUID mockId, UUID projectId, String method, String path,
                               Map<String, String> requestHeaders, String requestBody,
                               int status, String responseBody, int durationMs, String clientIp) {
        CompletableFuture.runAsync(() -> {
            try {
                ApiMockAccessLog log = new ApiMockAccessLog();
                log.setMockId(mockId);
                log.setProjectId(projectId);
                log.setMethod(method);
                log.setPath(path);
                log.setRequestHeaders(new LinkedHashMap<>(requestHeaders));
                log.setRequestBody(requestBody);
                log.setResponseStatus(status);
                log.setResponseBody(responseBody);
                log.setDurationMs(durationMs);
                log.setClientIp(clientIp);
                accessLogMapper.insert(log);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void incrementHitAsync(UUID mockId) {
        CompletableFuture.runAsync(() -> {
            try {
                mockMapper.incrementHit(mockId);
            } catch (Exception ignored) {
            }
        });
    }

    private MockDefinitionSnapshot toSnapshot(ApiMockDefinition entity) {
        return new MockDefinitionSnapshot(
                entity.getId(),
                entity.getProjectId(),
                entity.getInterfaceId(),
                entity.getMethod(),
                entity.getPath(),
                entity.getMatchRules(),
                entity.getDelayMs(),
                entity.getResponseStatus(),
                entity.getResponseHeaders(),
                entity.getResponseBody(),
                entity.getResponseBodyType(),
                entity.getFollowApi()
        );
    }
}
