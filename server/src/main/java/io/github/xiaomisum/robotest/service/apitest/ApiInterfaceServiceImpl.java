package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchMoveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceStepReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceStepSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceVariablesReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportMapping;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceChangeLog;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceFollow;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceStep;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceVariable;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceChangeLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceReferenceRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceStepRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceVariableRespDTO;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportMappingMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceChangeLogMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceFollowMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceStepMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceVariableMapper;
import io.github.xiaomisum.robotest.service.apitest.imports.ImportedOperation;
import io.github.xiaomisum.robotest.service.apitest.imports.ImportSourceFetcher;
import io.github.xiaomisum.robotest.service.apitest.imports.InterfaceImportParser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_FORMAT_CONVERT_FAILED;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_IMPORT_FORMAT_UNSUPPORTED;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_IMPORT_PARSE_FAILED;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_INTERFACE_NAME_EXISTS;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_INTERFACE_NOT_FOUND;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_INTERFACE_REFERENCED;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_INTERFACE_VERSION_CONFLICT;
import static xyz.migoo.framework.common.exception.ServiceExceptionUtil.get;

/**
 * 接口定义管理实现（接口管理详细设计 3.1–3.4）
 *
 * <p>V1.2 边界（详细设计 6.3）：仅 http 协议；步骤提取器/断言透传存储；
 * 引用计数由场景/Mock 模块维护，当前恒为 0，删除保护逻辑已就位。
 */
@Service
public class ApiInterfaceServiceImpl implements ApiInterfaceService {

    /** 解析策略注册表：按 format 提示与内容嗅探选择实现（3.4.1） */
    private final List<InterfaceImportParser> parsers = List.of(
            new io.github.xiaomisum.robotest.service.apitest.imports.SwaggerImportParser(),
            new io.github.xiaomisum.robotest.service.apitest.imports.PostmanImportParser(),
            new io.github.xiaomisum.robotest.service.apitest.imports.HarImportParser(),
            new io.github.xiaomisum.robotest.service.apitest.imports.JmeterImportParser());

    @Resource
    private ApiInterfaceMapper interfaceMapper;
    @Resource
    private ApiInterfaceStepMapper stepMapper;
    @Resource
    private ApiInterfaceVariableMapper variableMapper;
    @Resource
    private ApiInterfaceFollowMapper followMapper;
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

    // ==================== 3.1 接口定义 ====================

    @Override
    public PageResult<ApiInterfaceItemRespDTO> page(UUID projectId, UUID workspaceId, UUID userId,
                                                    UUID moduleId, String search, String status, String view,
                                                    PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        List<UUID> followIds = null;
        UUID createdBy = null;
        if ("followed".equals(view)) {
            // followed 视图先解析关注集合，空集合直接返回空页避免 IN () 全表语义
            followIds = convertFollowIds(userId);
            if (followIds.isEmpty()) {
                return PageResult.empty();
            }
        } else if ("created".equals(view)) {
            createdBy = userId;
        }
        Set<UUID> followedSet = convertFollowIds(userId).stream().collect(Collectors.toSet());
        PageResult<ApiInterface> page = interfaceMapper.selectPage(projectId, moduleId, search, status,
                followIds, createdBy, pageParam);
        return new PageResult<>(page.getList().stream()
                .map(item -> toItem(item, followedSet.contains(item.getId())))
                .toList(), page.getTotal());
    }

