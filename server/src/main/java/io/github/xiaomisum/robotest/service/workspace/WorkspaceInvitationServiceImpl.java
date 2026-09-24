package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.convert.WorkspaceInvitationConvertMapper;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.time.UtcTime;
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
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ErrorCode;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.security.core.authentication.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkspaceInvitationServiceImpl implements WorkspaceInvitationService {

    private static final String EFFECTIVE_STATUS_ACTIVE = "active";
    private static final String EFFECTIVE_STATUS_EXHAUSTED = "exhausted";
    private static final String EFFECTIVE_STATUS_EXPIRED = "expired";
    private static final String EFFECTIVE_STATUS_REVOKED = "revoked";

    @Resource
    private WorkspaceInvitationMapper invitationMapper;
    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private JwtTokenProvider jwtTokenProvider;
    @Resource
    private InvitationStateMachine invitationStateMachine;
    @Resource
    private WorkspaceInvitationConvertMapper workspaceInvitationConvertMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvitationRespDTO createInvitation(UUID userId, UUID workspaceId, InvitationCreateReqDTO reqDTO) {
        checkAdminPermission(userId, workspaceId);

        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.setWorkspaceId(workspaceId);
        invitation.setToken(generateToken());
        invitation.setCreatedBy(userId.toString());
        invitation.setExpiresAt(reqDTO.getExpiresAt());
        invitation.setMaxUses(reqDTO.getMaxUses());
        invitation.setUseCount(0);
        invitation.setStatus(Constants.Status.ACTIVE);
        invitationMapper.insert(invitation);

        return convertToRespDTO(invitation);
    }

    @Override
    public PageResult<InvitationListRespDTO> getInvitationPage(UUID userId, UUID workspaceId, Integer pageNo, Integer pageSize) {
        // 邀请链接是敏感凭据，列表仅管理员可见（与 create/revoke 一致）
        checkAdminPermission(userId, workspaceId);

        PageResult<WorkspaceInvitation> page = invitationMapper.findPageByWorkspaceId(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, workspaceId);

        LocalDateTime now = UtcTime.utcNow();
        List<InvitationListRespDTO> records = page.getList().stream().map(invitation -> {
            InvitationListRespDTO dto = workspaceInvitationConvertMapper.toListRespDTO(invitation);
            dto.setTokenPreview(maskToken(invitation.getToken()));
            dto.setEffectiveStatus(resolveEffectiveStatus(invitationStateMachine.decision(invitation, now)));
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    @Override
    public InvitationCopyLinkRespDTO getInvitationCopyLink(UUID userId, UUID workspaceId, UUID invitationId) {
        checkAdminPermission(userId, workspaceId);
        WorkspaceInvitation invitation = getOwnedInvitation(workspaceId, invitationId);

        InvitationRejectReason reason = invitationStateMachine.decision(invitation, UtcTime.utcNow()).reason();
        if (reason == InvitationRejectReason.REVOKED) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_REVOKED);
        }
        if (reason == InvitationRejectReason.EXPIRED) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_EXPIRED);
        }
        if (reason == InvitationRejectReason.INVALID) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_INVALID);
        }
        return new InvitationCopyLinkRespDTO(invitation.getToken());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeInvitation(UUID userId, UUID workspaceId, UUID invitationId) {
        checkAdminPermission(userId, workspaceId);

        WorkspaceInvitation invitation = getOwnedInvitation(workspaceId, invitationId);

        WorkspaceInvitation update = new WorkspaceInvitation();
        update.setId(invitation.getId());
        update.setStatus(Constants.Status.REVOKED);
        invitationMapper.updateById(update);
    }

    @Override
    public InvitationVerifyRespDTO verifyInvitation(String token) {
        InvitationVerifyRespDTO result = new InvitationVerifyRespDTO();

        WorkspaceInvitation invitation = invitationMapper.selectOne(WorkspaceInvitation::getToken, token);

        if (!isValidInvitation(invitation)) {
            result.setValid(false);
            return result;
        }

        Workspace workspace = workspaceMapper.selectById(invitation.getWorkspaceId());
        if (workspace == null) {
            result.setValid(false);
            return result;
        }

        result.setValid(true);
        result.setWorkspaceName(workspace.getName());
        result.setExpiresAt(UtcTime.toIso(invitation.getExpiresAt()));
        return result;
    }

    @Override
    public InvitationCheckEmailRespDTO checkEmail(String token, String email) {
        validateAndGetInvitation(token);
        SysUser existingUser = userMapper.findByEmail(email);
        return InvitationCheckEmailRespDTO.builder()
                .exists(existingUser != null)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvitationJoinRespDTO joinByInvitation(InvitationJoinReqDTO reqDTO) {
        WorkspaceInvitation invitation = validateAndGetInvitation(reqDTO.getToken());

        SysUser user = findOrCreateUser(reqDTO.getEmail(), reqDTO.getPassword(), reqDTO.getName());
        boolean isNewUser = userMapper.selectById(user.getId()) == null;
        if (isNewUser) {
            userMapper.insert(user);
        }

        WorkspaceUser workspaceUser = addMemberToWorkspace(user.getId(), invitation.getWorkspaceId());
        incrementInvitationUseCount(invitation);

        Workspace workspace = workspaceMapper.selectById(invitation.getWorkspaceId());
        LoginUser loginUser = buildLoginUser(user);

        return InvitationJoinRespDTO.builder()
                .accessToken(jwtTokenProvider.createAccessToken(loginUser))
                .refreshToken(jwtTokenProvider.createRefreshToken(loginUser))
                .tokenType(Constants.Auth.TOKEN_TYPE_BEARER)
                .user(InvitationJoinRespDTO.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .build())
                .activeWorkspace(InvitationJoinRespDTO.ActiveWorkspaceInfo.builder()
                        .id(workspace.getId())
                        .name(workspace.getName())
                        .workspaceRole(workspaceUser.getWorkspaceRole())
                        .build())
                .isNewUser(isNewUser)
                .build();
    }

    private WorkspaceInvitation getOwnedInvitation(UUID workspaceId, UUID invitationId) {
        WorkspaceInvitation invitation = invitationMapper.selectById(invitationId);
        if (invitation == null || !invitation.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_INVALID);
        }
        return invitation;
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return token;
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }

    private String resolveEffectiveStatus(InvitationDecision decision) {
        if (decision.joinable()) {
            return EFFECTIVE_STATUS_ACTIVE;
        }
        return switch (decision.reason()) {
            case USE_EXHAUSTED -> EFFECTIVE_STATUS_EXHAUSTED;
            case EXPIRED -> EFFECTIVE_STATUS_EXPIRED;
            case REVOKED, INVALID -> EFFECTIVE_STATUS_REVOKED;
        };
    }

    private void checkAdminPermission(UUID userId, UUID workspaceId) {
        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null || !Constants.WorkspaceRole.ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    private boolean isValidInvitation(WorkspaceInvitation invitation) {
        return invitationStateMachine.decision(invitation, UtcTime.utcNow()).joinable();
    }

    private WorkspaceInvitation validateAndGetInvitation(String token) {
        WorkspaceInvitation invitation = invitationMapper.selectOne(WorkspaceInvitation::getToken, token);

        InvitationDecision decision = invitationStateMachine.decision(invitation, UtcTime.utcNow());
        if (!decision.joinable()) {
            throw ServiceExceptionUtil.get(rejectToError(decision.reason()));
        }
        return invitation;
    }

    private ErrorCode rejectToError(InvitationRejectReason reason) {
        // 拒绝优先级已收敛进状态机判定（REVOKED > EXPIRED > 达上限），这里只做原因→错误码的机械映射
        return switch (reason) {
            case INVALID -> ErrorCodeConstants.INVITATION_INVALID;
            case REVOKED -> ErrorCodeConstants.INVITATION_REVOKED;
            case EXPIRED -> ErrorCodeConstants.INVITATION_EXPIRED;
            case USE_EXHAUSTED -> ErrorCodeConstants.INVITATION_MAX_USES;
        };
    }

    private SysUser findOrCreateUser(String email, String password, String name) {
        SysUser existingUser = userMapper.findByEmail(email);

        if (existingUser != null) {
            if (!passwordEncoder.matches(password, existingUser.getPasswordHash())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PASSWORD_WRONG);
            }
            return existingUser;
        }

        String displayName = (name != null && !name.isBlank()) ? name : generateUsername(email);
        SysUser newUser = new SysUser();
        newUser.setName(displayName);
        newUser.setUsername(generateUsername(email));
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(password));
        newUser.setStatus(Constants.Status.ACTIVE);
        return newUser;
    }

    private WorkspaceUser addMemberToWorkspace(UUID userId, UUID workspaceId) {
        WorkspaceUser existing = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);

        if (existing != null) {
            return existing;
        }

        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setUserId(userId);
        workspaceUser.setWorkspaceId(workspaceId);
        workspaceUser.setWorkspaceRole(Constants.WorkspaceRole.MEMBER_ID);
        workspaceUser.setJoinedAt(LocalDateTime.now());
        workspaceUserMapper.insert(workspaceUser);
        return workspaceUser;
    }

    private void incrementInvitationUseCount(WorkspaceInvitation invitation) {
        // 原子自增 + 上限条件，0 行表示已被并发 join 耗尽，避免 maxUses 被突破
        if (invitationMapper.incrementUseCount(invitation.getId()) == 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_MAX_USES);
        }
    }

    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setName(user.getName());
        loginUser.setEmail(user.getEmail());
        loginUser.setPassword(user.getPasswordHash());
        loginUser.setEnabled(Constants.Status.ACTIVE.equals(user.getStatus()));
        return loginUser;
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateUsername(String email) {
        String base = email.split("@")[0];
        String username = base;
        int counter = 1;
        while (userMapper.findByUsername(username) != null) {
            username = base + counter;
            counter++;
        }
        return username;
    }

    private InvitationRespDTO convertToRespDTO(WorkspaceInvitation invitation) {
        InvitationRespDTO dto = workspaceInvitationConvertMapper.toRespDTO(invitation);
        dto.setExpiresAt(UtcTime.toUtcWallClock(dto.getExpiresAt()));
        dto.setCreatedAt(UtcTime.toUtcWallClock(dto.getCreatedAt()));
        return dto;
    }
}
