package io.github.xiaomisum.robotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberAddResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.WorkspaceMemberService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkspaceMemberServiceImpl implements WorkspaceMemberService {

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;

    @Override
    public PageResult<WorkspaceMemberRespDTO> getMemberPage(String workspaceId, String keyword,
                                                             Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<WorkspaceUser> wrapper = new LambdaQueryWrapper<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                .orderByDesc(WorkspaceUser::getJoinedAt);

        PageResult<WorkspaceUser> page = workspaceUserMapper.selectPage(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, wrapper);

        if (page.getList().isEmpty()) {
            return new PageResult<>(List.of(), 0L);
        }

        List<String> userIds = page.getList().stream()
                .map(WorkspaceUser::getUserId)
                .collect(Collectors.toList());
        List<SysUser> users = userMapper.selectList(SysUser::getId, userIds);

        List<WorkspaceMemberRespDTO> records = page.getList().stream().map(wu -> {
            WorkspaceMemberRespDTO dto = new WorkspaceMemberRespDTO();
            dto.setUserId(wu.getUserId());
            dto.setWorkspaceRole(wu.getWorkspaceRole());
            dto.setJoinedAt(wu.getJoinedAt());

            users.stream()
                    .filter(u -> u.getId().equals(wu.getUserId()))
                    .findFirst()
                    .ifPresent(u -> {
                        dto.setUsername(u.getUsername());
                        dto.setEmail(u.getEmail());
                        dto.setAvatarUrl(u.getAvatarUrl());
                    });

            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceMemberAddResultRespDTO addMembers(String userId, String workspaceId,
                                                       WorkspaceMembersAddReqDTO reqDTO) {
        WorkspaceUser adminUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (adminUser == null || !ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(adminUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        List<String> skippedUserIds = new ArrayList<>();
        int successCount = 0;

        for (WorkspaceMembersAddReqDTO.MemberItem member : reqDTO.getMembers()) {
            SysUser user = userMapper.selectById(member.getUserId());
            if (user == null || !"active".equals(user.getStatus())) {
                continue;
            }

            WorkspaceUser existing = workspaceUserMapper.selectOne(
                    new LambdaQueryWrapper<WorkspaceUser>()
                            .eq(WorkspaceUser::getUserId, member.getUserId())
                            .eq(WorkspaceUser::getWorkspaceId, workspaceId));
            if (existing != null) {
                skippedUserIds.add(member.getUserId());
                continue;
            }

            WorkspaceUser workspaceUser = new WorkspaceUser();
            workspaceUser.setId(UUID.randomUUID());
            workspaceUser.setUserId(member.getUserId());
            workspaceUser.setWorkspaceId(workspaceId);
            workspaceUser.setWorkspaceRole(StringUtils.hasText(member.getWorkspaceRole())
                    ? member.getWorkspaceRole() : ErrorCodeConstants.WORKSPACE_ROLE_MEMBER_ID);
            workspaceUser.setJoinedAt(LocalDateTime.now());
            workspaceUserMapper.insert(workspaceUser);
            successCount++;
        }

        WorkspaceMemberAddResultRespDTO result = new WorkspaceMemberAddResultRespDTO();
        result.setSuccessCount(successCount);
        result.setSkippedUserIds(skippedUserIds);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRole(String userId, String workspaceId, String targetUserId, String workspaceRole) {
        WorkspaceUser adminUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (adminUser == null || !ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(adminUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        WorkspaceUser targetMember = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, targetUserId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (targetMember == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }

        if (ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(targetMember.getWorkspaceRole())
                && !ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(workspaceRole)) {
            Long adminCount = workspaceUserMapper.selectCount(
                    new LambdaQueryWrapper<WorkspaceUser>()
                            .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                            .eq(WorkspaceUser::getWorkspaceRole, ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID));
            if (adminCount <= 1) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.MUST_KEEP_ONE_WORKSPACE_ADMIN);
            }
        }

        targetMember.setWorkspaceRole(workspaceRole);
        workspaceUserMapper.updateById(targetMember);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(String userId, String workspaceId, String targetUserId) {
        WorkspaceUser currentUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (currentUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        boolean isSelf = userId.equals(targetUserId);
        if (!isSelf && !ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(currentUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        WorkspaceUser targetMember = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, targetUserId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (targetMember == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }

        if (ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(targetMember.getWorkspaceRole())) {
            Long adminCount = workspaceUserMapper.selectCount(
                    new LambdaQueryWrapper<WorkspaceUser>()
                            .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                            .eq(WorkspaceUser::getWorkspaceRole, ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID));
            if (adminCount <= 1) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.MUST_KEEP_ONE_WORKSPACE_ADMIN);
            }
        }

        workspaceUserMapper.deleteById(targetMember.getId());
    }
}
