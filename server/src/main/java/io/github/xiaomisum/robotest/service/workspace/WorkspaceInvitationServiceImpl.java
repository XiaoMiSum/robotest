package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.WorkspaceInvitationConvertMapper;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.workspace.InvitationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.InvitationJoinReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationCheckEmailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationJoinRespDTO;
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
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.security.core.authentication.JwtTokenProvider;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkspaceInvitationServiceImpl implements WorkspaceInvitationService {

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
    public PageResult<InvitationRespDTO> getInvitationPage(UUID workspaceId, Integer pageNo, Integer pageSize) {
        PageResult<WorkspaceInvitation> page = invitationMapper.findPageByWorkspaceId(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, workspaceId);

        List<InvitationRespDTO> records = page.getList().stream()
                .map(this::convertToRespDTO)
                .collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeInvitation(UUID userId, UUID workspaceId, UUID invitationId) {
        checkAdminPermission(userId, workspaceId);

        WorkspaceInvitation invitation = invitationMapper.selectById(invitationId);
        if (invitation == null || !invitation.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_INVALID);
        }

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
        result.setExpiresAt(invitation.getExpiresAt() != null
                ? invitation.getExpiresAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
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

    private void checkAdminPermission(UUID userId, UUID workspaceId) {
        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null || !Constants.WorkspaceRole.ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    private boolean isValidInvitation(WorkspaceInvitation invitation) {
        if (invitation == null || !Constants.Status.ACTIVE.equals(invitation.getStatus())) {
            return false;
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return invitation.getMaxUses() == null || invitation.getUseCount() < invitation.getMaxUses();
    }

    private WorkspaceInvitation validateAndGetInvitation(String token) {
        WorkspaceInvitation invitation = invitationMapper.selectOne(WorkspaceInvitation::getToken, token);

        if (invitation == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_INVALID);
        }
        if (!Constants.Status.ACTIVE.equals(invitation.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_REVOKED);
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_EXPIRED);
        }
        if (invitation.getMaxUses() != null && invitation.getUseCount() >= invitation.getMaxUses()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_MAX_USES);
        }
        return invitation;
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
        WorkspaceInvitation update = new WorkspaceInvitation();
        update.setId(invitation.getId());
        update.setUseCount(invitation.getUseCount() + 1);
        invitationMapper.updateById(update);
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
        return WorkspaceInvitationConvertMapper.INSTANCE.toRespDTO(invitation);
    }
}
