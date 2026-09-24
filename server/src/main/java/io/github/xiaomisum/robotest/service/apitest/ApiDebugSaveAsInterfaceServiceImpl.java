package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugSaveAsInterfaceReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiDebugRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.repository.apitest.ApiDebugRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 调试记录保存为接口实现（快速调试详细设计 4.3）
 */
@Service
public class ApiDebugSaveAsInterfaceServiceImpl implements ApiDebugSaveAsInterfaceService {

    @Resource
    private ApiDebugRecordMapper debugRecordMapper;
    @Resource
    private ApiInterfaceService interfaceService;
    @Resource
    private ApiInterfaceMapper interfaceMapper;
    @Resource
    private ApiEnvironmentMapper environmentMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Override
    public UUID saveAsInterface(UUID projectId, UUID workspaceId, UUID userId, UUID recordId,
                                ApiDebugSaveAsInterfaceReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiDebugRecord record = requireRecord(projectId, recordId);
        if (!record.getUserId().equals(userId)) {
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

    private void applyRequestSnapshot(ApiInterfaceCreateReqDTO target, ApiDebugRecord record,
                                      Map<String, Object> request) {
        String baseUrl = resolveBaseUrl(record.getProjectId(), record.getEnvironmentId());
        if (request == null || request.isEmpty()) {
            applySnapshot(target, record, baseUrl);
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

    private void applySnapshot(ApiInterfaceCreateReqDTO target, ApiDebugRecord record, String baseUrl) {
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
                : environmentMapper.findDefaultByProjectId(projectId);
        if (env == null || !env.getProjectId().equals(projectId)) {
            return "";
        }
        if (env.getHttpConfigs() == null || env.getHttpConfigs().isEmpty()) {
            return "";
        }
        Object baseUrl = env.getHttpConfigs().get(0).get("baseUrl");
        return baseUrl == null ? "" : baseUrl.toString();
    }

    private String extractPath(String url, String baseUrl) {
        String candidate = baseUrl != null && !baseUrl.isBlank() && url.startsWith(baseUrl)
                ? url.substring(baseUrl.length())
                : stripOrigin(url);
        int queryStart = candidate.indexOf('?');
        String path = queryStart >= 0 ? candidate.substring(0, queryStart) : candidate;
        return path.isEmpty() ? "/" : path;
    }

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

    private ApiDebugRecord requireRecord(UUID projectId, UUID id) {
        ApiDebugRecord record = debugRecordMapper.selectById(id);
        if (record == null || !projectId.equals(record.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DEBUG_RECORD_NOT_FOUND);
        }
        return record;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }
}
