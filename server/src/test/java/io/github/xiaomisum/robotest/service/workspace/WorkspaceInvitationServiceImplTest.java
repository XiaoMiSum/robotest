package io.github.xiaomisum.robotest.service.workspace;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.convert.WorkspaceInvitationConvertMapper;
import io.github.xiaomisum.robotest.model.convert.WorkspaceInvitationConvertMapperImpl;
import io.github.xiaomisum.robotest.model.dto.request.workspace.InvitationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.InvitationJoinReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationCheckEmailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationCopyLinkRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationJoinRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationVerifyRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceInvitation;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceInvitationMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.workspace.member.InvitationDecision;
import io.github.xiaomisum.robotest.service.workspace.member.InvitationRejectReason;
import io.github.xiaomisum.robotest.service.workspace.member.InvitationStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.security.core.authentication.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceInvitationServiceImplTest {

    @Mock
    private WorkspaceInvitationMapper invitationMapper;
    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private InvitationStateMachine invitationStateMachine;
    @Spy
    private WorkspaceInvitationConvertMapper workspaceInvitationConvertMapper =
            new WorkspaceInvitationConvertMapperImpl();

    @InjectMocks
    private WorkspaceInvitationServiceImpl invitationService;

    private final UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private final UUID invitationId = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private final String token = "tok-123";

    private WorkspaceInvitation invitation(String status) {
        WorkspaceInvitation inv = new WorkspaceInvitation();
        inv.setId(invitationId);
        inv.setWorkspaceId(workspaceId);
        inv.setToken(token);
        inv.setStatus(status);
        inv.setMaxUses(5);
        inv.setUseCount(0);
        inv.setExpiresAt(LocalDateTime.of(2026, 12, 31, 23, 59));
        return inv;
    }

    private WorkspaceInvitation invitationWithToken(String status) {
        WorkspaceInvitation invitation = invitation(status);
        invitation.setToken("0123456789abcdef");
        return invitation;
    }

    private WorkspaceUser admin() {
        WorkspaceUser scope = new WorkspaceUser();
        scope.setUserId(userId);
        scope.setWorkspaceId(workspaceId);
        scope.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        return scope;
    }

    private SysUser existingUser(UUID id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("alice");
        user.setEmail("alice@x.com");
        user.setName("Alice");
        user.setPasswordHash("hash");
        user.setStatus(Constants.Status.ACTIVE);
        return user;
    }

    private Workspace qaWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setName("QA 团队");
        return workspace;
    }

    private void stubJoinableInvitation() {
        when(invitationMapper.selectOne(any(SFunction.class), eq(token))).thenReturn(invitation(Constants.Status.ACTIVE));
        when(invitationStateMachine.decision(any(WorkspaceInvitation.class), any(LocalDateTime.class)))
                .thenReturn(InvitationDecision.allowed());
    }

    // ========== getInvitationPage ==========

    @Test
    void getInvitationPage_returnsPreviewAndEffectiveStatus() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(admin());
        List<WorkspaceInvitation> invitations = List.of(
                invitationWithToken(Constants.Status.ACTIVE),
                invitationWithToken(Constants.Status.ACTIVE),
                invitationWithToken(Constants.Status.ACTIVE),
                invitationWithToken(Constants.Status.REVOKED));
        when(invitationMapper.findPageByWorkspaceId(any(PageParam.class), eq(workspaceId)))
                .thenReturn(new PageResult<>(invitations, 4L));
        when(invitationStateMachine.decision(any(WorkspaceInvitation.class), any(LocalDateTime.class)))
                .thenReturn(
                        InvitationDecision.allowed(),
                        InvitationDecision.rejected(InvitationRejectReason.USE_EXHAUSTED),
                        InvitationDecision.rejected(InvitationRejectReason.EXPIRED),
                        InvitationDecision.rejected(InvitationRejectReason.REVOKED));

        PageResult<InvitationListRespDTO> result = invitationService.getInvitationPage(
                userId, workspaceId, 1, 20);

        assertEquals(4L, result.getTotal());
        assertEquals("0123…cdef", result.getList().get(0).getTokenPreview());
        assertEquals("active", result.getList().get(0).getEffectiveStatus());
        assertEquals("exhausted", result.getList().get(1).getEffectiveStatus());
        assertEquals("expired", result.getList().get(2).getEffectiveStatus());
        assertEquals("revoked", result.getList().get(3).getEffectiveStatus());
    }

    @Test
    void getInvitationPage_notAdmin_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> invitationService.getInvitationPage(userId, workspaceId, 1, 20));
        verify(invitationMapper, never()).findPageByWorkspaceId(any(PageParam.class), any());
    }

    // ========== getInvitationCopyLink ==========

    @Test
    void getInvitationCopyLink_active_returnsToken() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(admin());
        when(invitationMapper.selectById(invitationId)).thenReturn(invitation(Constants.Status.ACTIVE));
        when(invitationStateMachine.decision(any(WorkspaceInvitation.class), any(LocalDateTime.class)))
                .thenReturn(InvitationDecision.allowed());

        InvitationCopyLinkRespDTO result = invitationService.getInvitationCopyLink(userId, workspaceId, invitationId);

        assertEquals(token, result.getToken());
    }

    @Test
    void getInvitationCopyLink_exhausted_returnsToken() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(admin());
        when(invitationMapper.selectById(invitationId)).thenReturn(invitation(Constants.Status.ACTIVE));
        when(invitationStateMachine.decision(any(WorkspaceInvitation.class), any(LocalDateTime.class)))
                .thenReturn(InvitationDecision.rejected(InvitationRejectReason.USE_EXHAUSTED));

        InvitationCopyLinkRespDTO result = invitationService.getInvitationCopyLink(userId, workspaceId, invitationId);

        assertEquals(token, result.getToken());
    }

    @Test
    void getInvitationCopyLink_expired_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(admin());
        when(invitationMapper.selectById(invitationId)).thenReturn(invitation(Constants.Status.ACTIVE));
        when(invitationStateMachine.decision(any(WorkspaceInvitation.class), any(LocalDateTime.class)))
                .thenReturn(InvitationDecision.rejected(InvitationRejectReason.EXPIRED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> invitationService.getInvitationCopyLink(userId, workspaceId, invitationId));
        assertEquals(1000010031, error.getCode());
    }

    @Test
    void getInvitationCopyLink_revoked_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(admin());
        when(invitationMapper.selectById(invitationId)).thenReturn(invitation(Constants.Status.REVOKED));
        when(invitationStateMachine.decision(any(WorkspaceInvitation.class), any(LocalDateTime.class)))
                .thenReturn(InvitationDecision.rejected(InvitationRejectReason.REVOKED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> invitationService.getInvitationCopyLink(userId, workspaceId, invitationId));
        assertEquals(1000010032, error.getCode());
    }

    // ========== verifyInvitation ==========

    @Test
    void verifyInvitation_joinable_returnsValid() {
        stubJoinableInvitation();
        when(workspaceMapper.selectById(workspaceId)).thenReturn(qaWorkspace());

        InvitationVerifyRespDTO result = invitationService.verifyInvitation(token);

        assertTrue(result.getValid());
        assertEquals("QA 团队", result.getWorkspaceName());
        assertEquals("2026-12-31T23:59:00Z", result.getExpiresAt());
    }

    @Test
    void verifyInvitation_rejected_returnsInvalidWithoutQueryingWorkspace() {
        when(invitationMapper.selectOne(any(SFunction.class), eq(token))).thenReturn(invitation(Constants.Status.ACTIVE));
        when(invitationStateMachine.decision(any(WorkspaceInvitation.class), any(LocalDateTime.class)))
                .thenReturn(InvitationDecision.rejected(InvitationRejectReason.REVOKED));

        InvitationVerifyRespDTO result = invitationService.verifyInvitation(token);

        assertFalse(result.getValid());
        assertNull(result.getWorkspaceName());
        verify(workspaceMapper, never()).selectById(any());
    }

    @Test
    void verifyInvitation_workspaceMissing_returnsInvalid() {
        stubJoinableInvitation();
        when(workspaceMapper.selectById(workspaceId)).thenReturn(null);

        InvitationVerifyRespDTO result = invitationService.verifyInvitation(token);

        assertFalse(result.getValid());
    }

    // ========== checkEmail ==========

    @Test
    void checkEmail_rejectReasonMapsToInvitationError() {
        assertRejectToError(InvitationRejectReason.INVALID, 1000010029);
        assertRejectToError(InvitationRejectReason.REVOKED, 1000010032);
        assertRejectToError(InvitationRejectReason.EXPIRED, 1000010031);
        assertRejectToError(InvitationRejectReason.USE_EXHAUSTED, 1000010030);
    }

    @Test
    void checkEmail_joinable_returnsExistsFlag() {
        stubJoinableInvitation();
        when(userMapper.findByEmail("alice@x.com")).thenReturn(null);

        InvitationCheckEmailRespDTO result = invitationService.checkEmail(token, "alice@x.com");

        assertFalse(result.getExists());
    }

    // ========== joinByInvitation ==========

    @Test
    void joinByInvitation_existingUser_addsMemberAndIssuesTokens() {
        stubJoinableInvitation();
        SysUser existing = existingUser(UUID.fromString("00000000-0000-0000-0000-0000000000dd"));
        when(userMapper.findByEmail("alice@x.com")).thenReturn(existing);
        when(passwordEncoder.matches("Pass123!", "hash")).thenReturn(true);
        when(userMapper.selectById(existing.getId())).thenReturn(existing);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, existing.getId())).thenReturn(null);
        when(invitationMapper.incrementUseCount(invitationId)).thenReturn(1);
        when(workspaceMapper.selectById(workspaceId)).thenReturn(qaWorkspace());
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("refresh-token");

        InvitationJoinReqDTO req = joinReq();
        InvitationJoinRespDTO result = invitationService.joinByInvitation(req);

        assertFalse(result.getIsNewUser());
        assertEquals("access-token", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
        assertEquals(Constants.Auth.TOKEN_TYPE_BEARER, result.getTokenType());
        assertEquals("alice@x.com", result.getUser().getEmail());
        assertEquals(Constants.WorkspaceRole.MEMBER_ID, result.getActiveWorkspace().getWorkspaceRole());
        assertEquals("QA 团队", result.getActiveWorkspace().getName());
        verify(userMapper, never()).insert(any(SysUser.class));
        verify(workspaceUserMapper).insert(any(WorkspaceUser.class));
        verify(invitationMapper).incrementUseCount(invitationId);
    }

    @Test
    void joinByInvitation_newUser_createsUserAndIssuesTokens() {
        stubJoinableInvitation();
        when(userMapper.findByEmail("alice@x.com")).thenReturn(null);
        when(passwordEncoder.encode("Pass123!")).thenReturn("encoded");
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(eq(workspaceId), isNull())).thenReturn(null);
        when(invitationMapper.incrementUseCount(invitationId)).thenReturn(1);
        when(workspaceMapper.selectById(workspaceId)).thenReturn(qaWorkspace());
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("refresh-token");

        InvitationJoinReqDTO req = joinReq();
        req.setName("Alice 阿丽");
        InvitationJoinRespDTO result = invitationService.joinByInvitation(req);

        assertTrue(result.getIsNewUser());
        assertEquals("alice@x.com", result.getUser().getEmail());
        verify(userMapper).insert(any(SysUser.class));
        verify(invitationMapper).incrementUseCount(invitationId);
    }

    @Test
    void joinByInvitation_exhaustedAtIncrement_rollsBack() {
        stubJoinableInvitation();
        SysUser existing = existingUser(userId);
        when(userMapper.findByEmail("alice@x.com")).thenReturn(existing);
        when(passwordEncoder.matches("Pass123!", "hash")).thenReturn(true);
        when(userMapper.selectById(userId)).thenReturn(existing);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(null);
        when(invitationMapper.incrementUseCount(invitationId)).thenReturn(0);

        ServiceException e = assertThrows(ServiceException.class,
                () -> invitationService.joinByInvitation(joinReq()));
        assertEquals(1000010030, e.getCode());
    }

    // ========== revokeInvitation ==========

    @Test
    void revokeInvitation_success_partialUpdateCarrier() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(admin());
        when(invitationMapper.selectById(invitationId)).thenReturn(invitation(Constants.Status.ACTIVE));

        invitationService.revokeInvitation(userId, workspaceId, invitationId);

        ArgumentCaptor<WorkspaceInvitation> captor = ArgumentCaptor.forClass(WorkspaceInvitation.class);
        verify(invitationMapper).updateById(captor.capture());
        // C9 部分更新载体：仅携带 id + 新状态，不回写查询实体
        assertEquals(Constants.Status.REVOKED, captor.getValue().getStatus());
        assertEquals(invitationId, captor.getValue().getId());
        assertNull(captor.getValue().getWorkspaceId());
    }

    @Test
    void revokeInvitation_again_isIdempotent() {
        // 行为保持：已撤销邀请重复 revoke 幂等重写 REVOKED，不报错（撤销操作不做状态机闸门）
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(admin());
        when(invitationMapper.selectById(invitationId)).thenReturn(invitation(Constants.Status.REVOKED));

        assertDoesNotThrow(() -> invitationService.revokeInvitation(userId, workspaceId, invitationId));
        verify(invitationMapper).updateById(any(WorkspaceInvitation.class));
    }

    @Test
    void revokeInvitation_notAdmin_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(null);

        assertThrows(ServiceException.class, () -> invitationService.revokeInvitation(userId, workspaceId, invitationId));
        verify(invitationMapper, never()).updateById(any(WorkspaceInvitation.class));
    }

    @Test
    void revokeInvitation_notFound_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(admin());
        when(invitationMapper.selectById(invitationId)).thenReturn(null);

        assertThrows(ServiceException.class, () -> invitationService.revokeInvitation(userId, workspaceId, invitationId));
        verify(invitationMapper, never()).updateById(any(WorkspaceInvitation.class));
    }

    @Test
    void revokeInvitation_otherWorkspace_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(admin());
        WorkspaceInvitation other = invitation(Constants.Status.ACTIVE);
        other.setWorkspaceId(UUID.fromString("00000000-0000-0000-0000-0000000000ee"));
        when(invitationMapper.selectById(invitationId)).thenReturn(other);

        assertThrows(ServiceException.class, () -> invitationService.revokeInvitation(userId, workspaceId, invitationId));
        verify(invitationMapper, never()).updateById(any(WorkspaceInvitation.class));
    }

    // ========== createInvitation ==========

    @Test
    void createInvitation_success_partialDefaults() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(admin());

        InvitationCreateReqDTO req = new InvitationCreateReqDTO();
        req.setExpiresAt(LocalDateTime.of(2026, 12, 31, 23, 59));
        req.setMaxUses(10);
        InvitationRespDTO result = invitationService.createInvitation(userId, workspaceId, req);

        ArgumentCaptor<WorkspaceInvitation> captor = ArgumentCaptor.forClass(WorkspaceInvitation.class);
        verify(invitationMapper).insert(captor.capture());
        WorkspaceInvitation saved = captor.getValue();
        assertEquals(Constants.Status.ACTIVE, saved.getStatus());
        assertEquals(Integer.valueOf(0), saved.getUseCount());
        assertEquals(workspaceId, saved.getWorkspaceId());
        assertEquals(req.getExpiresAt(), saved.getExpiresAt());
        assertEquals(req.getMaxUses(), saved.getMaxUses());
        assertEquals(userId.toString(), saved.getCreatedBy());
        assertNotNull(saved.getToken());
        assertNotNull(result.getToken());
    }

    @Test
    void createInvitation_notAdmin_throws() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> invitationService.createInvitation(userId, workspaceId, new InvitationCreateReqDTO()));
        verify(invitationMapper, never()).insert(any(WorkspaceInvitation.class));
    }

    // ========== helpers ==========

    private InvitationJoinReqDTO joinReq() {
        InvitationJoinReqDTO req = new InvitationJoinReqDTO();
        req.setToken(token);
        req.setEmail("alice@x.com");
        req.setPassword("Pass123!");
        return req;
    }

    private void assertRejectToError(InvitationRejectReason reason, int expectedCode) {
        when(invitationMapper.selectOne(any(SFunction.class), eq(token))).thenReturn(invitation(Constants.Status.ACTIVE));
        when(invitationStateMachine.decision(any(WorkspaceInvitation.class), any(LocalDateTime.class)))
                .thenReturn(InvitationDecision.rejected(reason));

        ServiceException e = assertThrows(ServiceException.class, () -> invitationService.checkEmail(token, "alice@x.com"));
        assertEquals(expectedCode, e.getCode());
    }
}