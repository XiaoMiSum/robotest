package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiParsedImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportMapping;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceChangeLog;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportMappingMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceChangeLogMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.service.apitest.imports.ImportedOperation;
import io.github.xiaomisum.robotest.service.apitest.imports.ImportSourceFetcher;
import io.github.xiaomisum.robotest.service.apitest.imports.InterfaceImportParser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.*;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.*;
import static xyz.migoo.framework.common.exception.ServiceExceptionUtil.get;

/**
 * 接口导入实现（详细设计 4.1）
 */
@Service
public class ApiInterfaceImportServiceImpl implements ApiInterfaceImportService {

    private final List<InterfaceImportParser> parsers = List.of(
            new io.github.xiaomisum.robotest.service.apitest.imports.SwaggerImportParser());

    @Resource
    private ApiInterfaceMapper interfaceMapper;
    @Resource
    private ApiInterfaceChangeLogMapper changeLogMapper;
    @Resource
    private ApiImportMappingMapper importMappingMapper;
    @Resource
    private ApiImportRecordMapper importRecordMapper;
    @Resource
    private ImportSourceFetcher sourceFetcher;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiImportResultRespDTO importParsed(UUID projectId, UUID userId, ApiParsedImportReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        List<ImportedOperation> operations = reqDTO.getOperations().stream()
                .map(this::toImportedOperation)
                .toList();
        return doImport(projectId, userId, "curl", "cURL 粘贴导入", operations);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiImportResultRespDTO importUrl(UUID projectId, UUID userId, String url, String formatHint) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        String content = sourceFetcher.fetch(url);
        InterfaceImportParser parser = resolveParser(formatHint, content);
        List<ImportedOperation> operations = parseSafely(parser, content);
        return doImport(projectId, userId, "url", url, operations);
    }

    @Override
    public ApiImportPreviewRespDTO preview(UUID projectId, UUID userId, String url, String formatHint) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        String content = sourceFetcher.fetch(url);
        InterfaceImportParser parser = resolveParser(formatHint, content);
        List<ImportedOperation> operations = parseSafely(parser, content);
        List<ApiImportPreviewRespDTO.PreviewItem> items = new ArrayList<>();
        int toCreate = 0;
        int toUpdate = 0;
        int toSkip = 0;
        for (ImportedOperation operation : operations) {
            boolean exists = operation.getMethod() != null && operation.getPath() != null
                    && interfaceMapper.selectByPathAndMethod(projectId, operation.getMethod(), operation.getPath()) != null;
            String action = operation.getMethod() == null || operation.getPath() == null ? "skip"
                    : exists ? "update" : "create";
            switch (action) {
                case "create" -> toCreate += 1;
                case "update" -> toUpdate += 1;
                default -> toSkip += 1;
            }
            items.add(ApiImportPreviewRespDTO.PreviewItem.builder()
                    .name(operation.getSourceName())
                    .method(operation.getMethod())
                    .path(operation.getPath())
                    .action(action)
                    .conflict(exists)
                    .build());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toCreate", toCreate);
        summary.put("toUpdate", toUpdate);
        summary.put("toSkip", toSkip);
        return ApiImportPreviewRespDTO.builder().items(items).summary(summary).build();
    }

    // ==================== 内部方法 ====================

    private ApiImportResultRespDTO doImport(UUID projectId, UUID userId, String importType,
                                            String sourceName, List<ImportedOperation> operations) {
        int created = 0;
        int updated = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<PendingMapping> mappings = new ArrayList<>();
        for (ImportedOperation operation : operations) {
            try {
                UpsertResult result = upsertOperation(projectId, userId, sourceTypeOf(importType), operation);
                mappings.add(new PendingMapping(operation, result.targetId(), result.action()));
                if ("updated".equals(result.action())) {
                    updated += 1;
                } else {
                    created += 1;
                }
            } catch (ServiceException exception) {
                errors.add(Map.of("source", operation.getSourceName() == null ? "" : operation.getSourceName(),
                        "message", exception.getMessage() == null ? "" : exception.getMessage()));
            }
        }
        ApiImportRecord record = new ApiImportRecord();
        record.setProjectId(projectId);
        record.setImportType(importType);
        record.setSourceName(sourceName);
        record.setStatus(errors.isEmpty() ? "success" : created + updated > 0 ? "partial" : "failed");
        record.setSummary(importSummary(created, updated, errors.size()));
        record.setErrorDetails(errors);
        record.setCreatedBy(userId);
        importRecordMapper.insert(record);
        for (PendingMapping pending : mappings) {
            ApiImportMapping mapping = new ApiImportMapping();
            mapping.setProjectId(projectId);
            mapping.setImportRecordId(record.getId());
            mapping.setSourceType(sourceTypeOf(importType));
            mapping.setSourceId(pending.operation().getSourceId());
            mapping.setSourceName(pending.operation().getSourceName());
            mapping.setTargetType("interface");
            mapping.setTargetId(pending.targetId());
            mapping.setAction(pending.action());
            importMappingMapper.insert(mapping);
        }
        return ApiImportResultRespDTO.builder()
                .importHistoryId(record.getId())
                .summary(record.getSummary())
                .errors(errors)
                .build();
    }

