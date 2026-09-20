package io.github.xiaomisum.robotest.service.apitest;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvironmentSnapshotProvider;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.SuiteRunner;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugRenameReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugRecordItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugRestoreRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiDebugRecord;
import io.github.xiaomisum.robotest.repository.apitest.ApiDebugRecordMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.DebugRyzeConverter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class ApiDebugServiceImpl implements ApiDebugService {

    @Resource
    private ApiDebugRecordMapper debugRecordMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private EnvironmentSnapshotProvider environmentSnapshotFactory;
    @Resource
    private SuiteRunner suiteRunner;
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
        EnvSnapshot env = resolveEnvSnapshot(projectId, reqDTO.getEnvironmentId());
        Map<String, Object> suite = DebugRyzeConverter.buildSuite(env, reqDTO);

        int timeoutMs = reqDTO.getTimeoutMs() != null && reqDTO.getTimeoutMs() > 0
                ? reqDTO.getTimeoutMs()
                : properties.getDebug().getDefaultTimeoutMs();
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
            functionRuntime.prepareSuite(suite, projectId);
            return apiTestExecutor.submit(() -> collect(suiteRunner.run(suite)))
                    .get(guardMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ex) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_EXECUTOR_BUSY);
        } catch (java.util.concurrent.TimeoutException ex) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_EXEC_TIMEOUT, guardMs + "ms");
        } catch (Exception ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            log.warn("[api-debug] 调试执行异常", ex);
            return new TestResultSnapshot("error", null, null, null, 0,
                    ex.getMessage() == null ? "执行失败" : ex.getMessage(), 0L);
        }
    }

    private TestResultSnapshot collect(MappedResult root) {
        MappedResult step = root.children() == null ? null : root.children().stream()
                .filter(MappedResult::isSample)
                .findFirst()
                .orElse(null);

        Long elapsed = root.elapsedMs() == null ? 0L : root.elapsedMs();
        if (step == null) {
            return new TestResultSnapshot(root.status(), null, null, null, 0,
                    root.errorMessage() == null ? "未产生执行结果" : root.errorMessage(), elapsed);
        }
        String error = step.errorMessage() != null ? step.errorMessage() : root.errorMessage();
        return new TestResultSnapshot(step.status(), step.responseStatus(), step.responseHeaders(),
                step.fullResponseBody(), step.responseSize() == null ? 0 : step.responseSize(),
                error, elapsed);
    }

    private void applyResult(ApiDebugRecord record, TestResultSnapshot snapshot) {
        record.setStatus(snapshot.status());
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

    // ========== 环境快照 ==========

    private EnvSnapshot resolveEnvSnapshot(UUID projectId, UUID environmentId) {
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

    private record TestResultSnapshot(String status, Integer responseStatus,
            Map<String, Object> responseHeaders, String responseBody, int responseSize,
            String errorMessage, Long elapsedMs) {
    }
}
