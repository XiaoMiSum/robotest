package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceImplTest {

    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private WorkspaceServiceImpl workspaceService;

    private UUID workspaceId;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setName("WS 1");
        workspace.setStatus("active");
    }

    // ========== getWorkspaceDetail ==========

    @Test
    void getWorkspaceDetail_countsProjects() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);
        when(workspaceUserMapper.countByWorkspaceId(workspaceId)).thenReturn(3L);
        when(projectMapper.countByWorkspaceId(workspaceId)).thenReturn(5L);

        WorkspaceRespDTO result = workspaceService.getWorkspaceDetail(workspaceId);

        assertEquals(3L, result.getMemberCount());
        // projectCount 曾被硬编码为 0，须来自 project 表真实统计
        assertEquals(5L, result.getProjectCount());
    }

    @Test
    void getWorkspaceDetail_notFound_throws() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> workspaceService.getWorkspaceDetail(workspaceId));
    }

    // ========== getWorkspacePage ==========

    @Test
    void getWorkspacePage_countsProjects() {
        PageResult<Workspace> page = new PageResult<>(List.of(workspace), 1L);
        doReturn(page).when(workspaceMapper).findPage(any(PageParam.class), any(), any());
        when(workspaceUserMapper.countByWorkspaceId(workspaceId)).thenReturn(2L);
        when(projectMapper.countByWorkspaceId(workspaceId)).thenReturn(7L);

        PageResult<WorkspaceRespDTO> result = workspaceService.getWorkspacePage(null, null, 1, 20);

        assertEquals(1, result.getList().size());
        assertEquals(2L, result.getList().get(0).getMemberCount());
        assertEquals(7L, result.getList().get(0).getProjectCount());
    }

    // ========== createWorkspace ==========

    @Test
    void createWorkspace_success_insertsActive() {
        when(workspaceMapper.findByName(anyString())).thenReturn(null);
        doAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            w.setId(UUID.randomUUID());
            return 1;
        }).when(workspaceMapper).insert(any(Workspace.class));

        WorkspaceCreateReqDTO req = new WorkspaceCreateReqDTO();
        req.setName("新空间");
        req.setDescription("desc");
        String id = workspaceService.createWorkspace(req);

        assertNotNull(id);
        ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceMapper).insert(captor.capture());
        assertEquals("新空间", captor.getValue().getName());
        assertEquals(Constants.Status.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void createWorkspace_duplicateName_throws() {
        when(workspaceMapper.findByName("重复")).thenReturn(workspace);

        WorkspaceCreateReqDTO req = new WorkspaceCreateReqDTO();
        req.setName("重复");

        assertThrows(ServiceException.class, () -> workspaceService.createWorkspace(req));
        verify(workspaceMapper, never()).insert(any(Workspace.class));
    }

    // ========== updateWorkspace ==========

    @Test
    void updateWorkspace_success_partialUpdateCarrier() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);
        when(workspaceMapper.findByName(anyString())).thenReturn(null);
        when(workspaceUserMapper.countByWorkspaceId(workspaceId)).thenReturn(0L);
        when(projectMapper.countByWorkspaceId(workspaceId)).thenReturn(0L);

        WorkspaceUpdateReqDTO req = new WorkspaceUpdateReqDTO();
        req.setName("改名");
        workspaceService.updateWorkspace(workspaceId, req);

        ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceMapper).updateById(captor.capture());
        // C9 部分更新载体：仅携 id + 变更字段，不回写 status
        assertEquals(workspaceId, captor.getValue().getId());
        assertEquals("改名", captor.getValue().getName());
        assertNull(captor.getValue().getStatus());
    }

    @Test
    void updateWorkspace_nameConflictAnotherWorkspace_throws() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);
        Workspace other = new Workspace();
        other.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        other.setName("占用名");
        when(workspaceMapper.findByName("占用名")).thenReturn(other);

        WorkspaceUpdateReqDTO req = new WorkspaceUpdateReqDTO();
        req.setName("占用名");

        assertThrows(ServiceException.class, () -> workspaceService.updateWorkspace(workspaceId, req));
        verify(workspaceMapper, never()).updateById(any(Workspace.class));
    }

    @Test
    void updateWorkspace_dissolved_throws() {
        Workspace dissolved = new Workspace();
        dissolved.setId(workspaceId);
        dissolved.setStatus(Constants.Status.DISSOLVED);
        when(workspaceMapper.selectById(workspaceId)).thenReturn(dissolved);

        assertThrows(ServiceException.class,
                () -> workspaceService.updateWorkspace(workspaceId, new WorkspaceUpdateReqDTO()));
        verify(workspaceMapper, never()).updateById(any(Workspace.class));
    }

    // ========== dissolveWorkspace ==========

    @Test
    void dissolveWorkspace_success_deletesMembers() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);

        workspaceService.dissolveWorkspace(workspaceId);

        ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceMapper).updateById(captor.capture());
        assertEquals(Constants.Status.DISSOLVED, captor.getValue().getStatus());
        verify(workspaceUserMapper).deleteByWorkspaceId(workspaceId);
    }

    @Test
    void dissolveWorkspace_notFound_throws() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(null);

        assertThrows(ServiceException.class, () -> workspaceService.dissolveWorkspace(workspaceId));
        verify(workspaceMapper, never()).updateById(any(Workspace.class));
    }

    // ========== addWorkspaceMembers ==========

    @Test
    void addWorkspaceMembers_defaultRoleWhenNull() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SysUser user = new SysUser();
        user.setId(target);
        user.setStatus(Constants.Status.ACTIVE);
        when(userMapper.selectById(target)).thenReturn(user);
        when(workspaceUserMapper.existsByWorkspaceIdAndUserId(workspaceId, target)).thenReturn(false);

        WorkspaceMembersAddReqDTO.MemberItem member = new WorkspaceMembersAddReqDTO.MemberItem();
        member.setUserId(target);
        List<String> skipped = workspaceService.addWorkspaceMembers(workspaceId, List.of(member));

        assertTrue(skipped.isEmpty());
        ArgumentCaptor<WorkspaceUser> captor = ArgumentCaptor.forClass(WorkspaceUser.class);
        verify(workspaceUserMapper).insert(captor.capture());
        assertEquals(Constants.WorkspaceRole.MEMBER_ID, captor.getValue().getWorkspaceRole());
    }

    @Test
    void addWorkspaceMembers_existingSkippedAndReported() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SysUser user = new SysUser();
        user.setId(target);
        user.setStatus(Constants.Status.ACTIVE);
        when(userMapper.selectById(target)).thenReturn(user);
        when(workspaceUserMapper.existsByWorkspaceIdAndUserId(workspaceId, target)).thenReturn(true);

        WorkspaceMembersAddReqDTO.MemberItem member = new WorkspaceMembersAddReqDTO.MemberItem();
        member.setUserId(target);
        List<String> skipped = workspaceService.addWorkspaceMembers(workspaceId, List.of(member));

        assertEquals(List.of(target.toString()), skipped);
        verify(workspaceUserMapper, never()).insert(any(WorkspaceUser.class));
    }

    // ========== updateWorkspaceMemberRole ==========

    @Test
    void updateWorkspaceMemberRole_keepLastAdmin_throws() {
        WorkspaceUser admin = new WorkspaceUser();
        admin.setId(UUID.randomUUID());
        admin.setUserId(UUID.randomUUID());
        admin.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, admin.getUserId())).thenReturn(admin);
        when(workspaceUserMapper.countByWorkspaceIdAndRole(workspaceId, Constants.WorkspaceRole.ADMIN_ID)).thenReturn(1L);

        assertThrows(ServiceException.class,
                () -> workspaceService.updateWorkspaceMemberRole(workspaceId, admin.getUserId(), Constants.WorkspaceRole.MEMBER_ID));
        verify(workspaceUserMapper, never()).updateById(any(WorkspaceUser.class));
    }

    // ========== removeWorkspaceMember ==========

    @Test
    void removeWorkspaceMember_nonAdmin_success() {
        WorkspaceUser member = new WorkspaceUser();
        member.setId(UUID.randomUUID());
        member.setUserId(UUID.randomUUID());
        member.setWorkspaceRole(Constants.WorkspaceRole.MEMBER_ID);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, member.getUserId())).thenReturn(member);

        workspaceService.removeWorkspaceMember(workspaceId, member.getUserId());

        verify(workspaceUserMapper).deleteById(member.getId());
    }

    @Test
    void removeWorkspaceMember_lastAdmin_throws() {
        WorkspaceUser admin = new WorkspaceUser();
        admin.setId(UUID.randomUUID());
        admin.setUserId(UUID.randomUUID());
        admin.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, admin.getUserId())).thenReturn(admin);
        when(workspaceUserMapper.countByWorkspaceIdAndRole(workspaceId, Constants.WorkspaceRole.ADMIN_ID)).thenReturn(1L);

        assertThrows(ServiceException.class,
                () -> workspaceService.removeWorkspaceMember(workspaceId, admin.getUserId()));
        verify(workspaceUserMapper, never()).deleteById(any());
    }

    // ========== getWorkspaceMembers ==========

    @Test
    void getWorkspaceMembers_skipsOrphanUsers() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);
        WorkspaceUser wu = new WorkspaceUser();
        wu.setUserId(UUID.randomUUID());
        wu.setWorkspaceRole(Constants.WorkspaceRole.MEMBER_ID);
        PageResult<WorkspaceUser> page = new PageResult<>(List.of(wu), 1L);
        doReturn(page).when(workspaceUserMapper).findPageByWorkspaceId(any(PageParam.class), eq(workspaceId));
        when(userMapper.selectById(wu.getUserId())).thenReturn(null);

        PageResult<WorkspaceMemberRespDTO> result = workspaceService.getWorkspaceMembers(workspaceId, 1, 20);

        // 孤儿用户（SysUser 已删）被过滤掉，不产生脏记录
        assertEquals(1L, result.getTotal());
        assertEquals(0, result.getList().size());
    }
}
