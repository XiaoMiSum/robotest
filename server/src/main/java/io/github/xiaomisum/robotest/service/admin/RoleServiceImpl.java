package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.RoleConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.PermissionTableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleSimpleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleWorkspaceUserRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysPermission;
import io.github.xiaomisum.robotest.model.entity.SysRole;
import io.github.xiaomisum.robotest.model.entity.SysUserRole;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.SysPermissionMapper;
import io.github.xiaomisum.robotest.repository.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.SysUserRoleMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

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
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapperX<SysRole>()
                .eqIfPresent(SysRole::getType, type)
                .orderByAsc(SysRole::getType, SysRole::getName));

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
        // 鏍￠獙鍚嶇О鍞竴
        if (roleMapper.selectOne(SysRole::getName, reqDTO.getName()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NAME_EXISTS);
        }
        // 校验类型
        if (!Constants.RoleType.SYSTEM.equals(reqDTO.getType())
                && !Constants.RoleType.WORKSPACE.equals(reqDTO.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_TYPE_ERROR);
        }

        SysRole role = new SysRole();
        role.setName(reqDTO.getName());
        role.setType(reqDTO.getType());
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
        // 鏍￠獙鍚嶇О鍞竴锛堟帓闄よ嚜韬級
        SysRole existing = roleMapper.selectOne(SysRole::getName, reqDTO.getName());
        if (existing != null && !existing.getId().equals(id)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NAME_EXISTS);
        }
        role.setName(reqDTO.getName());
        roleMapper.updateById(role);
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
        // 妫€鏌ユ槸鍚︽湁鐢ㄦ埛寮曠敤
        Long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapperX<SysUserRole>().eq(SysUserRole::getRoleId, id));
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

        // 查询所有拥有该空间角色的 workspace_user 记录
        List<WorkspaceUser> workspaceUsers = workspaceUserMapper.selectList(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getWorkspaceRole, roleId));

        if (workspaceUsers.isEmpty()) {
            return List.of();
        }

        // 按 userId 分组
        Map<UUID, List<WorkspaceUser>> grouped = workspaceUsers.stream()
                .collect(Collectors.groupingBy(WorkspaceUser::getUserId));

        // 批量查询用户信息
        List<UUID> userIds = new ArrayList<>(grouped.keySet());
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapperX<SysUser>().in(SysUser::getId, userIds));
        Map<UUID, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        // 批量查询空间信息
        List<UUID> workspaceIds = workspaceUsers.stream()
                .map(WorkspaceUser::getWorkspaceId)
                .distinct()
                .collect(Collectors.toList());
        List<Workspace> workspaces = workspaceMapper.selectList(
                new LambdaQueryWrapperX<Workspace>().in(Workspace::getId, workspaceIds));
        Map<UUID, String> workspaceNameMap = workspaces.stream()
                .collect(Collectors.toMap(Workspace::getId, Workspace::getName));

        // 组装结果
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
            // 璺宠繃宸插瓨鍦ㄧ殑
            Long count = userRoleMapper.selectCount(new LambdaQueryWrapperX<SysUserRole>()
                    .eq(SysUserRole::getUserId, userId)
                    .eq(SysUserRole::getRoleId, id));
            if (count > 0) continue;

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
                // 跳过已存在的记录
                Long count = workspaceUserMapper.selectCount(new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                        .eq(WorkspaceUser::getWorkspaceRole, roleId));
                if (count > 0) continue;

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
        userRoleMapper.delete(new LambdaQueryWrapperX<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, id));
    }

    @Override
    public void removeWorkspaceRoleUser(UUID roleId, UUID userId, UUID workspaceId) {
        workspaceUserMapper.delete(new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getUserId, userId)
                .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                .eq(WorkspaceUser::getWorkspaceRole, roleId));
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
        roleMapper.updateById(role);
        RoleRespDTO dto = RoleConvertMapper.INSTANCE.toRespDTO(role);
        dto.setUserCount(Math.toIntExact(userRoleMapper.selectCount(SysUserRole::getRoleId, role.getId())));
        return dto;
    }

    @Override
    public List<PermissionTableRespDTO> getPermissionTable(String roleType) {
        String scope = Constants.RoleType.WORKSPACE.equals(roleType) ? "workspace" : "global";
        List<SysPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapperX<SysPermission>()
                        .eq(SysPermission::getScope, scope)
                        .orderByAsc(SysPermission::getModule, SysPermission::getSortOrder));

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
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapperX<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }

        List<UUID> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapperX<SysRole>().in(SysRole::getId, roleIds));

        return roles.stream()
                .flatMap(role -> {
                    List<String> perms = role.getPermissions() != null ? role.getPermissions() : List.of();
                    return perms.stream();
                })
                .distinct()
                .collect(Collectors.toList());
    }
}
