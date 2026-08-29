package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.CommonComponentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentCopyRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentListItemRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.CommonComponent;
import io.github.xiaomisum.robotest.repository.apitest.CommonComponentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommonComponentServiceImpl implements CommonComponentService {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> TYPES = Set.of("preprocessor", "postprocessor", "validator", "extractor");

    private final CommonComponentMapper componentMapper;
    private final ProjectAccessGuard projectAccessGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonComponentIdRespDTO create(UUID workspaceId, UUID projectId, UUID userId, CommonComponentSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        validateType(reqDTO.getType());
        String scope = normalizeScope(reqDTO.getScope());
        requireScopeEditPermission(scope);
        assertNameFree(scope, workspaceId, projectId, reqDTO.getType(), reqDTO.getName(), null);

        CommonComponent entity = new CommonComponent();
        entity.setWorkspaceId("workspace".equals(scope) ? workspaceId : null);
        entity.setProjectId("project".equals(scope) ? projectId : null);
        entity.setScope(scope);
        entity.setType(reqDTO.getType());
        entity.setName(reqDTO.getName());
        entity.setDescription(reqDTO.getDescription());
        entity.setSortOrder(reqDTO.getSortOrder() == null ? 0 : reqDTO.getSortOrder());
        entity.setEnabled(true);
        entity.setConfig(reqDTO.getConfig() == null ? "{}" : JsonUtils.toJsonString(reqDTO.getConfig()));
        entity.setUpdatedBy(userId);
        componentMapper.insert(entity);
        return new CommonComponentIdRespDTO(entity.getId().toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, CommonComponentSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        CommonComponent existing = requireVisible(workspaceId, projectId, id);
        if (!existing.getType().equals(reqDTO.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_COMMON_COMPONENT_NOT_FOUND, "组件类型不可变更");
        }

        // scope 为 null 表示不变更，使用现有 scope
        String newScope = reqDTO.getScope() != null ? reqDTO.getScope() : existing.getScope();
        boolean scopeChanged = !existing.getScope().equals(newScope);
        if (scopeChanged) {
            requireScopeEditPermission(newScope);
        } else {
            requireScopeEditPermission(existing.getScope());
        }

        assertNameFree(newScope, workspaceId, projectId, existing.getType(), reqDTO.getName(), id);

        CommonComponent update = new CommonComponent();
        update.setId(id);
        update.setName(reqDTO.getName());
        update.setDescription(reqDTO.getDescription());
        if (reqDTO.getSortOrder() != null) {
            update.setSortOrder(reqDTO.getSortOrder());
        }
        if (reqDTO.getConfig() != null) {
            update.setConfig(JsonUtils.toJsonString(reqDTO.getConfig()));
        }
        update.setUpdatedBy(userId);
        if (scopeChanged) {
            update.setScope(newScope);
            update.setWorkspaceId("workspace".equals(newScope) ? workspaceId : null);
            update.setProjectId("project".equals(newScope) ? projectId : null);
        }
        componentMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggle(UUID workspaceId, UUID projectId, UUID userId, UUID id, boolean enabled) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        CommonComponent existing = requireVisible(workspaceId, projectId, id);
        requireScopeEditPermission(existing.getScope());

        CommonComponent update = new CommonComponent();
        update.setId(id);
        update.setEnabled(enabled);
        update.setUpdatedBy(userId);
        componentMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchToggle(UUID workspaceId, UUID projectId, UUID userId, List<UUID> ids, boolean enabled) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        for (UUID id : ids) {
            CommonComponent existing = requireVisible(workspaceId, projectId, id);
            requireScopeEditPermission(existing.getScope());
            CommonComponent update = new CommonComponent();
            update.setId(id);
            update.setEnabled(enabled);
            update.setUpdatedBy(userId);
            componentMapper.updateById(update);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        CommonComponent existing = requireVisible(workspaceId, projectId, id);
        requireScopeEditPermission(existing.getScope());
        componentMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(UUID workspaceId, UUID projectId, UUID userId, List<UUID> ids) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        for (UUID id : ids) {
            CommonComponent existing = requireVisible(workspaceId, projectId, id);
            requireScopeEditPermission(existing.getScope());
            componentMapper.deleteById(id);
        }
    }

    @Override
    public PageResult<CommonComponentListItemRespDTO> fetchList(UUID workspaceId, UUID projectId, UUID userId,
                                                                PageParam pageParam, String type, Boolean enabled,
                                                                String scope, String keyword) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        PageResult<CommonComponent> page = componentMapper.selectPageVisible(projectId, workspaceId, type, enabled, scope, keyword, pageParam);
        return new PageResult<>(page.getList().stream().map(this::toListItem).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonComponentCopyRespDTO copy(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        CommonComponent existing = requireVisible(workspaceId, projectId, id);
        requireScopeEditPermission(existing.getScope());

        CommonComponent copy = new CommonComponent();
        copy.setWorkspaceId(existing.getWorkspaceId());
        copy.setProjectId(existing.getProjectId());
        copy.setScope(existing.getScope());
        copy.setType(existing.getType());
        copy.setName(existing.getName() + " (副本)");
        copy.setDescription(existing.getDescription());
        copy.setSortOrder(existing.getSortOrder());
        copy.setEnabled(false);
        copy.setConfig(existing.getConfig());
        copy.setUpdatedBy(userId);
        componentMapper.insert(copy);
        return new CommonComponentCopyRespDTO(
                copy.getId().toString(),
                copy.getType(),
                copy.getName(),
                existing.getId().toString()
        );
    }

    private CommonComponent requireVisible(UUID workspaceId, UUID projectId, UUID id) {
        CommonComponent entity = componentMapper.findVisibleById(projectId, workspaceId, id);
        if (entity == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_COMMON_COMPONENT_NOT_FOUND);
        }
        return entity;
    }

    private void validateType(String type) {
        if (type == null || !TYPES.contains(type)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_COMMON_COMPONENT_NOT_FOUND, "组件类型不合法");
        }
    }

    private static String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "project" : scope;
    }

    private static void requireScopeEditPermission(String scope) {
        String required = switch (scope) {
            case "workspace" -> "api-component:edit-space";
            case "global" -> "api-component:edit-global";
            default -> "api-component:edit";
        };
        LoginUser loginUser = currentUser();
        if (loginUser == null || !loginUser.getPermissions().contains(required)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    private void assertNameFree(String scope, UUID workspaceId, UUID projectId, String type, String name, UUID excludeId) {
        if (componentMapper.existsByScopeAndTypeAndName(scope, workspaceId, projectId, type, name, excludeId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_COMMON_COMPONENT_NAME_EXISTS, name);
        }
    }

    static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof LoginUser loginUser ? loginUser : null;
    }

    private CommonComponentListItemRespDTO toListItem(CommonComponent entity) {
        CommonComponentListItemRespDTO item = new CommonComponentListItemRespDTO();
        item.setId(entity.getId().toString());
        item.setScope(entity.getScope());
        item.setType(entity.getType());
        item.setName(entity.getName());
        item.setDescription(entity.getDescription());
        item.setSortOrder(entity.getSortOrder());
        item.setConfig(entity.getConfig());
        item.setEnabled(entity.getEnabled());
        item.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().format(DATETIME) : null);
        return item;
    }
}