    @Override
    public ApiInterfaceDetailRespDTO getDetail(UUID projectId, UUID interfaceId, UUID userId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        ApiInterface entity = requireInterface(projectId, interfaceId);
        boolean followed = followMapper.selectByInterfaceAndUser(interfaceId, userId) != null;
        List<ApiInterfaceStepRespDTO> steps = stepMapper.selectListByInterfaceId(interfaceId).stream()
                .map(this::toStep).toList();
        return ApiInterfaceDetailRespDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .protocol(entity.getProtocol())
                .method(entity.getMethod())
                .path(entity.getPath())
                .description(entity.getDescription())
                .moduleId(entity.getModuleId())
                .headers(entity.getHeaders())
                .body(entity.getBody())
                .params(entity.getQueryParams())
                .restParams(entity.getRestParams())
                .auth(entity.getAuth())
                .status(entity.getStatus())
                .changeVersion(entity.getChangeVersion())
                .responseExample(entity.getResponseExample())
                .referenceCount(entity.getReferenceCount())
                .followed(followed)
                .steps(steps)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID create(UUID projectId, UUID workspaceId, UUID userId, ApiInterfaceCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        validateProtocol(reqDTO.getProtocol());
        assertNameAvailable(projectId, reqDTO.getModuleId(), reqDTO.getName(), null);
        ApiInterface entity = new ApiInterface();
        applyRequest(entity, reqDTO);
        entity.setProjectId(projectId);
        entity.setCreatedBy(userId);
        entity.setChangeVersion(1);
        entity.setReferenceCount(0);
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("enabled");
        }
        interfaceMapper.insert(entity);
        writeChangeLog(entity.getId(), 1, "create", "创建接口", userId);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID projectId, UUID workspaceId, UUID userId, UUID id, ApiInterfaceUpdateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        ApiInterface current = requireInterface(projectId, id);
        if (!Objects.equals(current.getChangeVersion(), reqDTO.getChangeVersion())) {
            throw get(API_INTERFACE_VERSION_CONFLICT);
        }
        validateProtocol(reqDTO.getProtocol());
        assertNameAvailable(projectId, reqDTO.getModuleId(), reqDTO.getName(), id);

        int nextVersion = current.getChangeVersion() + 1;
        // C9：查询仅做校验，更新载体只携带本次变更字段
        ApiInterface update = new ApiInterface();
        update.setId(id);
        applyRequest(update, reqDTO);
        update.setChangeVersion(nextVersion);
        interfaceMapper.updateById(update);
        writeChangeLog(id, nextVersion, "update", diffSummary(current, reqDTO), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        ApiInterface entity = requireInterface(projectId, id);
        if (entity.getReferenceCount() != null && entity.getReferenceCount() > 0) {
            throw get(API_INTERFACE_REFERENCED);
        }
        interfaceMapper.deleteById(id);
        stepMapper.deleteByInterfaceId(id);
        variableMapper.deleteByInterfaceId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID copy(UUID projectId, UUID userId, UUID id, String copyName) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        ApiInterface source = requireInterface(projectId, id);
        String name = copyName == null || copyName.isBlank() ? source.getName() + "（副本）" : copyName;
        assertNameAvailable(projectId, source.getModuleId(), name, null);
        ApiInterface copy = new ApiInterface();
        copy.setProjectId(projectId);
        copy.setModuleId(source.getModuleId());
        copy.setName(name);
        copy.setProtocol(source.getProtocol());
        copy.setMethod(source.getMethod());
        copy.setPath(source.getPath());
        copy.setDescription(source.getDescription());
        copy.setHeaders(cloneList(source.getHeaders()));
        copy.setBodyType(source.getBodyType());
        copy.setBody(cloneMap(source.getBody()));
        copy.setQueryParams(cloneList(source.getQueryParams()));
        copy.setRestParams(cloneList(source.getRestParams()));
        copy.setAuth(cloneMap(source.getAuth()));
        copy.setStatus("enabled");
        copy.setResponseExample(cloneMap(source.getResponseExample()));
        copy.setCreatedBy(userId);
        copy.setChangeVersion(1);
        copy.setReferenceCount(0);
        interfaceMapper.insert(copy);
        // 公共步骤复制为独立副本，与原接口无关联（详细设计 3.1.6）
        int sort = 0;
        for (ApiInterfaceStep step : stepMapper.selectListByInterfaceId(id)) {
            ApiInterfaceStep copied = new ApiInterfaceStep();
            copied.setInterfaceId(copy.getId());
            copied.setName(step.getName());
            copied.setStepType(step.getStepType());
            copied.setSortOrder(sort++);
            copied.setEnabled(step.getEnabled());
            copied.setRequestConfig(step.getRequestConfig());
            copied.setProcessors(step.getProcessors());
            copied.setValidators(step.getValidators());
            copied.setExtractors(step.getExtractors());
            stepMapper.insert(copied);
        }
        writeChangeLog(copy.getId(), 1, "copy", "复制自接口 " + source.getName(), userId);
        return copy.getId();
    }

    @Override
    public ApiInterfaceReferenceRespDTO references(UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, id);
        // 场景/Mock 模块未上线，恒为空列表（详细设计 3.1.7）
        return ApiInterfaceReferenceRespDTO.builder()
                .scenes(List.of()).mocks(List.of()).build();
    }

