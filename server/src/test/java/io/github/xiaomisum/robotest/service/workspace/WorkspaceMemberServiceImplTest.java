package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberAddResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceMemberServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;

    @InjectMocks
    private WorkspaceMemberServiceImpl memberService;

    private final UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private final UUID adminId = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private final UUID targetUserId = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    private WorkspaceUser adminUser() {
        WorkspaceUser wu = new WorkspaceUser();
        wu.setId(UUID.randomUUID());
        wu.setUserId(adminId);
        wu.setWorkspaceId(workspaceId);
        wu.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        return wu;
    }

    private WorkspaceUser memberUser(UUID userId) {
        WorkspaceUser wu = new WorkspaceUser();
        wu.setId(UUID.randomUUID());
        wu.setUserId(userId);
        wu.setWorkspaceId(workspaceId);
        wu.setWorkspaceRole(Constants.WorkspaceRole.MEMBER_ID);
        return wu;
    }

    // ========== getMemberPage ==========

    @Test
    void getMemberPage_appliesWorkspaceRoleFilterAndReturnsDisplayName() {
        WorkspaceUser targetMember = memberUser(targetUserId);
        targetMember.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        SysUser targetUser = activeUser(targetUserId);
        targetUser.setName("李四");
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(adminUser());
        when(workspaceUserMapper.findPageByWorkspaceIdAndUserIds(
                any(PageParam.class), eq(workspaceId), isNull(), eq(Constants.WorkspaceRole.ADMIN_ID)))
                .thenReturn(new PageResult<>(List.of(targetMember), 1L));
        when(userMapper.listByIds(List.of(targetUserId))).thenReturn(List.of(targetUser));

        PageResult<WorkspaceMemberRespDTO> result = memberService.getMemberPage(
                adminId, workspaceId, null, Constants.WorkspaceRole.ADMIN_ID, 1, 20);

        assertEquals(1L, result.getTotal());
        assertEquals("李四", result.getList().get(0).getName());
        assertEquals(targetUserId, result.getList().get(0).getUserId());
    }

    // ========== addMembers ==========

    @Test
    void addMembers_notAdmin_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> memberService.addMembers(adminId, workspaceId, new WorkspaceMembersAddReqDTO()));
        verify(workspaceUserMapper, never()).insert(any(WorkspaceUser.class));
    }

    @Test
    void addMembers_success_countsAndInsertsWithDefaultRole() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(adminUser());
        SysUser target = activeUser(targetUserId);
        when(userMapper.selectById(targetUserId)).thenReturn(target);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(null);

        WorkspaceMembersAddReqDTO req = new WorkspaceMembersAddReqDTO();
        WorkspaceMembersAddReqDTO.MemberItem item = new WorkspaceMembersAddReqDTO.MemberItem();
        item.setUserId(targetUserId);
        req.setMembers(List.of(item));

        WorkspaceMemberAddResultRespDTO result = memberService.addMembers(adminId, workspaceId, req);

        assertEquals(Integer.valueOf(1), result.getSuccessCount());
        assertTrue(result.getSkippedUserIds().isEmpty());
        ArgumentCaptor<WorkspaceUser> captor = ArgumentCaptor.forClass(WorkspaceUser.class);
        verify(workspaceUserMapper).insert(captor.capture());
        assertEquals(targetUserId, captor.getValue().getUserId());
        assertEquals(workspaceId, captor.getValue().getWorkspaceId());
        // 未指定角色时默认 MEMBER_ID
        assertEquals(Constants.WorkspaceRole.MEMBER_ID, captor.getValue().getWorkspaceRole());
    }

    @Test
    void addMembers_inactiveUser_skippedSilently() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(adminUser());
        SysUser target = activeUser(targetUserId);
        target.setStatus(Constants.Status.DISABLED);
        when(userMapper.selectById(targetUserId)).thenReturn(target);

        WorkspaceMembersAddReqDTO req = new WorkspaceMembersAddReqDTO();
        WorkspaceMembersAddReqDTO.MemberItem item = new WorkspaceMembersAddReqDTO.MemberItem();
        item.setUserId(targetUserId);
        req.setMembers(List.of(item));

        WorkspaceMemberAddResultRespDTO result = memberService.addMembers(adminId, workspaceId, req);

        assertEquals(Integer.valueOf(0), result.getSuccessCount());
        assertTrue(result.getSkippedUserIds().isEmpty());
        verify(workspaceUserMapper, never()).insert(any(WorkspaceUser.class));
    }

    @Test
    void addMembers_alreadyMember_reportedAsSkipped() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(adminUser());
        SysUser target = activeUser(targetUserId);
        when(userMapper.selectById(targetUserId)).thenReturn(target);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(memberUser(targetUserId));

        WorkspaceMembersAddReqDTO req = new WorkspaceMembersAddReqDTO();
        WorkspaceMembersAddReqDTO.MemberItem item = new WorkspaceMembersAddReqDTO.MemberItem();
        item.setUserId(targetUserId);
        req.setMembers(List.of(item));

        WorkspaceMemberAddResultRespDTO result = memberService.addMembers(adminId, workspaceId, req);

        assertEquals(Integer.valueOf(0), result.getSuccessCount());
        assertEquals(List.of(targetUserId), result.getSkippedUserIds());
        verify(workspaceUserMapper, never()).insert(any(WorkspaceUser.class));
    }

    // ========== updateMemberRole ==========

    @Test
    void updateMemberRole_success_partialUpdate() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(adminUser());
        WorkspaceUser target = memberUser(targetUserId);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(target);

        memberService.updateMemberRole(adminId, workspaceId, targetUserId, Constants.WorkspaceRole.ADMIN_ID);

        ArgumentCaptor<WorkspaceUser> captor = ArgumentCaptor.forClass(WorkspaceUser.class);
        verify(workspaceUserMapper).updateById(captor.capture());
        // C9 部分更新载体：仅携带 id + 新角色
        assertEquals(target.getId(), captor.getValue().getId());
        assertEquals(Constants.WorkspaceRole.ADMIN_ID, captor.getValue().getWorkspaceRole());
    }

    @Test
    void updateMemberRole_downgradeLastAdmin_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(adminUser());
        WorkspaceUser target = new WorkspaceUser();
        target.setId(UUID.randomUUID());
        target.setUserId(targetUserId);
        target.setWorkspaceId(workspaceId);
        target.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(target);
        when(workspaceUserMapper.countByWorkspaceIdAndRole(workspaceId, Constants.WorkspaceRole.ADMIN_ID)).thenReturn(1L);

        assertThrows(ServiceException.class,
                () -> memberService.updateMemberRole(adminId, workspaceId, targetUserId, Constants.WorkspaceRole.MEMBER_ID));
        verify(workspaceUserMapper, never()).updateById(any(WorkspaceUser.class));
    }

    @Test
    void updateMemberRole_notAdmin_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(memberUser(adminId));

        assertThrows(ServiceException.class,
                () -> memberService.updateMemberRole(adminId, workspaceId, targetUserId, Constants.WorkspaceRole.MEMBER_ID));
        verify(workspaceUserMapper, never()).updateById(any(WorkspaceUser.class));
    }

    // ========== removeMember ==========

    @Test
    void removeMember_notAdminAndNotSelf_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(memberUser(adminId));

        assertThrows(ServiceException.class,
                () -> memberService.removeMember(adminId, workspaceId, targetUserId));
        verify(workspaceUserMapper, never()).deleteById(any());
    }

    @Test
    void removeMember_selfAllowed_evenIfNotAdmin() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(memberUser(adminId));
        WorkspaceUser self = memberUser(adminId);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(self);

        memberService.removeMember(adminId, workspaceId, adminId);

        verify(workspaceUserMapper).deleteById(self.getId());
    }

    @Test
    void removeMember_lastAdmin_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(adminUser());
        WorkspaceUser target = memberUser(targetUserId);
        target.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(target);
        when(workspaceUserMapper.countByWorkspaceIdAndRole(workspaceId, Constants.WorkspaceRole.ADMIN_ID)).thenReturn(1L);

        assertThrows(ServiceException.class,
                () -> memberService.removeMember(adminId, workspaceId, targetUserId));
        verify(workspaceUserMapper, never()).deleteById(any());
    }

    @Test
    void removeMember_adminRemovesMember_success() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, adminId)).thenReturn(adminUser());
        WorkspaceUser target = memberUser(targetUserId);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(target);

        memberService.removeMember(adminId, workspaceId, targetUserId);

        verify(workspaceUserMapper).deleteById(target.getId());
    }

    private SysUser activeUser(UUID id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("u@x.com");
        user.setStatus(Constants.Status.ACTIVE);
        return user;
    }
}