    private UpsertResult upsertOperation(UUID projectId, UUID userId, String sourceType, ImportedOperation operation) {
        ApiImportMapping mapping = importMappingMapper.selectBySource(projectId, sourceType, operation.getSourceId());
        ApiInterface existing = mapping != null
                ? interfaceMapper.selectById(mapping.getTargetId())
                : interfaceMapper.selectByPathAndMethod(projectId, operation.getMethod(), operation.getPath());
        if (existing != null) {
            ApiInterface update = new ApiInterface();
            update.setId(existing.getId());
            update.setMethod(operation.getMethod());
            update.setPath(operation.getPath());
            update.setDescription(operation.getDescription());
            update.setHeaders(operation.getHeaders());
            update.setQueryParams(operation.getQueryParams());
            if (operation.getBody() != null) {
                update.setBodyType(String.valueOf(operation.getBody().get("type")));
                update.setBody(operation.getBody());
            }
            update.setChangeVersion((existing.getChangeVersion() == null ? 1 : existing.getChangeVersion()) + 1);
            interfaceMapper.updateById(update);
            writeChangeLog(existing.getId(), update.getChangeVersion(), "import", "导入覆盖更新", userId);
            return new UpsertResult(existing.getId(), "updated");
        }
        ApiInterface entity = new ApiInterface();
        entity.setProjectId(projectId);
        entity.setName(uniqueName(projectId, operation.getSourceName(), operation.getMethod(), operation.getPath()));
        entity.setProtocol("http");
        entity.setMethod(operation.getMethod());
        entity.setPath(operation.getPath());
        entity.setDescription(operation.getDescription());
        entity.setHeaders(operation.getHeaders());
        entity.setQueryParams(operation.getQueryParams());
        if (operation.getBody() != null) {
            entity.setBodyType(String.valueOf(operation.getBody().get("type")));
            entity.setBody(operation.getBody());
        }
        entity.setStatus("enabled");
        entity.setCreatedBy(userId);
        entity.setChangeVersion(1);
        entity.setReferenceCount(0);
        interfaceMapper.insert(entity);
        writeChangeLog(entity.getId(), 1, "import", "导入创建", userId);
        return new UpsertResult(entity.getId(), "created");
    }

    private void writeChangeLog(UUID interfaceId, int version, String action, String summary, UUID operatorId) {
        ApiInterfaceChangeLog log = new ApiInterfaceChangeLog();
        log.setInterfaceId(interfaceId);
        log.setChangeVersion(version);
        log.setAction(action);
        log.setSummary(summary);
        log.setOperatorId(operatorId);
        changeLogMapper.insert(log);
    }

    private String sourceTypeOf(String importType) {
        return "curl".equals(importType) ? "curl_operation" : "swagger_operation";
    }

    private ImportedOperation toImportedOperation(ApiParsedImportReqDTO.Operation operation) {
        return ImportedOperation.builder()
                .sourceId(operation.getMethod() + ":" + operation.getPath())
                .sourceName(operation.getName())
                .method(operation.getMethod())
                .path(operation.getPath())
                .description(operation.getDescription())
                .headers(operation.getHeaders())
                .queryParams(operation.getQueryParams())
                .body(operation.getBody())
                .build();
    }

    private Map<String, Object> importSummary(int created, int updated, int failed) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("failed", failed);
        return summary;
    }

    private InterfaceImportParser resolveParser(String formatHint, String content) {
        return parsers.stream()
                .filter(parser -> parser.supports(formatHint, content))
                .findFirst()
                .orElseThrow(() -> get(API_IMPORT_FORMAT_UNSUPPORTED,
                        formatHint == null ? "自动识别失败" : formatHint));
    }

    private List<ImportedOperation> parseSafely(InterfaceImportParser parser, String text) {
        try {
            return parser.parse(text);
        } catch (IllegalArgumentException exception) {
            throw get(API_IMPORT_PARSE_FAILED, exception.getMessage());
        }
    }

    private String uniqueName(UUID projectId, String name, String method, String path) {
        String base = name == null || name.isBlank() ? method + " " + path : name;
        String candidate = base;
        int suffix = 2;
        while (interfaceMapper.selectByNameAndModule(projectId, null, candidate) != null) {
            candidate = base + " (" + suffix++ + ")";
        }
        return candidate;
    }

    private record UpsertResult(UUID targetId, String action) {
    }

    private record PendingMapping(ImportedOperation operation, UUID targetId, String action) {
    }
}
