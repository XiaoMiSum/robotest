package io.github.xiaomisum.robotest.service.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    public PageResult<WorkspaceRespDTO> getWorkspacePage(String keyword, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<Workspace> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Workspace::getName, keyword);
        }
        wrapper.orderByDesc(Workspace::getCreatedAt);

        PageResult<Workspace> page = workspaceMapper.selectPage(
                new xyz.migoo.framework.common.pojo.PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, wrapper);

        List<WorkspaceRespDTO> records = page.getList().stream().map(ws -> {
            WorkspaceRespDTO dto = new WorkspaceRespDTO();
            dto.setId(ws.getId());
            dto.setName(ws.getName());
            dto.setDescription(ws.getDescription());
            dto.setStatus(ws.getStatus());
            dto.setCreatedAt(ws.getCreatedAt());
            dto.setMemberCount(workspaceUserMapper.selectCount(WorkspaceUser::getWorkspaceId, ws.getId()));
            // projectCount 闇€瑕佺瓑 project 妯″潡瀹炵幇锛屾殏璁句负0
            dto.setProjectCount(0L);
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createWorkspace(WorkspaceCreateReqDTO reqDTO) {
        if (workspaceMapper.selectOne(Workspace::getName, reqDTO.getName()) != null) {
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
    public WorkspaceRespDTO getWorkspaceDetail(String id) {
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
        dto.setMemberCount(workspaceUserMapper.selectCount(WorkspaceUser::getWorkspaceId, workspace.getId()));
        dto.setProjectCount(0L);
        return dto;
    }

    @Override
    public WorkspaceRespDTO updateWorkspace(String id, WorkspaceUpdateReqDTO reqDTO) {
        Workspace workspace = workspaceMapper.selectById(id);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }
        if (Constants.Status.DISSOLVED.equals(workspace.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }
        if (StringUtils.hasText(reqDTO.getName())) {
            Workspace existing = workspaceMapper.selectOne(Workspace::getName, reqDTO.getName());
            if (existing != null && !existing.getId().equals(id)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NAME_EXISTS);
            }
            workspace.setName(reqDTO.getName());
        }
        if (reqDTO.getDescription() != null) {
            workspace.setDescription(reqDTO.getDescription());
        }
        workspaceMapper.updateById(workspace);
        return getWorkspaceDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dissolveWorkspace(String id) {
        Workspace workspace = workspaceMapper.selectById(id);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }
        // TODO: 妫€鏌ュ伐浣滅┖闂翠笅鏄惁鏈夐」鐩?
        workspace.setStatus(Constants.Status.DISSOLVED);
        workspaceMapper.updateById(workspace);
        // 鍒犻櫎鎵€鏈夋垚鍛樺叧鑱?
        workspaceUserMapper.delete(new LambdaQueryWrapper<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, id));
    }

    @Override
    public PageResult<WorkspaceMemberRespDTO> getWorkspaceMembers(String id, Integer pageNo, Integer pageSize) {
        Workspace workspace = workspaceMapper.selectById(id);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        LambdaQueryWrapper<WorkspaceUser> wrapper = new LambdaQueryWrapper<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, id);

        PageResult<WorkspaceUser> page = workspaceUserMapper.selectPage(
                new xyz.migoo.framework.common.pojo.PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, wrapper);

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
        }).filter(dto -> dto != null).collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> addWorkspaceMembers(String id, List<WorkspaceMembersAddReqDTO.MemberItem> members) {
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
            // 妫€鏌ユ槸鍚﹀凡鍦ㄥ伐浣滅┖闂?
            Long count = workspaceUserMapper.selectCount(new LambdaQueryWrapper<WorkspaceUser>()
                    .eq(WorkspaceUser::getUserId, member.getUserId())
                    .eq(WorkspaceUser::getWorkspaceId, id));
            if (count > 0) {
                skippedUserIds.add(member.getUserId().toString());
                continue;
            }

            WorkspaceUser wu = new WorkspaceUser();
            wu.setUserId(member.getUserId().toString());
            wu.setWorkspaceId(id);
            wu.setWorkspaceRole(StringUtils.hasText(member.getWorkspaceRole()) ? member.getWorkspaceRole() : ErrorCodeConstants.WORKSPACE_ROLE_MEMBER_ID);
            wu.setJoinedAt(LocalDateTime.now());
            workspaceUserMapper.insert(wu);
        }
        return skippedUserIds;
    }

    @Override
    public void updateWorkspaceMemberRole(String id, String userId, String workspaceRole) {
        WorkspaceUser wu = workspaceUserMapper.selectOne(new LambdaQueryWrapper<WorkspaceUser>()
                .eq(WorkspaceUser::getUserId, userId)
                .eq(WorkspaceUser::getWorkspaceId, id));
        if (wu == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        // 妫€鏌ユ槸鍚︽槸闄嶇骇鍞竴绠＄悊鍛?
        if (ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(wu.getWorkspaceRole())
                && !ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(workspaceRole)) {
            Long adminCount = workspaceUserMapper.selectCount(new LambdaQueryWrapper<WorkspaceUser>()
                    .eq(WorkspaceUser::getWorkspaceId, id)
                    .eq(WorkspaceUser::getWorkspaceRole, ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID));
            if (adminCount <= 1) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.MUST_KEEP_ONE_WORKSPACE_ADMIN);
            }
        }
        wu.setWorkspaceRole(workspaceRole);
        workspaceUserMapper.updateById(wu);
    }

    @Override
    public void removeWorkspaceMember(String id, String userId) {
        WorkspaceUser wu = workspaceUserMapper.selectOne(new LambdaQueryWrapper<WorkspaceUser>()
                .eq(WorkspaceUser::getUserId, userId)
                .eq(WorkspaceUser::getWorkspaceId, id));
        if (wu == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        // 妫€鏌ユ槸鍚︽槸绉婚櫎鍞竴绠＄悊鍛?
        if (ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(wu.getWorkspaceRole())) {
            Long adminCount = workspaceUserMapper.selectCount(new LambdaQueryWrapper<WorkspaceUser>()
                    .eq(WorkspaceUser::getWorkspaceId, id)
                    .eq(WorkspaceUser::getWorkspaceRole, ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID));
            if (adminCount <= 1) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.MUST_KEEP_ONE_WORKSPACE_ADMIN);
            }
        }
        workspaceUserMapper.deleteById(wu.getId());
    }
}
