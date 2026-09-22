package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchMoveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceChangeLog;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceFollow;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceChangeLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceItemRespDTO;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceChangeLogMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceFollowMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.*;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_FORMAT_CONVERT_FAILED;
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

    @Resource
    private ApiInterfaceMapper interfaceMapper;
    @Resource
    private ApiInterfaceFollowMapper followMapper;
    @Resource
    private ApiInterfaceChangeLogMapper changeLogMapper;
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
        Set<UUID> followedSet = new HashSet<>(convertFollowIds(userId));
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
                .validators(entity.getValidators())
                .extractors(entity.getExtractors())
                .followed(followed)
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
        copy.setValidators(cloneList(source.getValidators()));
        copy.setExtractors(cloneList(source.getExtractors()));
        copy.setCreatedBy(userId);
        copy.setChangeVersion(1);
        copy.setReferenceCount(0);
        interfaceMapper.insert(copy);
        writeChangeLog(copy.getId(), 1, "copy", "复制自接口 " + source.getName(), userId);
        return copy.getId();
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

    private ApiInterface requireInterface(UUID projectId, UUID id) {
        ApiInterface entity = interfaceMapper.selectById(id);
        if (entity == null || !projectId.equals(entity.getProjectId())) {
            throw get(API_INTERFACE_NOT_FOUND);
        }
        return entity;
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
        entity.setValidators(reqDTO.getValidators());
        entity.setExtractors(reqDTO.getExtractors());
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

    private List<Map<String, Object>> cloneList(List<Map<String, Object>> source) {
        return source == null ? null : List.copyOf(source);
    }

    private Map<String, Object> cloneMap(Map<String, Object> source) {
        return source == null ? null : Map.copyOf(source);
    }
}
