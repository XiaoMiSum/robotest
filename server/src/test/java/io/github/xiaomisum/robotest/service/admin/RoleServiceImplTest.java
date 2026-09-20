package io.github.xiaomisum.robotest.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.admin.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.PermissionTableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.RoleSimpleRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysPermission;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysPermissionMapper;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysPermissionMapper permissionMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private PermissionFacade permissionFacade;

    @InjectMocks
    private RoleServiceImpl roleService;

    private UUID userId;
    private UUID roleId;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        roleId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    }

    @Test
    void getUserPermissionCodes_delegatesToSystemFacade() {
        when(permissionFacade.permissionsOf(userId, PermissionScope.SYSTEM, null))
                .thenReturn(Set.of("user:view", "role:edit"));

        List<String> codes = roleService.getUserPermissionCodes(userId);

        assertEquals(2, codes.size());
        assertTrue(codes.containsAll(List.of("user:view", "role:edit")));
    }

    @Test
    void createRole_duplicateName_throws() {
        RoleCreateReqDTO dto = new RoleCreateReqDTO();
        dto.setName("测试角色");
        dto.setType(Constants.RoleType.WORKSPACE);
        when(roleMapper.selectOne(any(SFunction.class), any())).thenReturn(new SysRole());

        assertThrows(ServiceException.class, () -> roleService.createRole(dto));
    }

    @Test
    void createRole_badType_throws() {
        RoleCreateReqDTO dto = new RoleCreateReqDTO();
        dto.setName("非法类型角色");
        dto.setType("unknown");
        when(roleMapper.selectOne(any(SFunction.class), any())).thenReturn(null);

        assertThrows(ServiceException.class, () -> roleService.createRole(dto));
    }

    @Test
    void deleteRole_systemRole_throws() {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setIsSystem(true);
        when(roleMapper.selectById(roleId)).thenReturn(role);

        assertThrows(ServiceException.class, () -> roleService.deleteRole(roleId));
    }

    @Test
    void deleteRole_inUse_throws() {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setIsSystem(false);
        when(roleMapper.selectById(roleId)).thenReturn(role);
        when(userRoleMapper.selectCount(any(SFunction.class), any())).thenReturn(3L);

        assertThrows(ServiceException.class, () -> roleService.deleteRole(roleId));
        verify(roleMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void getRoleDetail_ok() {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setName("测试角色");
        role.setIsSystem(false);
        when(roleMapper.selectById(roleId)).thenReturn(role);
        when(userRoleMapper.selectCount(any(SFunction.class), any())).thenReturn(2L);

        var dto = roleService.getRoleDetail(roleId);

        assertEquals(roleId, dto.getId());
        assertEquals(2, dto.getUserCount());
    }

    @Test
    void updateRolePermissions_systemRole_throws() {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setIsSystem(true);
        when(roleMapper.selectById(roleId)).thenReturn(role);

        RolePermissionsUpdateReqDTO dto = new RolePermissionsUpdateReqDTO();
        dto.setPermissions(List.of("user:view"));

        assertThrows(ServiceException.class, () -> roleService.updateRolePermissions(roleId, dto));
    }

    @Test
    void addRoleUsers_skipsExistingMembership() {
        SysRole role = new SysRole();
        role.setId(roleId);
        when(roleMapper.selectById(roleId)).thenReturn(role);
        when(userRoleMapper.selectOne(any(SFunction.class), any(), any(SFunction.class), any()))
                .thenReturn(new SysUserRole());

        roleService.addRoleUsers(roleId, List.of(userId));

        verify(userRoleMapper, never()).insert(any(SysUserRole.class));
    }

    @Test
    void updateRole_duplicateName_throws() {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setName("原角色");
        when(roleMapper.selectById(roleId)).thenReturn(role);

        SysRole existing = new SysRole();
        existing.setId(UUID.randomUUID());
        when(roleMapper.selectOne(any(SFunction.class), any())).thenReturn(existing);

        RoleUpdateReqDTO dto = new RoleUpdateReqDTO();
        dto.setName("重名角色");

        assertThrows(ServiceException.class, () -> roleService.updateRole(roleId, dto));
        verify(roleMapper, never()).updateById(any(SysRole.class));
    }

    @Test
    void updateRole_success_partialUpdateCarrier() {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setName("原角色");
        role.setIsSystem(false);
        when(roleMapper.selectById(roleId)).thenReturn(role);
        when(roleMapper.selectOne(any(SFunction.class), any())).thenReturn(null);
        when(userRoleMapper.selectCount(any(SFunction.class), any())).thenReturn(2L);

        RoleUpdateReqDTO dto = new RoleUpdateReqDTO();
        dto.setName("新角色名");

        var resultDto = roleService.updateRole(roleId, dto);

        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(roleMapper).updateById(captor.capture());
        assertEquals(roleId, captor.getValue().getId());
        assertEquals("新角色名", captor.getValue().getName());
        assertEquals(2, resultDto.getUserCount());
    }

    @Test
    void getRoleList_aggregatesUserCountWithoutNPlusOne() {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setName("QA");
        role.setType(Constants.RoleType.WORKSPACE);
        when(roleMapper.listByType(Constants.RoleType.WORKSPACE)).thenReturn(List.of(role));

        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(roleId);
        when(userRoleMapper.listByRoleIds(any())).thenReturn(List.of(userRole, userRole)); // 2 个成员

        List<RoleSimpleRespDTO> result = roleService.getRoleList(Constants.RoleType.WORKSPACE);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getUserCount());
    }

    @Test
    void getRoleList_empty_typeReturnsEmpty() {
        when(roleMapper.listByType("unknown")).thenReturn(List.of());

        List<RoleSimpleRespDTO> result = roleService.getRoleList("unknown");

        assertTrue(result.isEmpty());
        verify(userRoleMapper, never()).listByRoleIds(any());
    }

    @Test
    void addWorkspaceRoleUsers_skipsExistingAssignments() {
        SysRole role = new SysRole();
        role.setId(roleId);
        when(roleMapper.selectById(roleId)).thenReturn(role);
        UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(workspaceUserMapper.existsByUserIdAndWorkspaceIdAndRole(userId, workspaceId, roleId)).thenReturn(true);

        roleService.addWorkspaceRoleUsers(roleId, List.of(userId), List.of(workspaceId));

        verify(workspaceUserMapper, never()).insert(any(WorkspaceUser.class));
    }

    @Test
    void addWorkspaceRoleUsers_success_insertsMembership() {
        SysRole role = new SysRole();
        role.setId(roleId);
        when(roleMapper.selectById(roleId)).thenReturn(role);
        UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(workspaceUserMapper.existsByUserIdAndWorkspaceIdAndRole(userId, workspaceId, roleId)).thenReturn(false);

        roleService.addWorkspaceRoleUsers(roleId, List.of(userId), List.of(workspaceId));

        ArgumentCaptor<WorkspaceUser> captor = ArgumentCaptor.forClass(WorkspaceUser.class);
        verify(workspaceUserMapper).insert(captor.capture());
        assertEquals(roleId, captor.getValue().getWorkspaceRole());
        assertEquals(workspaceId, captor.getValue().getWorkspaceId());
    }

    @Test
    void removeRoleUser_delegatesDelete() {
        roleService.removeRoleUser(roleId, userId);

        verify(userRoleMapper).deleteByUserIdAndRoleId(userId, roleId);
    }

    @Test
    void removeWorkspaceRoleUser_delegatesDelete() {
        UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000099");

        roleService.removeWorkspaceRoleUser(roleId, userId, workspaceId);

        verify(workspaceUserMapper).deleteByUserIdAndWorkspaceIdAndRole(userId, workspaceId, roleId);
    }

    @Test
    void getPermissionTable_groupsByModule_filtersNoParent() {
        SysPermission child = new SysPermission();
        child.setParentCode("root");
        child.setModule("测试模块");
        child.setCode("bug:edit");
        child.setName("编辑缺陷");
        SysPermission orphan = new SysPermission();
        orphan.setParentCode(null);
        orphan.setModule("孤立模块");
        orphan.setCode("orphan:code");
        when(permissionMapper.findByScopeOrdered("workspace")).thenReturn(List.of(child, orphan));

        List<PermissionTableRespDTO> result = roleService.getPermissionTable(Constants.RoleType.WORKSPACE);

        assertEquals(1, result.size());
        assertEquals("测试模块", result.get(0).getModule());
        assertEquals("bug:edit", result.get(0).getPermissions().get(0).getCode());
    }

    @Test
    void getPermissionTable_globalScope_whenSystemType() {
        when(permissionMapper.findByScopeOrdered("global")).thenReturn(List.of());

        roleService.getPermissionTable(Constants.RoleType.SYSTEM);

        verify(permissionMapper).findByScopeOrdered("global");
    }
}