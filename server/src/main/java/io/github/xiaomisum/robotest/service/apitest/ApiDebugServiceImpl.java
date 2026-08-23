package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugRenameReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugCurlImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugRecordItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugRestoreRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiDebugRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentHttp;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentProcessor;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentVariable;
import io.github.xiaomisum.robotest.repository.apitest.ApiDebugRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentHttpMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentProcessorMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentVariableMapper;
import io.github.xiaomisum.ryze.Ryze;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class ApiDebugServiceImpl implements ApiDebugService {

    private static final String TYPE_SENSITIVE = "sensitive";
    private static final String DEFAULT_HTTP_CONFIG = "默认 HTTP 配置";

    @Resource
    private ApiDebugRecordMapper debugRecordMapper;
    @Resource
    private ApiEnvironmentMapper environmentMapper;
    @Resource
    private ApiEnvironmentHttpMapper environmentHttpMapper;
    @Resource
    private ApiEnvironmentVariableMapper environmentVariableMapper;
    @Resource
    private ApiEnvironmentProcessorMapper environmentProcessorMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource(name = "apiTestExecutor")
    private ThreadPoolTaskExecutor apiTestExecutor;
    @Resource(name = "apiDebugPersistExecutor")
    private ThreadPoolTaskExecutor persistExecutor;
    @Resource
    private ApiTestProperties properties;

    @Value("${robotest.env.secret-key:}")
    private String secretKeyBase64;

    @Override
    public ApiDebugExecuteRespDTO execute(UUID projectId, UUID workspaceId, UUID userId,
            ApiDebugExecuteReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        if (reqDTO.getProtocol() != null && !"http".equalsIgnoreCase(reqDTO.getProtocol())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_FORMAT_CONVERT_FAILED, "V1.2 仅支持 http 协议");
        }
        DebugRyzeConverter.EnvSnapshot env = resolveEnvSnapshot(projectId, reqDTO.getEnvironmentId());
        Map<String, Object> suite = DebugRyzeConverter.buildSuite(env, reqDTO);

        int timeoutMs = reqDTO.getTimeoutMs() != null && reqDTO.getTimeoutMs() > 0
                ? reqDTO.getTimeoutMs()
                : properties.getDebug().getDefaultTimeoutMs();
        // 整体护栏 = 请求超时 + 引擎开销缓冲，防止取样器超时失效时线程无限等待
        long guardMs = timeoutMs + 5000L;

        ApiDebugRecord record = new ApiDebugRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(projectId);
        record.setUserId(userId);
        record.setProtocol("http");
        record.setMethod(reqDTO.getMethod().toUpperCase());
        record.setUrl(reqDTO.getUrl());
        record.setHeaders(safeList(reqDTO.getHeaders()));
        record.setBodyType(reqDTO.getBody() == null ? "none"
                : Objects.requireNonNullElse(reqDTO.getBody().getType(), "none"));
        record.setBody(flattenBody(reqDTO.getBody()));
        record.setQueryParams(safeList(reqDTO.getParams()));
        record.setProcessors(safeList(reqDTO.getProcessors()));
        record.setEnvironmentId(reqDTO.getEnvironmentId());
        record.setTimeoutMs(timeoutMs);
        record.setName(DebugRyzeConverter.autoName(record.getMethod(), reqDTO.getUrl()));
        record.setExecutedAt(LocalDateTime.now());

        TestResultSnapshot snapshot = runSuite(suite, guardMs);
        applyResult(record, snapshot);

        persistAsync(record);
        return ApiDebugExecuteRespDTO.builder()
                .debugRecordId(record.getId().toString())
                .status(record.getStatus())
                .responseStatus(record.getResponseStatus())
                .responseHeaders(record.getResponseHeaders())
                .responseBody(parseResponseBody(record.getResponseBody()))
                .durationMs(record.getDurationMs())
                .size(record.getResponseSize())
                .errorMessage(record.getErrorMessage())
                .build();
    }

    @Override
    public ApiDebugCurlImportRespDTO importCurl(UUID projectId, UUID workspaceId, UUID userId, String curl) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        CurlParser.ParsedCurl parsed;
        try {
            parsed = CurlParser.parse(curl);
        } catch (IllegalArgumentException ex) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_IMPORT_PARSE_FAILED, ex.getMessage());
        }
        Object content = parsed.bodyContent();
        String type = parsed.bodyType();
        if (content instanceof String && !"raw".equals(type) && !isJsonText((String) content)) {
            // 非 JSON 文本负载降级为 raw，与解析器降级规则一致
            type = "raw";
        }
        return ApiDebugCurlImportRespDTO.builder()
                .protocol("http")
                .method(parsed.method())
                .url(parsed.url())
                .headers(parsed.headers())
                .body(ApiDebugCurlImportRespDTO.Body.builder()
                        .type(type == null ? "none" : type)
                        .content(content)
                        .build())
                .params(List.of())
                .build();
    }

    @Override
    public PageResult<ApiDebugRecordItemRespDTO> pageRecords(UUID projectId, UUID workspaceId, UUID userId,
            String keyword, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        PageResult<ApiDebugRecord> page = debugRecordMapper.selectPage(projectId, userId, keyword, pageParam);
        List<ApiDebugRecordItemRespDTO> items = page.getList().stream().map(this::toListItem).toList();
        return new PageResult<>(items, page.getTotal());
    }

    @Override
    public void deleteRecord(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        requireRecord(projectId, id);
        debugRecordMapper.deleteById(id);
    }

    @Override
    public void renameRecord(UUID projectId, UUID workspaceId, UUID userId, UUID id,
            ApiDebugRenameReqDTO reqDTO) {
        requireRecord(projectId, id);
        ApiDebugRecord update = new ApiDebugRecord();
        update.setId(id);
        update.setName(reqDTO.getName());
        debugRecordMapper.updateById(update);
    }

    @Override
    public ApiDebugRestoreRespDTO restore(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        ApiDebugRecord record = requireRecord(projectId, id);
        return ApiDebugRestoreRespDTO.builder()
                .debugRecordId(record.getId().toString())
                .request(ApiDebugRestoreRespDTO.Snapshot.builder()
                        .protocol(record.getProtocol())
                        .method(record.getMethod())
                        .url(record.getUrl())
                        .headers(record.getHeaders())
                        .body(record.getBody())
                        .params(record.getQueryParams())
                        .build())
                .response(ApiDebugRestoreRespDTO.Response.builder()
                        .statusCode(record.getResponseStatus())
                        .headers(record.getResponseHeaders())
                        .body(parseResponseBody(record.getResponseBody()))
                        .elapsed(record.getDurationMs())
                        .size(record.getResponseSize())
                        .build())
                .createdAt(record.getCreatedAt())
                .build();
    }

    // ========== 执行 ==========

    private TestResultSnapshot runSuite(Map<String, Object> suite, long guardMs) {
        try {
            return apiTestExecutor.submit(() -> {
                var result = Ryze.start(suite);
                return collect(result);
            }).get(guardMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ex) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_EXECUTOR_BUSY);
        } catch (java.util.concurrent.TimeoutException ex) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_EXEC_TIMEOUT, guardMs + "ms");
        } catch (Exception ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            log.warn("[api-debug] 调试执行异常", ex);
            return new TestResultSnapshot(TestStatus.broken, null, null, null, 0,
                    ex.getMessage() == null ? "执行失败" : ex.getMessage(), 0L);
        }
    }

    private TestResultSnapshot collect(io.github.xiaomisum.ryze.Result suiteResult) {
        var children = suiteResult instanceof io.github.xiaomisum.ryze.testelement.TestSuiteResult suite
                ? suite.getChildren()
                : List.of();
        io.github.xiaomisum.ryze.testelement.sampler.SampleResult step = children.stream()
                .filter(child -> child instanceof io.github.xiaomisum.ryze.testelement.sampler.SampleResult)
                .map(child -> (io.github.xiaomisum.ryze.testelement.sampler.SampleResult) child)
                .findFirst()
                .orElse(null);

        Long elapsed = Duration.between(suiteResult.getStartTime(), suiteResult.getEndTime()).toMillis();
        if (step == null) {
            Throwable error = suiteResult.getThrowable();
            return new TestResultSnapshot(suiteResult.getStatus(), null, null, null, 0,
                    error == null ? "未产生执行结果" : error.getMessage(), elapsed);
        }
        Integer responseStatus = null;
        Map<String, Object> responseHeaders = null;
        String responseBody = null;
        int size = 0;
        if (step.getResponse() instanceof RealHTTPResponse response) {
            responseStatus = response.status();
            byte[] bytes = response.bytes();
            size = bytes == null ? 0 : bytes.length;
            responseBody = bytesAsString(response);
            responseHeaders = toHeaderMap(response.headers());
        }
        Throwable error = step.getThrowable() != null ? step.getThrowable() : suiteResult.getThrowable();
        return new TestResultSnapshot(step.getStatus(), responseStatus, responseHeaders, responseBody, size,
                error == null ? null : error.getMessage(), elapsed);
    }

    private String bytesAsString(RealHTTPResponse response) {
        try {
            return response.bytesAsString();
        } catch (Exception ex) {
            return response.format();
        }
    }

    private Map<String, Object> toHeaderMap(List<Header> headers) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Header header : headers) {
            String name = header.getName();
            String value = header.getValue();
            if (map.containsKey(name)) {
                map.put(name, map.get(name) + ", " + value);
            } else {
                map.put(name, value);
            }
        }
        return map;
    }

    private void applyResult(ApiDebugRecord record, TestResultSnapshot snapshot) {
        record.setStatus(mapStatus(snapshot.status()));
        record.setResponseStatus(snapshot.responseStatus());
        record.setResponseHeaders(snapshot.responseHeaders());
        record.setDurationMs(snapshot.elapsedMs() == null ? null
                : snapshot.elapsedMs().intValue());
        record.setErrorMessage(truncate(snapshot.errorMessage(), 2000));
        if (snapshot.responseBody() != null) {
            record.setResponseBody(truncate(snapshot.responseBody(),
                    properties.getDebug().getMaxResponseBodyChars()));
        }
        record.setResponseSize(sizeOrDerived(snapshot));
    }

    private Integer sizeOrDerived(TestResultSnapshot snapshot) {
        if (snapshot.responseSize() > 0) {
            return snapshot.responseSize();
        }
        return snapshot.responseBody() == null ? null : snapshot.responseBody().getBytes().length;
    }

    private String mapStatus(TestStatus status) {
        if (status == TestStatus.passed) {
            return "success";
        }
        return status == TestStatus.failed ? "failed" : "error";
    }

    // ========== 环境快照 ==========

    private DebugRyzeConverter.EnvSnapshot resolveEnvSnapshot(UUID projectId, UUID environmentId) {
        ApiEnvironment env = environmentId != null
                ? environmentMapper.selectById(environmentId)
                : findDefaultEnvironment(projectId);
        if (env == null || !env.getProjectId().equals(projectId)) {
            return DebugRyzeConverter.EnvSnapshot.empty();
        }
        ApiEnvironmentHttp defaultHttp = environmentHttpMapper.listByEnvironmentId(env.getId()).stream()
                .filter(http -> Boolean.TRUE.equals(http.getIsDefault()))
                .findFirst()
                .orElse(null);
        Map<String, Object> variables = new LinkedHashMap<>();
        for (ApiEnvironmentVariable variable : environmentVariableMapper.listByEnvironmentId(env.getId())) {
            variables.put(variable.getName(), plaintext(variable));
        }
        List<Map<String, Object>> pre = processorConfigs(env.getId(), "preprocessor");
        List<Map<String, Object>> post = processorConfigs(env.getId(), "postprocessor");

        Map<String, Object> envHeaders = new LinkedHashMap<>();
        if (defaultHttp != null && defaultHttp.getDefaultHeaders() != null) {
            for (Map<String, Object> entry : defaultHttp.getDefaultHeaders()) {
                Object key = entry.get("key");
                if (key != null && !Boolean.FALSE.equals(entry.get("enabled"))) {
                    envHeaders.put(key.toString(), entry.getOrDefault("value", ""));
                }
            }
        }
        return new DebugRyzeConverter.EnvSnapshot(
                defaultHttp == null ? "" : Objects.requireNonNullElse(defaultHttp.getBaseUrl(), ""),
                envHeaders, variables, pre, post);
    }

    private ApiEnvironment findDefaultEnvironment(UUID projectId) {
        return environmentMapper.selectList(
                        new LambdaQueryWrapperX<ApiEnvironment>()
                                .eq(ApiEnvironment::getProjectId, projectId)
                                .eq(ApiEnvironment::getIsDefault, true))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> processorConfigs(UUID envId, String processorType) {
        List<Map<String, Object>> configs = new ArrayList<>();
        for (ApiEnvironmentProcessor processor
                : environmentProcessorMapper.listByEnvironmentIdAndType(envId, processorType)) {
            if (Boolean.FALSE.equals(processor.getEnabled())) {
                continue;
            }
            configs.add(processor.getConfig());
        }
        return configs;
    }

    /** 变量明文：敏感变量解密后参与执行（执行需要真实值，区别于前端展示掩码） */
    private String plaintext(ApiEnvironmentVariable variable) {
        String value = variable.getValue();
        if (value == null || !TYPE_SENSITIVE.equals(variable.getType())) {
            return value;
        }
        try {
            byte[] key = SecretCryptoUtil.parseKey(secretKeyBase64);
            return key == null ? value : SecretCryptoUtil.decrypt(key, value);
        } catch (Exception ex) {
            log.warn("[api-debug] 敏感变量 {} 解密失败，按密文参与执行", variable.getName());
            return value;
        }
    }

    // ========== 记录持久化 ==========

    private void persistAsync(ApiDebugRecord record) {
        int limit = properties.getDebug().getRecordLimit();
        persistExecutor.execute(() -> {
            try {
                debugRecordMapper.insert(record);
                debugRecordMapper.trimToLimit(record.getProjectId(), record.getUserId(), limit);
            } catch (Exception ex) {
                log.warn("[api-debug] 调试记录自动保存失败 id={}", record.getId(), ex);
            }
        });
    }

    private ApiDebugRecord requireRecord(UUID projectId, UUID id) {
        ApiDebugRecord record = debugRecordMapper.selectById(id);
        if (record == null || !record.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DEBUG_RECORD_NOT_FOUND);
        }
        return record;
    }

    private ApiDebugRecordItemRespDTO toListItem(ApiDebugRecord record) {
        ApiDebugRecordItemRespDTO item = new ApiDebugRecordItemRespDTO();
        item.setId(record.getId());
        item.setName(record.getName());
        item.setMethod(record.getMethod());
        item.setUrl(record.getUrl());
        item.setStatus(record.getStatus());
        item.setResponseStatus(record.getResponseStatus());
        item.setDurationMs(record.getDurationMs());
        item.setExecutedAt(record.getExecutedAt());
        return item;
    }

    // ========== 杂项 ==========

    private Object parseResponseBody(String body) {
        if (body == null) {
            return null;
        }
        try {
            return JsonUtils.parseObject(body, Object.class);
        } catch (Exception ex) {
            return body;
        }
    }

    private boolean isJsonText(String text) {
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private Map<String, Object> flattenBody(ApiDebugExecuteReqDTO.Body body) {
        if (body == null || body.getContent() == null) {
            return null;
        }
        if (body.getContent() instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        return Map.of("content", body.getContent());
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    /** 取样器结果切片：状态/响应/耗时 */
    private record TestResultSnapshot(TestStatus status, Integer responseStatus,
            Map<String, Object> responseHeaders, String responseBody, int responseSize,
            String errorMessage, Long elapsedMs) {
    }
}
