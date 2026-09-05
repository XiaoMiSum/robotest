package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugRenameReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugSaveAsInterfaceReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugRecordItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugRestoreRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugSaveAsInterfaceRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiDebugRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.repository.apitest.ApiDebugRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.ryze.Ryze;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.Header;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class ApiDebugServiceImpl implements ApiDebugService {

    private static final String DEFAULT_HTTP_CONFIG = "默认 HTTP 配置";

    @Resource
    private ApiDebugRecordMapper debugRecordMapper;
    @Resource
    private ApiEnvironmentMapper environmentMapper;
    @Resource
    private ApiInterfaceService interfaceService;
    @Resource
    private ApiInterfaceMapper interfaceMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private EnvironmentSnapshotFactory environmentSnapshotFactory;
    @Resource(name = "apiTestExecutor")
    private ThreadPoolTaskExecutor apiTestExecutor;
    @Resource(name = "apiDebugPersistExecutor")
    private ThreadPoolTaskExecutor persistExecutor;
    @Resource
    private ApiTestProperties properties;
    @Resource
    private CustomFunctionRuntime functionRuntime;

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

        TestResultSnapshot snapshot = runSuite(suite, guardMs, projectId);
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
                        .body(buildInterfaceBody(record))
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

    @Override
    public UUID saveAsInterface(UUID projectId, UUID workspaceId, UUID userId, UUID id,
            ApiDebugSaveAsInterfaceReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiDebugRecord record = requireRecord(projectId, id);
        if (!record.getUserId().equals(userId)) {
            // 调试记录按用户隔离（列表口径一致），非本人记录视同不存在，避免探测他人记录
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DEBUG_RECORD_NOT_FOUND);
        }
        return switch (reqDTO.getMode()) {
            case "create" -> saveAsNewInterface(projectId, workspaceId, userId, record, reqDTO);
            case "attach" -> attachToInterface(projectId, workspaceId, userId, record, reqDTO);
            default -> throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        };
    }

    private UUID saveAsNewInterface(UUID projectId, UUID workspaceId, UUID userId, ApiDebugRecord record,
            ApiDebugSaveAsInterfaceReqDTO reqDTO) {
        if (reqDTO.getName() == null || reqDTO.getName().isBlank() || reqDTO.getModuleId() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        ApiInterfaceCreateReqDTO create = new ApiInterfaceCreateReqDTO();
        applyRequestSnapshot(create, record, reqDTO.getRequest());
        create.setName(reqDTO.getName().trim());
        create.setModuleId(reqDTO.getModuleId());
        create.setResponseExample(reqDTO.getResponseExample());
        return interfaceService.create(projectId, workspaceId, userId, create);
    }

    private UUID attachToInterface(UUID projectId, UUID workspaceId, UUID userId, ApiDebugRecord record,
            ApiDebugSaveAsInterfaceReqDTO reqDTO) {
        if (reqDTO.getInterfaceId() == null || reqDTO.getChangeVersion() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        // 名称与模块取自目标接口：update 载体仅覆盖主请求字段，元数据原样保留
        ApiInterface target = interfaceMapper.selectById(reqDTO.getInterfaceId());
        if (target == null || !projectId.equals(target.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_INTERFACE_NOT_FOUND);
        }
        ApiInterfaceUpdateReqDTO update = new ApiInterfaceUpdateReqDTO();
        update.setChangeVersion(reqDTO.getChangeVersion());
        update.setName(target.getName());
        update.setModuleId(target.getModuleId());
        applyRequestSnapshot(update, record, reqDTO.getRequest());
        update.setResponseExample(reqDTO.getResponseExample());
        interfaceService.update(projectId, workspaceId, userId, target.getId(), update);
        return target.getId();
    }

    /**
     * 从 UI 表单构建的请求快照映射为接口定义请求字段（取代原 applySnapshot(record)）。
     * request 为 null 时降级到旧 debug record 行为，保持向前兼容。
     */
    private void applyRequestSnapshot(ApiInterfaceCreateReqDTO target, ApiDebugRecord record,
            Map<String, Object> request) {
        String baseUrl = resolveBaseUrl(record.getProjectId(), record.getEnvironmentId());
        if (request == null || request.isEmpty()) {
            applySnapshot(target, record);
            return;
        }
        String method = Objects.toString(request.get("method"), record.getMethod());
        String url = Objects.toString(request.get("url"), record.getUrl());
        List<Map<String, Object>> headers = safeCastList(request.get("headers"));
        List<Map<String, Object>> params = safeCastList(request.get("params"));
        Map<String, Object> body = request.get("body") instanceof Map<?, ?> b ? castMap(b) : null;
        Map<String, Object> auth = request.get("auth") instanceof Map<?, ?> a ? castMap(a) : null;
        target.setProtocol("http");
        target.setMethod(method);
        target.setPath(extractPath(url, baseUrl));
        target.setHeaders(headers);
        target.setBody(body);
        target.setParams(mergeQueryParams(url, params));
        target.setAuth(auth);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<Map<String, Object>> safeCastList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(e -> (Map<String, Object>) e)
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /** 调试快照 → 接口定义请求字段映射（快速调试详细设计 4.3） */
    private void applySnapshot(ApiInterfaceCreateReqDTO target, ApiDebugRecord record) {
        String baseUrl = resolveBaseUrl(record.getProjectId(), record.getEnvironmentId());
        target.setProtocol("http");
        target.setMethod(record.getMethod());
        target.setPath(extractPath(record.getUrl(), baseUrl));
        target.setHeaders(safeList(record.getHeaders()));
        target.setBody(buildInterfaceBody(record));
        target.setParams(mergeQueryParams(record.getUrl(), safeList(record.getQueryParams())));
    }

    private String resolveBaseUrl(UUID projectId, UUID environmentId) {
        ApiEnvironment env = environmentId != null
                ? environmentMapper.selectById(environmentId)
                : environmentMapper.selectList(new LambdaQueryWrapperX<ApiEnvironment>()
                        .eq(ApiEnvironment::getProjectId, projectId)
                        .eq(ApiEnvironment::getIsDefault, true))
                .stream().findFirst().orElse(null);
        if (env == null || !env.getProjectId().equals(projectId)) {
            return "";
        }
        if (env.getHttpConfigs() == null || env.getHttpConfigs().isEmpty()) {
            return "";
        }
        Object baseUrl = env.getHttpConfigs().get(0).get("baseUrl");
        return baseUrl == null ? "" : baseUrl.toString();
    }

    /** 剥离环境 baseUrl 得相对路径；无法剥离时仅截取 URL 路径部分（接口 path 不含域名/端口，接口管理详细设计 2.1.1） */
    private String extractPath(String url, String baseUrl) {
        String candidate = baseUrl != null && !baseUrl.isBlank() && url.startsWith(baseUrl)
                ? url.substring(baseUrl.length())
                : stripOrigin(url);
        int queryStart = candidate.indexOf('?');
        String path = queryStart >= 0 ? candidate.substring(0, queryStart) : candidate;
        return path.isEmpty() ? "/" : path;
    }

    /** 环境 baseUrl 无法剥离时，仅保留 URL 的 path 部分，剔除 scheme/host/port（query 另行并入 params） */
    private String stripOrigin(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getRawPath() != null) {
                return uri.getRawPath();
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    /** URL query 与已配置参数合并去重，同名参数以已配置值为准 */
    private List<Map<String, Object>> mergeQueryParams(String url, List<Map<String, Object>> recorded) {
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> item : recorded) {
            Object key = item.get("key");
            if (key != null) {
                seen.add(key.toString());
            }
        }
        List<Map<String, Object>> merged = new ArrayList<>();
        int queryStart = url.indexOf('?');
        if (queryStart >= 0) {
            for (String pair : url.substring(queryStart + 1).split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String key = decodeQueryParam(eq < 0 ? pair : pair.substring(0, eq));
                if (!seen.add(key)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", key);
                item.put("value", decodeQueryParam(eq < 0 ? "" : pair.substring(eq + 1)));
                item.put("enabled", true);
                merged.add(item);
            }
        }
        merged.addAll(recorded);
        return merged;
    }

    private String decodeQueryParam(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    /** 接口 body 列约定为 {type, content} 结构；调试记录落库时 content 已扁平化，此处还原包装 */
    private Map<String, Object> buildInterfaceBody(ApiDebugRecord record) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", Objects.requireNonNullElse(record.getBodyType(), "none"));
        Map<String, Object> flat = record.getBody();
        if (flat != null && !flat.isEmpty()) {
            body.put("content", flat.size() == 1 && flat.containsKey("content")
                    ? flat.get("content") : flat);
        }
        return body;
    }

    // ========== 执行 ==========

    private TestResultSnapshot runSuite(Map<String, Object> suite, long guardMs, UUID projectId) {
        try {
            // 执行前注入自定义函数：重写调用名并标记项目上下文
            functionRuntime.prepareSuite(suite, projectId);
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
        return environmentSnapshotFactory.resolve(projectId, environmentId);
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
