package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserBatchStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.UserRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.UserSimpleRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private UUID userId;
    private SysUser user;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        user = new SysUser();
        user.setId(userId);
        user.setUsername("tester");
        user.setName("测试员");
        user.setEmail("tester@example.com");
        user.setStatus(Constants.Status.ACTIVE);
        user.setPasswordHash("old-hash");
    }

    /** convertToUserRespDTO 的聚合查询空结果桩：detail/getUserPage/update 成功路径需要 */
    private void stubEmptyAggregations() {
        when(userRoleMapper.listByUserId(userId)).thenReturn(List.of());
        when(workspaceUserMapper.listByUserId(userId)).thenReturn(List.of());
    }

    // ========== getUserPage ==========

    @Test
    void getUserPage_success_noRoleFilter() {
        stubEmptyAggregations();
        when(userMapper.findPage(eq("tester"), isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(new PageResult<>(List.of(user), 1L));

        PageResult<UserRespDTO> result = userService.getUserPage("tester", null, null, 1, 10);

        assertEquals(1L, result.getTotal());
        assertEquals("tester", result.getList().get(0).getUsername());
        assertEquals("active", result.getList().get(0).getStatus());
    }

    @Test
    void getUserPage_roleFiltered_returnsOnlyRoleMembers() {
        SysUserRole role = new SysUserRole();
        role.setUserId(userId);
        role.setRoleId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        when(userRoleMapper.listByRoleId(role.getRoleId())).thenReturn(List.of(role));
        stubEmptyAggregations();
        when(userMapper.findPage(isNull(), isNull(), eq(List.of(userId)), eq(1), eq(20)))
                .thenReturn(new PageResult<>(List.of(user), 1L));

        PageResult<UserRespDTO> result = userService.getUserPage(null, null, role.getRoleId(), 1, 20);

        assertEquals(1L, result.getTotal());
        assertEquals(userId, result.getList().get(0).getId());
        verify(userMapper).findPage(isNull(), isNull(), eq(List.of(userId)), eq(1), eq(20));
    }

    @Test
    void getUserPage_roleWithoutMembers_returnsEmptyEarly() {
        UUID roleId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        when(userRoleMapper.listByRoleId(roleId)).thenReturn(List.of());

        PageResult<UserRespDTO> result = userService.getUserPage(null, null, roleId, 1, 20);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(userMapper, never()).findPage(any(), any(), any(), any(), any());
    }

    @Test
    void getUserPage_aggregatesRolesAndWorkspaces() {
        when(userMapper.findPage(isNull(), isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(new PageResult<>(List.of(user), 1L));

        SysUserRole role = new SysUserRole();
        role.setUserId(userId);
        role.setRoleId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        when(userRoleMapper.listByUserId(userId)).thenReturn(List.of(role));

        SysRole sysRole = new SysRole();
        sysRole.setId(role.getRoleId());
        sysRole.setName("QA");
        sysRole.setType(Constants.RoleType.SYSTEM);
        when(roleMapper.listByIds(List.of(role.getRoleId()))).thenReturn(List.of(sysRole));

        WorkspaceUser wu = new WorkspaceUser();
        wu.setUserId(userId);
        wu.setWorkspaceId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        wu.setWorkspaceRole(Constants.WorkspaceRole.MEMBER_ID);
        when(workspaceUserMapper.listByUserId(userId)).thenReturn(List.of(wu));

        Workspace ws = new Workspace();
        ws.setId(wu.getWorkspaceId());
        ws.setName("QA 团队");
        when(workspaceMapper.listByIds(List.of(wu.getWorkspaceId()))).thenReturn(List.of(ws));

        PageResult<UserRespDTO> result = userService.getUserPage(null, null, null, 1, 10);

        assertEquals(1, result.getList().get(0).getRoles().size());
        assertEquals("QA", result.getList().get(0).getRoles().get(0).getName());
        assertEquals(1, result.getList().get(0).getWorkspaces().size());
        assertEquals("QA 团队", result.getList().get(0).getWorkspaces().get(0).getName());
        assertEquals(Constants.WorkspaceRole.MEMBER_ID.toString(),
                result.getList().get(0).getWorkspaces().get(0).getWorkspaceRole());
    }

    // ========== getUserSimpleList ==========

    @Test
    void getUserSimpleList_returnsActiveOnlyByIdAndName() {
        when(userMapper.listActiveByKeyword("tester")).thenReturn(List.of(user));

        List<UserSimpleRespDTO> result = userService.getUserSimpleList("tester");

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getId());
        assertEquals("测试员", result.get(0).getName());
    }

    // ========== getUserDetail ==========

    @Test
    void getUserDetail_success() {
        when(userMapper.selectById(userId)).thenReturn(user);
        stubEmptyAggregations();

        UserRespDTO result = userService.getUserDetail(userId);

        assertEquals(userId, result.getId());
        assertEquals("tester", result.getUsername());
    }

    @Test
    void getUserDetail_userNotFound_throws() {
        when(userMapper.selectById(userId)).thenReturn(null);

        assertThrows(ServiceException.class, () -> userService.getUserDetail(userId));
    }

    // ========== createUser ==========

    @Test
    void createUser_success_encodesPasswordAndAssignsRoles() {
        UUID roleId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        when(userMapper.findByUsername("newuser")).thenReturn(null);
        when(userMapper.findByEmail("new@example.com")).thenReturn(null);
        when(passwordEncoder.encode("Pass123!")).thenReturn("encoded-hash");
        doAnswer(invocation -> {
            SysUser u = invocation.getArgument(0);
            u.setId(userId);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        UserCreateReqDTO req = new UserCreateReqDTO();
        req.setUsername("newuser");
        req.setName("新用户");
        req.setEmail("new@example.com");
        req.setPassword("Pass123!");
        req.setRoleIds(List.of(roleId));

        String id = userService.createUser(req);

        assertEquals(userId.toString(), id);
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("encoded-hash", captor.getValue().getPasswordHash());
        assertEquals(Constants.Status.ACTIVE, captor.getValue().getStatus());
        ArgumentCaptor<SysUserRole> roleCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper).insert(roleCaptor.capture());
        assertEquals(userId, roleCaptor.getValue().getUserId());
        assertEquals(roleId, roleCaptor.getValue().getRoleId());
    }

    @Test
    void createUser_usernameExists_throws() {
        when(userMapper.findByUsername("newuser")).thenReturn(user);

        UserCreateReqDTO req = new UserCreateReqDTO();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        req.setPassword("Pass123!");

        assertThrows(ServiceException.class, () -> userService.createUser(req));
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void createUser_emailExists_throws() {
        when(userMapper.findByUsername("newuser")).thenReturn(null);
        when(userMapper.findByEmail("dup@example.com")).thenReturn(user);

        UserCreateReqDTO req = new UserCreateReqDTO();
        req.setUsername("newuser");
        req.setEmail("dup@example.com");
        req.setPassword("Pass123!");

        assertThrows(ServiceException.class, () -> userService.createUser(req));
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void createUser_noRoleIds_skipsUserRoleInsert() {
        when(userMapper.findByUsername("newuser")).thenReturn(null);
        when(userMapper.findByEmail("new@example.com")).thenReturn(null);
        when(passwordEncoder.encode("Pass123!")).thenReturn("encoded-hash");
        doAnswer(invocation -> {
            SysUser u = invocation.getArgument(0);
            u.setId(userId);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        UserCreateReqDTO req = new UserCreateReqDTO();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        req.setPassword("Pass123!");

        userService.createUser(req);

        verify(userMapper).findByEmail("new@example.com");
        verify(userRoleMapper, never()).insert(any(SysUserRole.class));
    }

    // ========== updateUser ==========

    @Test
    void updateUser_success_partialUpdateCarrier() {
        when(userMapper.selectById(userId)).thenReturn(user);
        stubEmptyAggregations();

        UserUpdateReqDTO req = new UserUpdateReqDTO();
        req.setName("改名后的名字");
        req.setEmail("new@example.com");

        userService.updateUser(userId, req);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(userId, captor.getValue().getId());
        assertEquals("改名后的名字", captor.getValue().getName());
        assertEquals("new@example.com", captor.getValue().getEmail());
        // C9 部分更新：状态/密码不回写
        assertNull(captor.getValue().getStatus());
        assertNull(captor.getValue().getPasswordHash());
    }

    @Test
    void updateUser_roleReplace_rebindsRoles() {
        UUID oldRoleId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID newRoleId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        when(userMapper.selectById(userId)).thenReturn(user);
        stubEmptyAggregations();

        UserUpdateReqDTO req = new UserUpdateReqDTO();
        req.setRoleIds(List.of(newRoleId));

        userService.updateUser(userId, req);

        verify(userRoleMapper).deleteByUserId(userId);
        ArgumentCaptor<SysUserRole> roleCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper).insert(roleCaptor.capture());
        assertEquals(newRoleId, roleCaptor.getValue().getRoleId());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void updateUser_userNotFound_throws() {
        when(userMapper.selectById(userId)).thenReturn(null);

        UserUpdateReqDTO req = new UserUpdateReqDTO();
        req.setName("任意");

        assertThrows(ServiceException.class, () -> userService.updateUser(userId, req));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    // ========== updateUserStatus ==========

    @Test
    void updateUserStatus_disable_setsStatusCarrier() {
        when(userMapper.selectById(userId)).thenReturn(user);
        stubEmptyAggregations();

        userService.updateUserStatus(userId, Constants.Status.DISABLED);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(userId, captor.getValue().getId());
        assertEquals(Constants.Status.DISABLED, captor.getValue().getStatus());
    }

    @Test
    void updateUserStatus_userNotFound_throws() {
        when(userMapper.selectById(userId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> userService.updateUserStatus(userId, Constants.Status.DISABLED));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    // ========== batchUpdateStatus ==========

    @Test
    void batchUpdateStatus_skipsMissingUsers() {
        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(userMapper.selectById(userId)).thenReturn(user);
        when(userMapper.selectById(missingId)).thenReturn(null);

        UserBatchStatusReqDTO req = new UserBatchStatusReqDTO();
        req.setUserIds(List.of(userId, missingId));
        req.setStatus(Constants.Status.DISABLED);

        userService.batchUpdateStatus(req);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper, times(1)).updateById(captor.capture());
        assertEquals(userId, captor.getValue().getId());
        assertEquals(Constants.Status.DISABLED, captor.getValue().getStatus());
    }

    // ========== resetPassword ==========

    @Test
    void resetPassword_success_encodesAndUpdates() {
        when(userMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("new-encoded-hash");

        userService.resetPassword(userId, "NewPass123!");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(userId, captor.getValue().getId());
        assertEquals("new-encoded-hash", captor.getValue().getPasswordHash());
        // C9 部分更新：其他字段不回写
        assertNull(captor.getValue().getUsername());
    }

    @Test
    void resetPassword_userNotFound_throws() {
        when(userMapper.selectById(userId)).thenReturn(null);

        assertThrows(ServiceException.class, () -> userService.resetPassword(userId, "NewPass123!"));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    // ========== changePassword ==========

    @Test
    void changePassword_success() {
        when(userMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.matches("OldPass123!", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass456!")).thenReturn("new-hash");

        userService.changePassword(userId, "OldPass123!", "NewPass456!");

        // 更新载体仅携带 id + 新密码，不再回写查询实体
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(userId, captor.getValue().getId());
        assertEquals("new-hash", captor.getValue().getPasswordHash());
    }

    @Test
    void changePassword_oldPasswordWrong_throws() {
        when(userMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.matches("WrongOld!", "old-hash")).thenReturn(false);

        assertThrows(ServiceException.class,
                () -> userService.changePassword(userId, "WrongOld!", "NewPass456!"));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void changePassword_userNotFound_throws() {
        when(userMapper.selectById(userId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> userService.changePassword(userId, "OldPass123!", "NewPass456!"));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }
}