    @Override
    public List<ApiInterfaceReferenceRespDTO.RefItem> referenceScenes(UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, id);
        return List.of();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMove(UUID projectId, UUID userId, ApiInterfaceBatchMoveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        for (UUID id : reqDTO.getIds()) {
            requireInterface(projectId, id);
            ApiInterface update = new ApiInterface();
            update.setId(id);
            update.setModuleId(reqDTO.getModuleId());
            interfaceMapper.updateById(update);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(UUID projectId, UUID userId, ApiInterfaceBatchDeleteReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        // 整体拒绝语义：任一被引用则全部不删（详细设计 3.1.10）
        for (UUID id : reqDTO.getIds()) {
            ApiInterface entity = requireInterface(projectId, id);
            if (entity.getReferenceCount() != null && entity.getReferenceCount() > 0) {
                throw get(API_INTERFACE_REFERENCED);
            }
        }
        reqDTO.getIds().forEach(id -> delete(projectId, userId, id));
    }

    @Override
    public void updateStatus(UUID projectId, UUID userId, UUID id, ApiInterfaceStatusReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, id);
        ApiInterface update = new ApiInterface();
        update.setId(id);
        update.setStatus(reqDTO.getStatus());
        interfaceMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, id);
        if (followMapper.selectByInterfaceAndUser(id, userId) == null) {
            ApiInterfaceFollow row = new ApiInterfaceFollow();
            row.setInterfaceId(id);
            row.setUserId(userId);
            followMapper.insert(row);
        }
    }

