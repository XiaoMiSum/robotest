package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.RoleConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.admin.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.PermissionTableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.RoleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.RoleSimpleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.RoleWorkspaceUserRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysPermission;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysPermissionMapper;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements RoleService {

    @Resource
    private SysRoleMapper roleMapper;
    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private SysPermissionMapper permissionMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private WorkspaceMapper workspaceMapper;

    @Override
    public List<RoleSimpleRespDTO> getRoleList(String type) {
        List<SysRole> roles = roleMapper.selectList(SysRole::getType, type);

        return roles.stream().map(role -> {
            RoleSimpleRespDTO node = new RoleSimpleRespDTO();
            node.setId(role.getId());
            node.setName(role.getName());
            node.setType(role.getType());
            node.setIsSystem(role.getIsSystem());
            node.setFullAccess(role.getFullAccess());
            node.setUserCount(Math.toIntExact(userRoleMapper.selectCount(SysUserRole::getRoleId, role.getId())));
            return node;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createRole(RoleCreateReqDTO reqDTO) {
        if (roleMapper.selectOne(SysRole::getName, reqDTO.getName()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NAME_EXISTS);
        }
        if (!Constants.RoleType.SYSTEM.equals(reqDTO.getType())
                && !Constants.RoleType.WORKSPACE.equals(reqDTO.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_TYPE_ERROR);
        }

        SysRole role = RoleConvertMapper.INSTANCE.toEntity(reqDTO);
        role.setIsSystem(false);
        role.setPermissions(List.of());
        roleMapper.insert(role);
        return role.getId().toString();
    }

    @Override
    public RoleRespDTO updateRole(UUID id, RoleUpdateReqDTO reqDTO) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        SysRole existing = roleMapper.selectOne(SysRole::getName, reqDTO.getName());
        if (existing != null && !existing.getId().equals(id)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NAME_EXISTS);
        }
        role.setName(reqDTO.getName());
        SysRole update = new SysRole();
        update.setId(id);
        update.setName(reqDTO.getName());
        roleMapper.updateById(update);
        RoleRespDTO dto = RoleConvertMapper.INSTANCE.toRespDTO(role);
        dto.setUserCount(Math.toIntExact(userRoleMapper.selectCount(SysUserRole::getRoleId, role.getId())));
        return dto;
    }

    @Override
    public void deleteRole(UUID id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SYSTEM_ROLE_NOT_DELETABLE);
        }
        long userCount = userRoleMapper.selectCount(SysUserRole::getRoleId, id);
        if (userCount > 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_IN_USE);
        }
        roleMapper.deleteById(id);
    }

    @Override
    public RoleRespDTO getRoleDetail(UUID id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        RoleRespDTO dto = RoleConvertMapper.INSTANCE.toRespDTO(role);
        dto.setUserCount(Math.toIntExact(userRoleMapper.selectCount(SysUserRole::getRoleId, role.getId())));
        return dto;
    }

    @Override
    public List<RoleWorkspaceUserRespDTO> getRoleWorkspaceUsers(UUID roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }

        List<WorkspaceUser> workspaceUsers = workspaceUserMapper.selectList(WorkspaceUser::getWorkspaceRole, roleId);
        if (workspaceUsers.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<WorkspaceUser>> grouped = workspaceUsers.stream()
                .collect(Collectors.groupingBy(WorkspaceUser::getUserId));

        List<UUID> userIds = new ArrayList<>(grouped.keySet());
        List<SysUser> users = userMapper.listByIds(userIds);
        Map<UUID, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        List<UUID> workspaceIds = workspaceUsers.stream()
                .map(WorkspaceUser::getWorkspaceId)
                .distinct()
                .collect(Collectors.toList());
        List<Workspace> workspaces = workspaceMapper.listByIds(workspaceIds);
        Map<UUID, String> workspaceNameMap = workspaces.stream()
                .collect(Collectors.toMap(Workspace::getId, Workspace::getName));

        return grouped.entrySet().stream().map(entry -> {
            UUID userId = entry.getKey();
            List<WorkspaceUser> wuList = entry.getValue();
            SysUser user = userMap.get(userId);

            RoleWorkspaceUserRespDTO dto = new RoleWorkspaceUserRespDTO();
            dto.setUserId(userId);
            dto.setUsername(user != null ? user.getUsername() : null);
            dto.setName(user != null ? user.getName() : null);
            dto.setWorkspaces(wuList.stream().map(wu -> {
                RoleWorkspaceUserRespDTO.WorkspaceInfo info = new RoleWorkspaceUserRespDTO.WorkspaceInfo();
                info.setWorkspaceId(wu.getWorkspaceId());
                info.setWorkspaceName(workspaceNameMap.get(wu.getWorkspaceId()));
                return info;
            }).collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addRoleUsers(UUID id, List<UUID> userIds) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        for (UUID userId : userIds) {
            if (userRoleMapper.selectOne(SysUserRole::getUserId, userId, SysUserRole::getRoleId, id) != null) continue;

            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(id);
            userRole.setAssignedAt(java.time.LocalDateTime.now());
            userRoleMapper.insert(userRole);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addWorkspaceRoleUsers(UUID roleId, List<UUID> userIds, List<UUID> workspaceIds) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        for (UUID userId : userIds) {
            for (UUID workspaceId : workspaceIds) {
                if (workspaceUserMapper.existsByUserIdAndWorkspaceIdAndRole(userId, workspaceId, roleId)) continue;

                WorkspaceUser workspaceUser = new WorkspaceUser();
                workspaceUser.setUserId(userId);
                workspaceUser.setWorkspaceId(workspaceId);
                workspaceUser.setWorkspaceRole(roleId);
                workspaceUser.setJoinedAt(java.time.LocalDateTime.now());
                workspaceUserMapper.insert(workspaceUser);
            }
        }
    }

    @Override
    public void removeRoleUser(UUID id, UUID userId) {
        userRoleMapper.deleteByUserIdAndRoleId(userId, id);
    }

    @Override
    public void removeWorkspaceRoleUser(UUID roleId, UUID userId, UUID workspaceId) {
        workspaceUserMapper.deleteByUserIdAndWorkspaceIdAndRole(userId, workspaceId, roleId);
    }

    @Override
    public RoleRespDTO updateRolePermissions(UUID id, RolePermissionsUpdateReqDTO reqDTO) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(role.getIsSystem()) || Boolean.TRUE.equals(role.getFullAccess())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SYSTEM_ROLE_PERMISSION_NOT_MODIFIABLE);
        }
        role.setPermissions(reqDTO.getPermissions());
        SysRole update = new SysRole();
        update.setId(id);
        update.setPermissions(reqDTO.getPermissions());
        roleMapper.updateById(update);
        RoleRespDTO dto = RoleConvertMapper.INSTANCE.toRespDTO(role);
        dto.setUserCount(Math.toIntExact(userRoleMapper.selectCount(SysUserRole::getRoleId, role.getId())));
        return dto;
    }

    @Override
    public List<PermissionTableRespDTO> getPermissionTable(String roleType) {
        String scope = Constants.RoleType.WORKSPACE.equals(roleType) ? "workspace" : "global";
        List<SysPermission> permissions = permissionMapper.findByScopeOrdered(scope);

        return permissions.stream()
                .filter(p -> p.getParentCode() != null)
                .collect(Collectors.groupingBy(SysPermission::getModule))
                .entrySet().stream()
                .map(entry -> {
                    PermissionTableRespDTO dto = new PermissionTableRespDTO();
                    dto.setModule(entry.getKey());
                    dto.setPermissions(entry.getValue().stream().map(p -> {
                        PermissionTableRespDTO.PermissionItem item = new PermissionTableRespDTO.PermissionItem();
                        item.setCode(p.getCode());
                        item.setName(p.getName());
                        return item;
                    }).collect(Collectors.toList()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getUserPermissionCodes(UUID userId) {
        List<SysUserRole> userRoles = userRoleMapper.listByUserId(userId);
        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }

        List<UUID> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = roleMapper.listByIds(roleIds);

        return roles.stream()
                .flatMap(role -> {
                    List<String> perms = role.getPermissions() != null ? role.getPermissions() : List.of();
                    return perms.stream();
                })
                .distinct()
                .collect(Collectors.toList());
    }
}
