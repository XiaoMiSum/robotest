package io.github.xiaomisum.robotest.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.admin.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.repository.admin.SysPermissionMapper;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}