    @Override
    public void unfollow(UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, id);
        ApiInterfaceFollow row = followMapper.selectByInterfaceAndUser(id, userId);
        if (row != null) {
            followMapper.deleteById(row.getId());
        }
    }

    // ==================== 3.2 公共步骤 ====================

    @Override
    public UUID createStep(UUID projectId, UUID userId, UUID interfaceId, ApiInterfaceStepReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, interfaceId);
        ApiInterfaceStep step = new ApiInterfaceStep();
        step.setInterfaceId(interfaceId);
        applyStepFields(step, reqDTO);
        stepMapper.insert(step);
        return step.getId();
    }

    @Override
    public void updateStep(UUID projectId, UUID userId, UUID interfaceId, UUID stepId, ApiInterfaceStepReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, interfaceId);
        requireStep(interfaceId, stepId);
        ApiInterfaceStep update = new ApiInterfaceStep();
        update.setId(stepId);
        applyStepFields(update, reqDTO);
        stepMapper.updateById(update);
    }

    @Override
    public void deleteStep(UUID projectId, UUID userId, UUID interfaceId, UUID stepId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, interfaceId);
        requireStep(interfaceId, stepId);
        // 链接引用保护由场景模块维护引用计数后生效，当前无场景引用（V1.2 边界）
        stepMapper.deleteById(stepId);
    }

    @Override
    public void sortStep(UUID projectId, UUID userId, UUID interfaceId, UUID stepId, ApiInterfaceStepSortReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, interfaceId);
        requireStep(interfaceId, stepId);
        ApiInterfaceStep update = new ApiInterfaceStep();
        update.setId(stepId);
        update.setSortOrder(reqDTO.getSortOrder());
        stepMapper.updateById(update);
    }

    // ==================== 3.3 接口级变量 ====================

    @Override
    public List<ApiInterfaceVariableRespDTO> listVariables(UUID projectId, UUID userId, UUID interfaceId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, interfaceId);
        return variableMapper.selectListByInterfaceId(interfaceId).stream()
                .map(this::toVariable).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVariables(UUID projectId, UUID userId, UUID interfaceId, ApiInterfaceVariablesReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, interfaceId);
        // 全量覆盖语义：按 name 匹配更新，请求未包含的删除（详细设计 3.3.2）
        Map<String, ApiInterfaceVariable> byName = variableMapper.selectListByInterfaceId(interfaceId).stream()
                .collect(Collectors.toMap(ApiInterfaceVariable::getName, v -> v));
        List<String> keepNames = new ArrayList<>();
        List<ApiInterfaceVariablesReqDTO.VariableItem> items = reqDTO.getVariables() == null
                ? List.of() : reqDTO.getVariables();
        int sort = 0;
        for (ApiInterfaceVariablesReqDTO.VariableItem item : items) {
            keepNames.add(item.getName());
            ApiInterfaceVariable existing = byName.get(item.getName());
            ApiInterfaceVariable target = existing == null ? new ApiInterfaceVariable() : null;
            ApiInterfaceVariable payload = target != null ? target : new ApiInterfaceVariable();
            payload.setId(existing == null ? null : existing.getId());
            fillVariable(payload, item, sort++);
            if (target != null) {
                target.setInterfaceId(interfaceId);
                variableMapper.insert(target);
            } else {
                variableMapper.updateById(payload);
            }
        }
        variableMapper.selectListByInterfaceId(interfaceId).stream()
                .filter(v -> !keepNames.contains(v.getName()))
                .forEach(v -> variableMapper.deleteById(v.getId()));
    }

    // ==================== 3.4 导入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiImportResultRespDTO importFile(UUID projectId, UUID userId, byte[] content,
                                             String filename, String formatHint) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        String text = content == null ? "" : new String(content, StandardCharsets.UTF_8);
        return doImport(projectId, userId, "file", filename, formatHint, text);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiImportResultRespDTO importUrl(UUID projectId, UUID userId, String url, String formatHint) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        String content = sourceFetcher.fetch(url);
        return doImport(projectId, userId, "url", url, formatHint, content);
    }

    @Override
    public ApiImportPreviewRespDTO preview(UUID projectId, UUID userId, byte[] content, String formatHint) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        String text = content == null ? "" : new String(content, StandardCharsets.UTF_8);
        InterfaceImportParser parser = resolveParser(formatHint, text);
        List<ImportedOperation> operations = parseSafely(parser, text);
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

    // ==================== 3.1.13 变更历史 ====================

    @Override
    public PageResult<ApiInterfaceChangeLogRespDTO> changeLogs(UUID projectId, UUID userId, UUID interfaceId, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireInterface(projectId, interfaceId);
        PageResult<ApiInterfaceChangeLog> page = changeLogMapper.selectPageByInterfaceId(interfaceId, pageParam);
        return new PageResult<>(page.getList().stream()
                .map(log -> ApiInterfaceChangeLogRespDTO.builder()
                        .id(log.getId())
                        .changeVersion(log.getChangeVersion())
                        .action(log.getAction())
                        .summary(log.getSummary())
                        .operatorId(log.getOperatorId())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList(), page.getTotal());
    }

    // ==================== 内部方法 ====================

    private List<UUID> convertFollowIds(UUID userId) {
        return followMapper.selectListByUserId(userId).stream()
                .map(ApiInterfaceFollow::getInterfaceId).toList();
    }

    private ApiInterfaceItemRespDTO toItem(ApiInterface entity, boolean followed) {
        return ApiInterfaceItemRespDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .protocol(entity.getProtocol())
                .method(entity.getMethod())
                .path(entity.getPath())
                .moduleId(entity.getModuleId())
                .status(entity.getStatus())
                .referenceCount(entity.getReferenceCount())
                .changeVersion(entity.getChangeVersion())
                .followed(followed)
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ApiInterfaceStepRespDTO toStep(ApiInterfaceStep step) {
        return ApiInterfaceStepRespDTO.builder()
                .id(step.getId())
                .name(step.getName())
                .stepType(step.getStepType())
                .sortOrder(step.getSortOrder())
                .enabled(step.getEnabled())
                .requestConfig(step.getRequestConfig())
                .processors(step.getProcessors())
                .validators(step.getValidators())
                .extractors(step.getExtractors())
                .build();
    }

    private ApiInterfaceVariableRespDTO toVariable(ApiInterfaceVariable variable) {
        return ApiInterfaceVariableRespDTO.builder()
                .id(variable.getId())
                .name(variable.getName())
                .defaultValue(variable.getDefaultValue())
                .description(variable.getDescription())
                .required(variable.getRequired())
                .sortOrder(variable.getSortOrder())
                .build();
    }

    private ApiInterface requireInterface(UUID projectId, UUID id) {
        ApiInterface entity = interfaceMapper.selectById(id);
        if (entity == null || !projectId.equals(entity.getProjectId())) {
            throw get(API_INTERFACE_NOT_FOUND);
        }
        return entity;
    }

    private ApiInterfaceStep requireStep(UUID interfaceId, UUID stepId) {
        ApiInterfaceStep step = stepMapper.selectById(stepId);
        if (step == null || !interfaceId.equals(step.getInterfaceId())) {
            throw get(API_INTERFACE_NOT_FOUND);
        }
        return step;
    }

    private void assertNameAvailable(UUID projectId, UUID moduleId, String name, UUID excludeId) {
        ApiInterface existing = interfaceMapper.selectByNameAndModule(projectId, moduleId, name);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw get(API_INTERFACE_NAME_EXISTS, name);
        }
    }

    /** V1.2 仅 http 协议（详细设计 6.3），jdbc 随场景模块梯队三开放 */
    private void validateProtocol(String protocol) {
        if (protocol != null && !"http".equals(protocol)) {
            throw get(API_FORMAT_CONVERT_FAILED, "V1.2 仅支持 http 协议");
        }
    }

    private void applyRequest(ApiInterface entity, ApiInterfaceCreateReqDTO reqDTO) {
        entity.setName(reqDTO.getName());
        entity.setProtocol(reqDTO.getProtocol());
        entity.setMethod(reqDTO.getMethod());
        entity.setPath(reqDTO.getPath());
        entity.setDescription(reqDTO.getDescription());
        entity.setModuleId(reqDTO.getModuleId());
        entity.setHeaders(reqDTO.getHeaders());
        entity.setBodyType(reqDTO.getBody() == null ? null : String.valueOf(reqDTO.getBody().get("type")));
        entity.setBody(reqDTO.getBody());
        entity.setQueryParams(reqDTO.getParams());
        entity.setRestParams(reqDTO.getRestParams());
        entity.setAuth(reqDTO.getAuth());
        entity.setStatus(reqDTO.getStatus());
        entity.setResponseExample(reqDTO.getResponseExample());
    }

    private void applyStepFields(ApiInterfaceStep step, ApiInterfaceStepReqDTO reqDTO) {
        step.setName(reqDTO.getName());
        step.setStepType(reqDTO.getStepType());
        step.setSortOrder(reqDTO.getSortOrder() == null ? 0 : reqDTO.getSortOrder());
        step.setEnabled(reqDTO.getEnabled() == null || reqDTO.getEnabled());
        step.setRequestConfig(reqDTO.getRequestConfig());
        step.setProcessors(reqDTO.getProcessors() == null ? List.of() : reqDTO.getProcessors());
        step.setValidators(reqDTO.getValidators() == null ? List.of() : reqDTO.getValidators());
        step.setExtractors(reqDTO.getExtractors() == null ? List.of() : reqDTO.getExtractors());
    }

    private void fillVariable(ApiInterfaceVariable target, ApiInterfaceVariablesReqDTO.VariableItem item, int sort) {
        target.setName(item.getName());
        target.setDefaultValue(item.getDefaultValue());
        target.setDescription(item.getDescription());
        target.setRequired(Boolean.TRUE.equals(item.getRequired()));
        target.setSortOrder(item.getSortOrder() == null ? sort : item.getSortOrder());
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

    private String diffSummary(ApiInterface current, ApiInterfaceCreateReqDTO reqDTO) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(current.getName(), reqDTO.getName())) changes.add("名称");
        if (!Objects.equals(current.getPath(), reqDTO.getPath())) changes.add("路径");
        if (!Objects.equals(current.getMethod(), reqDTO.getMethod())) changes.add("方法");
        if (!Objects.equals(current.getDescription(), reqDTO.getDescription())) changes.add("描述");
        return changes.isEmpty() ? "更新请求参数" : "修改 " + String.join("、", changes);
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

    /**
     * 导入主流程（详细设计 4.1）：解析 → 逐条 upsert → 导入记录留痕 → 映射关系落库。
     * 单条失败不中断整体，失败项计入 errors 并标记 partial 状态。
     */
    private ApiImportResultRespDTO doImport(UUID projectId, UUID userId, String channel,
                                            String sourceName, String formatHint, String content) {
        InterfaceImportParser parser = resolveParser(formatHint, content);
        String importType = channel + "_" + parserType(parser);
        List<ImportedOperation> operations = parseSafely(parser, content);
        int created = 0;
        int updated = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<PendingMapping> mappings = new ArrayList<>();
        for (ImportedOperation operation : operations) {
            try {
                UpsertResult result = upsertOperation(projectId, userId, parser.sourceType(), operation);
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
            mapping.setSourceType(parser.sourceType());
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

    private Map<String, Object> importSummary(int created, int updated, int failed) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("failed", failed);
        return summary;
    }

    private String parserType(InterfaceImportParser parser) {
        return switch (parser.sourceType()) {
            case "swagger_operation" -> "swagger";
            case "postman_item" -> "postman";
            case "har_entry" -> "har";
            default -> "jmeter";
        };
    }

    /** 增量导入：优先按导入映射匹配源标识，缺失时按路径+方法去重（详细设计 4.1/6.2） */
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

    /** 名称模块内唯一约束兜底：重名时拼接序号后缀 */
    private String uniqueName(UUID projectId, String name, String method, String path) {
        String base = name == null || name.isBlank() ? method + " " + path : name;
        String candidate = base;
        int suffix = 2;
        while (interfaceMapper.selectByNameAndModule(projectId, null, candidate) != null) {
            candidate = base + " (" + suffix++ + ")";
        }
        return candidate;
    }

    private List<Map<String, Object>> cloneList(List<Map<String, Object>> source) {
        return source == null ? null : List.copyOf(source);
    }

    private Map<String, Object> cloneMap(Map<String, Object> source) {
        return source == null ? null : Map.copyOf(source);
    }

    private record UpsertResult(UUID targetId, String action) {
    }

    private record PendingMapping(ImportedOperation operation, UUID targetId, String action) {
    }
}
