package io.github.xiaomisum.robotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.InvitationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.InvitationJoinReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationJoinRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationVerifyRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceInvitation;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceInvitationMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.WorkspaceInvitationService;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;
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
    public InvitationRespDTO createInvitation(String userId, String workspaceId, InvitationCreateReqDTO reqDTO) {
        checkAdminPermission(userId, workspaceId);

        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.setId(UUID.randomUUID().toString());
        invitation.setWorkspaceId(workspaceId);
        invitation.setToken(generateToken());
        invitation.setCreatedBy(userId);
        invitation.setExpiresAt(reqDTO.getExpiresAt());
        invitation.setMaxUses(reqDTO.getMaxUses());
        invitation.setUseCount(0);
        invitation.setStatus("active");
        invitationMapper.insert(invitation);

        return convertToRespDTO(invitation);
    }

    @Override
    public PageResult<InvitationRespDTO> getInvitationPage(String workspaceId, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<WorkspaceInvitation> wrapper = new LambdaQueryWrapper<WorkspaceInvitation>()
                .eq(WorkspaceInvitation::getWorkspaceId, workspaceId)
                .orderByDesc(WorkspaceInvitation::getCreatedAt);

        PageResult<WorkspaceInvitation> page = invitationMapper.selectPage(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, wrapper);

        List<InvitationRespDTO> records = page.getList().stream()
                .map(this::convertToRespDTO)
                .collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeInvitation(String userId, String workspaceId, String invitationId) {
        checkAdminPermission(userId, workspaceId);

        WorkspaceInvitation invitation = invitationMapper.selectById(invitationId);
        if (invitation == null || !invitation.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_INVALID);
        }

        invitation.setStatus("revoked");
        invitationMapper.updateById(invitation);
    }

    @Override
    public InvitationVerifyRespDTO verifyInvitation(String token) {
        InvitationVerifyRespDTO result = new InvitationVerifyRespDTO();

        WorkspaceInvitation invitation = invitationMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceInvitation>()
                        .eq(WorkspaceInvitation::getToken, token));

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
    @Transactional(rollbackFor = Exception.class)
    public InvitationJoinRespDTO joinByInvitation(InvitationJoinReqDTO reqDTO) {
        WorkspaceInvitation invitation = validateAndGetInvitation(reqDTO.getToken());

        SysUser user = findOrCreateUser(reqDTO.getEmail(), reqDTO.getPassword());
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
                .tokenType("Bearer")
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

    private void checkAdminPermission(String userId, String workspaceId) {
        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null || !ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    private boolean isValidInvitation(WorkspaceInvitation invitation) {
        if (invitation == null || !"active".equals(invitation.getStatus())) {
            return false;
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return invitation.getMaxUses() == null || invitation.getUseCount() < invitation.getMaxUses();
    }

    private WorkspaceInvitation validateAndGetInvitation(String token) {
        WorkspaceInvitation invitation = invitationMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceInvitation>()
                        .eq(WorkspaceInvitation::getToken, token));

        if (invitation == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INVITATION_INVALID);
        }
        if (!"active".equals(invitation.getStatus())) {
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

    private SysUser findOrCreateUser(String email, String password) {
        SysUser existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));

        if (existingUser != null) {
            if (!passwordEncoder.matches(password, existingUser.getPasswordHash())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PASSWORD_WRONG);
            }
            return existingUser;
        }

        SysUser newUser = new SysUser();
        newUser.setId(UUID.randomUUID().toString());
        newUser.setUsername(generateUsername(email));
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(password));
        newUser.setStatus("active");
        return newUser;
    }

    private WorkspaceUser addMemberToWorkspace(String userId, String workspaceId) {
        WorkspaceUser existing = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));

        if (existing != null) {
            return existing;
        }

        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setId(UUID.randomUUID().toString());
        workspaceUser.setUserId(userId);
        workspaceUser.setWorkspaceId(workspaceId);
        workspaceUser.setWorkspaceRole(ErrorCodeConstants.WORKSPACE_ROLE_MEMBER_ID);
        workspaceUser.setJoinedAt(LocalDateTime.now());
        workspaceUserMapper.insert(workspaceUser);
        return workspaceUser;
    }

    private void incrementInvitationUseCount(WorkspaceInvitation invitation) {
        invitation.setUseCount(invitation.getUseCount() + 1);
        invitationMapper.updateById(invitation);
    }

    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setName(user.getUsername());
        loginUser.setEmail(user.getEmail());
        loginUser.setPassword(user.getPasswordHash());
        loginUser.setEnabled("active".equals(user.getStatus()));
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
        while (userMapper.selectOne(SysUser::getUsername, username) != null) {
            username = base + counter;
            counter++;
        }
        return username;
    }

    private InvitationRespDTO convertToRespDTO(WorkspaceInvitation invitation) {
        InvitationRespDTO dto = new InvitationRespDTO();
        dto.setId(invitation.getId());
        dto.setToken(invitation.getToken());
        dto.setExpiresAt(invitation.getExpiresAt());
        dto.setMaxUses(invitation.getMaxUses());
        dto.setUseCount(invitation.getUseCount());
        dto.setStatus(invitation.getStatus());
        dto.setCreatedAt(invitation.getCreatedAt());
        return dto;
    }
}
