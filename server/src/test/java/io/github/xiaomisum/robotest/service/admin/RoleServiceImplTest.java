package io.github.xiaomisum.robotest.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.convert.RoleConvertMapper;
import io.github.xiaomisum.robotest.model.convert.RoleConvertMapperImpl;
import io.github.xiaomisum.robotest.model.dto.request.admin.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.PermissionTableRespDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
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
    @Spy
    private RoleConvertMapper roleConvertMapper = new RoleConvertMapperImpl();
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
    void getRoleWorkspaceUsers_grantedAtTakesLatestUpdatedAt() {
        SysRole role = new SysRole();
        role.setId(roleId);
        when(roleMapper.selectById(roleId)).thenReturn(role);

        UUID ws1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID ws2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
        LocalDateTime earlier = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime later = LocalDateTime.of(2026, 3, 1, 10, 0);

        WorkspaceUser wu1 = new WorkspaceUser();
        wu1.setUserId(userId);
        wu1.setWorkspaceId(ws1);
        wu1.setUpdatedAt(earlier);
        WorkspaceUser wu2 = new WorkspaceUser();
        wu2.setUserId(userId);
        wu2.setWorkspaceId(ws2);
        wu2.setUpdatedAt(later);
        doReturn(List.of(wu1, wu2)).when(workspaceUserMapper)
                .selectList(any(SFunction.class), eq(roleId));

        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("tester");
        user.setName("测试员");
        when(userMapper.findPage(isNull(), isNull(), eq(List.of(userId)), eq(1), eq(20)))
                .thenReturn(new PageResult<>(List.of(user), 1L));

        Workspace wsEntity1 = new Workspace();
        wsEntity1.setId(ws1);
        wsEntity1.setName("空间一");
        Workspace wsEntity2 = new Workspace();
        wsEntity2.setId(ws2);
        wsEntity2.setName("空间二");
        when(workspaceMapper.listByIds(any())).thenReturn(List.of(wsEntity1, wsEntity2));

        PageParam pageParam = new PageParam();
        pageParam.setPageNo(1);
        pageParam.setPageSize(20);
        var result = roleService.getRoleWorkspaceUsers(roleId, pageParam);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals(2, result.getList().get(0).getWorkspaces().size());
        assertEquals(later, result.getList().get(0).getGrantedAt());
    }

    @Test
    void getRoleWorkspaceUsers_empty_returnsEmptyPage() {
        SysRole role = new SysRole();
        role.setId(roleId);
        when(roleMapper.selectById(roleId)).thenReturn(role);
        doReturn(List.of()).when(workspaceUserMapper)
                .selectList(any(SFunction.class), eq(roleId));

        PageParam pageParam = new PageParam();
        PageResult<RoleWorkspaceUserRespDTO> result = roleService.getRoleWorkspaceUsers(roleId, pageParam);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(userMapper, never()).findPage(any(), any(), any(), any(), any());
    }

    @Test
    void getRoleWorkspaceUsers_secondPage_aggregatesOnlyPageUsers() {
        SysRole role = new SysRole();
        role.setId(roleId);
        when(roleMapper.selectById(roleId)).thenReturn(role);

        UUID secondUserId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        LocalDateTime grantedAt = LocalDateTime.of(2026, 3, 1, 10, 0);
        WorkspaceUser firstMembership = new WorkspaceUser();
        firstMembership.setUserId(userId);
        firstMembership.setWorkspaceId(workspaceId);
        WorkspaceUser secondMembership = new WorkspaceUser();
        secondMembership.setUserId(secondUserId);
        secondMembership.setWorkspaceId(workspaceId);
        secondMembership.setUpdatedAt(grantedAt);
        doReturn(List.of(firstMembership, secondMembership)).when(workspaceUserMapper)
                .selectList(any(SFunction.class), eq(roleId));

        SysUser secondUser = new SysUser();
        secondUser.setId(secondUserId);
        secondUser.setUsername("second");
        secondUser.setName("第二页用户");
        when(userMapper.findPage(isNull(), isNull(), eq(List.of(userId, secondUserId)), eq(2), eq(1)))
                .thenReturn(new PageResult<>(List.of(secondUser), 2L));

        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setName("空间一");
        when(workspaceMapper.listByIds(List.of(workspaceId))).thenReturn(List.of(workspace));

        PageParam pageParam = new PageParam();
        pageParam.setPageNo(2);
        pageParam.setPageSize(1);
        PageResult<RoleWorkspaceUserRespDTO> result = roleService.getRoleWorkspaceUsers(roleId, pageParam);

        assertEquals(2L, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals(secondUserId, result.getList().get(0).getUserId());
        assertEquals(grantedAt, result.getList().get(0).getGrantedAt());
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
    void getPermissionTable_threeLevelGrouping_ordersBySort() {
        SysPermission spaceRoot = permission("ws-info", null, "空间信息", "我的空间", 1);
        SysPermission viewLeaf = permission("ws-info:view", "ws-info", "查看空间信息", "我的空间", 1);
        SysPermission editLeaf = permission("ws-info:edit", "ws-info", "编辑空间信息", "我的空间", 2);
        SysPermission caseRoot = permission("case", null, "测试用例", "功能测试", 5);
        SysPermission caseLeaf = permission("case:view", "case", "查看用例", "功能测试", 1);
        // 打乱返回顺序，验证展示序来自内存排序而非 SQL 返回序
        when(permissionMapper.findByScopeOrdered("workspace"))
                .thenReturn(List.of(editLeaf, caseRoot, viewLeaf, spaceRoot, caseLeaf));

        List<PermissionTableRespDTO> result = roleService.getPermissionTable(Constants.RoleType.WORKSPACE);

        assertEquals(2, result.size());
        // 一级序 = 组内最小根 sort：我的空间(1) 先于 功能测试(5)
        assertEquals("我的空间", result.get(0).getTopModule());
        assertEquals("功能测试", result.get(1).getTopModule());
        assertEquals("空间信息", result.get(0).getModules().get(0).getModule());
        // 根行不作为权限点下发，叶子按 sort 升序
        assertEquals(List.of("ws-info:view", "ws-info:edit"),
                result.get(0).getModules().get(0).getPermissions().stream()
                        .map(PermissionTableRespDTO.PermissionItem::getCode)
                        .toList());
        assertEquals("测试用例", result.get(1).getModules().get(0).getModule());
    }

    @Test
    void getPermissionTable_orphanLeaf_fallsBackToModuleAndSortsLast() {
        SysPermission caseRoot = permission("case", null, "测试用例", "功能测试", 5);
        SysPermission caseLeaf = permission("case:view", "case", "查看用例", "功能测试", 1);
        SysPermission orphanLeaf = permission("ghost:view", "ghost", "幽灵权限", "功能测试", 1);
        orphanLeaf.setModule("幽灵模块");
        when(permissionMapper.findByScopeOrdered("workspace"))
                .thenReturn(List.of(orphanLeaf, caseRoot, caseLeaf));

        List<PermissionTableRespDTO> result = roleService.getPermissionTable(Constants.RoleType.WORKSPACE);

        assertEquals(1, result.size());
        assertEquals("功能测试", result.get(0).getTopModule());
        assertEquals(2, result.get(0).getModules().size());
        assertEquals("测试用例", result.get(0).getModules().get(0).getModule());
        // 根缺失的孤儿组用叶子 module 兜底并排末尾，不打断整体分组
        assertEquals("幽灵模块", result.get(0).getModules().get(1).getModule());
        assertEquals("ghost:view", result.get(0).getModules().get(1).getPermissions().get(0).getCode());
    }

    @Test
    void getPermissionTable_globalScope_whenSystemType() {
        when(permissionMapper.findByScopeOrdered("global")).thenReturn(List.of());

        roleService.getPermissionTable(Constants.RoleType.SYSTEM);

        verify(permissionMapper).findByScopeOrdered("global");
    }

    private static SysPermission permission(String code, String parentCode, String name, String topModule, int sortOrder) {
        SysPermission permission = new SysPermission();
        permission.setCode(code);
        permission.setParentCode(parentCode);
        permission.setName(name);
        permission.setTopModule(topModule);
        permission.setSortOrder(sortOrder);
        return permission;
    }
}