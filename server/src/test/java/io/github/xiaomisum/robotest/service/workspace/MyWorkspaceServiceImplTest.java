package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.workspace.MyWorkspaceQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyScopeCountsRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.MyWorkspaceQueryMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyWorkspaceServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private MyWorkspaceQueryMapper myWorkspaceQueryMapper;

    @InjectMocks
    private MyWorkspaceServiceImpl myWorkspaceService;

    @Test
    void getMyWorkspaces_appliesFiltersAndReturnsRoleAndCaseStats() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID defaultProjectId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        MyWorkspaceQueryReqDTO query = new MyWorkspaceQueryReqDTO();
        query.setKeyword("  质量  ");
        query.setScope("managed");
        query.setPageNo(2);
        query.setPageSize(12);

        WorkspaceMyRespDTO row = new WorkspaceMyRespDTO();
        row.setId(workspaceId);
        row.setName("质量中台");
        row.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID.toString());
        row.setWorkspaceRoleName("管理员");
        row.setDefaultProjectId(defaultProjectId);
        row.setDefaultProjectName("默认项目");
        row.setMemberCount(4L);
        row.setProjectCount(6L);
        row.setTestCaseCount(1024L);
        row.setStatus(Constants.Status.ACTIVE);
        row.setCreatedAt(LocalDateTime.of(2026, 9, 18, 2, 24));
        row.setLastAccessedAt(LocalDateTime.of(2026, 9, 23, 8, 30));

        when(myWorkspaceQueryMapper.selectPage(userId, "质量", "managed",
                Constants.WorkspaceRole.ADMIN_ID, 12L, 12)).thenReturn(List.of(row));
        when(myWorkspaceQueryMapper.count(userId, "质量", "managed",
                Constants.WorkspaceRole.ADMIN_ID)).thenReturn(1L);

        PageResult<WorkspaceMyRespDTO> result = myWorkspaceService.getMyWorkspaces(userId, query);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals("管理员", result.getList().get(0).getWorkspaceRoleName());
        assertEquals(4L, result.getList().get(0).getMemberCount());
        assertEquals(6L, result.getList().get(0).getProjectCount());
        assertEquals(1024L, result.getList().get(0).getTestCaseCount());
        assertEquals("默认项目", result.getList().get(0).getDefaultProjectName());
        verify(myWorkspaceQueryMapper).selectPage(userId, "质量", "managed",
                Constants.WorkspaceRole.ADMIN_ID, 12L, 12);
        verify(myWorkspaceQueryMapper).count(userId, "质量", "managed",
                Constants.WorkspaceRole.ADMIN_ID);
    }

    @Test
    void getMyWorkspaces_archivedScopeReturnsPageAndCountsAreLoadedSeparately() {
        UUID userId = UUID.randomUUID();
        MyWorkspaceQueryReqDTO query = new MyWorkspaceQueryReqDTO();
        query.setKeyword("旧空间");
        query.setScope("archived");

        WorkspaceMyScopeCountsRespDTO counts = new WorkspaceMyScopeCountsRespDTO();
        counts.setAll(3L);
        counts.setManaged(1L);
        counts.setArchived(2L);
        when(myWorkspaceQueryMapper.selectPage(userId, "旧空间", "archived",
                Constants.WorkspaceRole.ADMIN_ID, 0L, 12)).thenReturn(List.of());
        when(myWorkspaceQueryMapper.count(userId, "旧空间", "archived",
                Constants.WorkspaceRole.ADMIN_ID)).thenReturn(2L);
        when(myWorkspaceQueryMapper.countScopes(userId, "旧空间",
                Constants.WorkspaceRole.ADMIN_ID)).thenReturn(counts);

        PageResult<WorkspaceMyRespDTO> result = myWorkspaceService.getMyWorkspaces(userId, query);
        WorkspaceMyScopeCountsRespDTO countsResult = myWorkspaceService.getMyWorkspaceCounts(userId, "旧空间");

        assertTrue(result.getList().isEmpty());
        assertEquals(2L, result.getTotal());
        assertEquals(3L, countsResult.getAll());
        assertEquals(1L, countsResult.getManaged());
        assertEquals(2L, countsResult.getArchived());
        verify(myWorkspaceQueryMapper).countScopes(userId, "旧空间",
                Constants.WorkspaceRole.ADMIN_ID);
    }

    @Test
    void getMyWorkspaces_allScopeUsesAllMemberships() {
        UUID userId = UUID.randomUUID();
        MyWorkspaceQueryReqDTO query = new MyWorkspaceQueryReqDTO();

        when(myWorkspaceQueryMapper.selectPage(userId, null, "all",
                Constants.WorkspaceRole.ADMIN_ID, 0L, 12)).thenReturn(List.of());
        when(myWorkspaceQueryMapper.count(userId, null, "all",
                Constants.WorkspaceRole.ADMIN_ID)).thenReturn(2L);

        PageResult<WorkspaceMyRespDTO> result = myWorkspaceService.getMyWorkspaces(userId, query);

        assertEquals(2L, result.getTotal());
        verify(myWorkspaceQueryMapper).count(userId, null, "all",
                Constants.WorkspaceRole.ADMIN_ID);
    }

    @Test
    void getMyWorkspaces_invalidScope_throwsValidationError() {
        MyWorkspaceQueryReqDTO query = new MyWorkspaceQueryReqDTO();
        query.setScope("unknown");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> myWorkspaceService.getMyWorkspaces(UUID.randomUUID(), query));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void getMyWorkspaces_invalidPaging_throwsBeforeQuery() {
        MyWorkspaceQueryReqDTO query = new MyWorkspaceQueryReqDTO();
        query.setPageNo(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> myWorkspaceService.getMyWorkspaces(UUID.randomUUID(), query));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
        verifyNoInteractions(myWorkspaceQueryMapper);
    }

    @Test
    void setActiveWorkspace_missingContextHeader_throwsBeforeQueries() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> myWorkspaceService.setActiveWorkspace(UUID.randomUUID(), null));

        assertEquals(ErrorCodeConstants.CONTEXT_HEADER_MISSING.code(), exception.getCode());
        verifyNoInteractions(userMapper, workspaceMapper, workspaceUserMapper);
    }

    @Test
    void setActiveWorkspace_updatesBothRowsWithPartialPayloads() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        LocalDateTime previousAccess = LocalDateTime.now().minusDays(1);

        SysUser user = new SysUser();
        user.setId(userId);
        user.setLastActiveWorkspaceId(workspaceId.toString());
        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setStatus(Constants.Status.ACTIVE);
        WorkspaceUser membership = new WorkspaceUser();
        membership.setId(membershipId);
        membership.setUserId(userId);
        membership.setWorkspaceId(workspaceId);
        membership.setLastAccessedAt(previousAccess);

        when(userMapper.selectById(userId)).thenReturn(user);
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(membership);

        myWorkspaceService.setActiveWorkspace(userId, workspaceId);

        ArgumentCaptor<SysUser> userUpdateCaptor = ArgumentCaptor.forClass(SysUser.class);
        ArgumentCaptor<WorkspaceUser> membershipUpdateCaptor = ArgumentCaptor.forClass(WorkspaceUser.class);
        verify(userMapper).updateById(userUpdateCaptor.capture());
        verify(workspaceUserMapper).updateById(membershipUpdateCaptor.capture());

        SysUser userUpdate = userUpdateCaptor.getValue();
        assertEquals(userId, userUpdate.getId());
        assertEquals(workspaceId.toString(), userUpdate.getLastActiveWorkspaceId());
        assertNull(userUpdate.getUsername());
        assertNull(userUpdate.getName());

        WorkspaceUser membershipUpdate = membershipUpdateCaptor.getValue();
        assertEquals(membershipId, membershipUpdate.getId());
        assertNotNull(membershipUpdate.getLastAccessedAt());
        assertTrue(membershipUpdate.getLastAccessedAt().isAfter(previousAccess));
        assertNull(membershipUpdate.getUserId());
        assertNull(membershipUpdate.getWorkspaceId());
        assertNull(membershipUpdate.getWorkspaceRole());
    }

    @Test
    void setActiveWorkspace_nonMember_rejectsWithoutUpdates() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setStatus(Constants.Status.ACTIVE);
        when(userMapper.selectById(userId)).thenReturn(user);
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> myWorkspaceService.setActiveWorkspace(userId, workspaceId));

        assertEquals(ErrorCodeConstants.NO_PERMISSION.code(), exception.getCode());
        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.any(SysUser.class));
        verify(workspaceUserMapper, never()).updateById(org.mockito.ArgumentMatchers.any(WorkspaceUser.class));
    }

    @Test
    void setActiveWorkspace_dissolvedWorkspace_rejectsWithoutUpdates() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setStatus(Constants.Status.DISSOLVED);
        WorkspaceUser membership = new WorkspaceUser();
        membership.setId(UUID.randomUUID());
        when(userMapper.selectById(userId)).thenReturn(user);
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(membership);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> myWorkspaceService.setActiveWorkspace(userId, workspaceId));

        assertEquals(ErrorCodeConstants.WORKSPACE_DISSOLVED.code(), exception.getCode());
        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.any(SysUser.class));
        verify(workspaceUserMapper, never()).updateById(org.mockito.ArgumentMatchers.any(WorkspaceUser.class));
    }
}
