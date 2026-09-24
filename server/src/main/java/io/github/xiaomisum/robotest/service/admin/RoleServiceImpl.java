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
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        List<SysRole> roles = roleMapper.listByType(type);
        if (roles.isEmpty()) {
            return List.of();
        }

        // 一次查出所有角色的用户关系并分组计数，避免逐角色 selectCount 造成 N+1 查询
        List<UUID> roleIds = roles.stream().map(SysRole::getId).collect(Collectors.toList());
        Map<UUID, Long> userCountMap = userRoleMapper.listByRoleIds(roleIds).stream()
                .collect(Collectors.groupingBy(SysUserRole::getRoleId, Collectors.counting()));

        return roles.stream().map(role -> {
            RoleSimpleRespDTO node = RoleConvertMapper.INSTANCE.toSimpleRespDTO(role);
            node.setUserCount(Math.toIntExact(userCountMap.getOrDefault(role.getId(), 0L)));
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
            // 同一用户可能分布在多个空间，授权时间取最近一次关联更新
            dto.setGrantedAt(wuList.stream()
                    .map(WorkspaceUser::getUpdatedAt)
                    .max(Comparator.naturalOrder())
                    .orElse(null));
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
        if (Boolean.TRUE.equals(role.getIsSystem())) {
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
        List<SysPermission> rows = permissionMapper.findByScopeOrdered(scope);

        Map<String, SysPermission> roots = new HashMap<>();
        List<SysPermission> leaves = new ArrayList<>();
        for (SysPermission permission : rows) {
            if (permission.getParentCode() == null) {
                roots.put(permission.getCode(), permission);
            } else {
                leaves.add(permission);
            }
        }

        // 一级取 top_module、二级命名与排序取根节点，展示顺序完全由种子 sort_order 驱动（一级=组内最小根 sort）
        Map<String, Map<String, List<SysPermission>>> grouped = new LinkedHashMap<>();
        for (SysPermission leaf : leaves) {
            grouped.computeIfAbsent(leaf.getTopModule(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(leaf.getParentCode(), key -> new ArrayList<>())
                    .add(leaf);
        }

        record RankedModule(int sort, PermissionTableRespDTO.ModuleGroup dto) {
        }
        record RankedTop(int sort, PermissionTableRespDTO dto) {
        }

        List<RankedTop> rankedTops = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<SysPermission>>> topEntry : grouped.entrySet()) {
            List<RankedModule> modules = new ArrayList<>();
            for (Map.Entry<String, List<SysPermission>> moduleEntry : topEntry.getValue().entrySet()) {
                SysPermission root = roots.get(moduleEntry.getKey());
                String moduleName;
                int moduleSort;
                if (root != null) {
                    moduleName = root.getName();
                    moduleSort = root.getSortOrder();
                } else {
                    // 根节点缺失的孤儿叶子兜底用叶子 module 作组名并排末尾，脏数据不打断整体分组
                    moduleName = moduleEntry.getValue().get(0).getModule();
                    moduleSort = Integer.MAX_VALUE;
                }
                PermissionTableRespDTO.ModuleGroup group = new PermissionTableRespDTO.ModuleGroup();
                group.setModule(moduleName);
                group.setPermissions(moduleEntry.getValue().stream()
                        .sorted(Comparator.comparing(SysPermission::getSortOrder))
                        .map(leaf -> {
                            PermissionTableRespDTO.PermissionItem item = new PermissionTableRespDTO.PermissionItem();
                            item.setCode(leaf.getCode());
                            item.setName(leaf.getName());
                            return item;
                        })
                        .collect(Collectors.toList()));
                modules.add(new RankedModule(moduleSort, group));
            }
            modules.sort(Comparator.comparingInt(RankedModule::sort));
            PermissionTableRespDTO topDto = new PermissionTableRespDTO();
            topDto.setTopModule(topEntry.getKey());
            topDto.setModules(modules.stream().map(RankedModule::dto).collect(Collectors.toList()));
            int topSort = modules.stream().mapToInt(RankedModule::sort).min().orElse(Integer.MAX_VALUE);
            rankedTops.add(new RankedTop(topSort, topDto));
        }
        rankedTops.sort(Comparator.comparingInt(RankedTop::sort));
        return rankedTops.stream().map(RankedTop::dto).collect(Collectors.toList());
    }
}
