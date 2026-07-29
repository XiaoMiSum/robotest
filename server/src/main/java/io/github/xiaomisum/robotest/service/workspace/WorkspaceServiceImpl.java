package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private SysUserMapper userMapper;

    @Override
    public PageResult<WorkspaceRespDTO> getWorkspacePage(String keyword, String status, Integer pageNo, Integer pageSize) {
        PageResult<Workspace> page = workspaceMapper.findPage(
                new xyz.migoo.framework.common.pojo.PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, keyword, status);

        List<WorkspaceRespDTO> records = page.getList().stream().map(ws -> {
            WorkspaceRespDTO dto = new WorkspaceRespDTO();
            dto.setId(ws.getId());
            dto.setName(ws.getName());
            dto.setDescription(ws.getDescription());
            dto.setStatus(ws.getStatus());
            dto.setCreatedAt(ws.getCreatedAt());
            dto.setMemberCount(workspaceUserMapper.countByWorkspaceId(ws.getId()));
            dto.setProjectCount(0L);
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createWorkspace(WorkspaceCreateReqDTO reqDTO) {
        if (workspaceMapper.findByName(reqDTO.getName()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NAME_EXISTS);
        }

        Workspace workspace = new Workspace();
        workspace.setName(reqDTO.getName());
        workspace.setDescription(reqDTO.getDescription());
        workspace.setStatus(Constants.Status.ACTIVE);
        workspaceMapper.insert(workspace);
        return workspace.getId().toString();
    }

    @Override
    public WorkspaceRespDTO getWorkspaceDetail(UUID id) {
        Workspace workspace = workspaceMapper.selectById(id);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }
        WorkspaceRespDTO dto = new WorkspaceRespDTO();
        dto.setId(workspace.getId());
        dto.setName(workspace.getName());
        dto.setDescription(workspace.getDescription());
        dto.setStatus(workspace.getStatus());
        dto.setCreatedAt(workspace.getCreatedAt());
        dto.setMemberCount(workspaceUserMapper.countByWorkspaceId(workspace.getId()));
        dto.setProjectCount(0L);
        return dto;
    }

    @Override
    public WorkspaceRespDTO updateWorkspace(UUID id, WorkspaceUpdateReqDTO reqDTO) {
        Workspace workspace = workspaceMapper.selectById(id);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }
        if (Constants.Status.DISSOLVED.equals(workspace.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }
        Workspace update = new Workspace();
        update.setId(id);
        if (StringUtils.hasText(reqDTO.getName())) {
            Workspace existing = workspaceMapper.findByName(reqDTO.getName());
            if (existing != null && !existing.getId().equals(id)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NAME_EXISTS);
            }
            update.setName(reqDTO.getName());
        }
        if (reqDTO.getDescription() != null) {
            update.setDescription(reqDTO.getDescription());
        }
        workspaceMapper.updateById(update);
        return getWorkspaceDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dissolveWorkspace(UUID id) {
        Workspace workspace = workspaceMapper.selectById(id);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }
        Workspace update = new Workspace();
        update.setId(id);
        update.setStatus(Constants.Status.DISSOLVED);
        workspaceMapper.updateById(update);
        workspaceUserMapper.deleteByWorkspaceId(id);
    }

    @Override
    public PageResult<WorkspaceMemberRespDTO> getWorkspaceMembers(UUID id, Integer pageNo, Integer pageSize) {
        Workspace workspace = workspaceMapper.selectById(id);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        PageResult<WorkspaceUser> page = workspaceUserMapper.findPageByWorkspaceId(
                new xyz.migoo.framework.common.pojo.PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, id);

        List<WorkspaceMemberRespDTO> records = page.getList().stream().map(wu -> {
            SysUser user = userMapper.selectById(wu.getUserId());
            if (user == null) return null;
            WorkspaceMemberRespDTO dto = new WorkspaceMemberRespDTO();
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setEmail(user.getEmail());
            dto.setAvatarUrl(user.getAvatarUrl());
            dto.setWorkspaceRole(wu.getWorkspaceRole());
            dto.setJoinedAt(wu.getJoinedAt());
            return dto;
        }).filter(Objects::nonNull).collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> addWorkspaceMembers(UUID id, List<WorkspaceMembersAddReqDTO.MemberItem> members) {
        Workspace workspace = workspaceMapper.selectById(id);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        List<String> skippedUserIds = new ArrayList<>();
        for (WorkspaceMembersAddReqDTO.MemberItem member : members) {
            SysUser user = userMapper.selectById(member.getUserId());
            if (user == null || !Constants.Status.ACTIVE.equals(user.getStatus())) {
                continue;
            }
            if (workspaceUserMapper.existsByWorkspaceIdAndUserId(id, member.getUserId())) {
                skippedUserIds.add(member.getUserId().toString());
                continue;
            }

            WorkspaceUser wu = new WorkspaceUser();
            wu.setUserId(member.getUserId());
            wu.setWorkspaceId(id);
            wu.setWorkspaceRole(member.getWorkspaceRole() != null
                    ? member.getWorkspaceRole() : Constants.WorkspaceRole.MEMBER_ID);
            wu.setJoinedAt(LocalDateTime.now());
            workspaceUserMapper.insert(wu);
        }
        return skippedUserIds;
    }

    @Override
    public void updateWorkspaceMemberRole(UUID id, UUID userId, UUID workspaceRole) {
        WorkspaceUser wu = workspaceUserMapper.findByWorkspaceIdAndUserId(id, userId);
        if (wu == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        if (Constants.WorkspaceRole.ADMIN_ID.equals(wu.getWorkspaceRole())
                && !Constants.WorkspaceRole.ADMIN_ID.equals(workspaceRole)) {
            long adminCount = workspaceUserMapper.countByWorkspaceIdAndRole(id, Constants.WorkspaceRole.ADMIN_ID);
            if (adminCount <= 1) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.MUST_KEEP_ONE_WORKSPACE_ADMIN);
            }
        }
        WorkspaceUser update = new WorkspaceUser();
        update.setId(wu.getId());
        update.setWorkspaceRole(workspaceRole);
        workspaceUserMapper.updateById(update);
    }

    @Override
    public void removeWorkspaceMember(UUID id, UUID userId) {
        WorkspaceUser wu = workspaceUserMapper.findByWorkspaceIdAndUserId(id, userId);
        if (wu == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        if (Constants.WorkspaceRole.ADMIN_ID.equals(wu.getWorkspaceRole())) {
            long adminCount = workspaceUserMapper.countByWorkspaceIdAndRole(id, Constants.WorkspaceRole.ADMIN_ID);
            if (adminCount <= 1) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.MUST_KEEP_ONE_WORKSPACE_ADMIN);
            }
        }
        workspaceUserMapper.deleteById(wu.getId());
    }
}
