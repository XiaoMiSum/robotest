package io.github.xiaomisum.robotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMyRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.MyWorkspaceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MyWorkspaceServiceimpl implements MyWorkspaceService {

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;

    @Override
    public PageResult<WorkspaceMyRespDTO> getMyWorkspacePage(String userId, Integer pageNo, Integer pageSize) {
        // 查询用户关联的工作空间
        LambdaQueryWrapper<WorkspaceUser> wrapper = new LambdaQueryWrapper<WorkspaceUser>()
                .eq(WorkspaceUser::getUserId, userId)
                .orderByDesc(WorkspaceUser::getJoinedAt);

        PageResult<WorkspaceUser> workspaceUserPage = workspaceUserMapper.selectPage(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, wrapper);

        if (workspaceUserPage.getList().isEmpty()) {
            return new PageResult<>(List.of(), 0L);
        }

        // 批量查询工作空间信息
        List<String> workspaceIds = workspaceUserPage.getList().stream()
                .map(WorkspaceUser::getWorkspaceId)
                .collect(Collectors.toList());
        Map<String, Workspace> workspaceMap = workspaceMapper.selectList(Workspace::getId, workspaceIds)
                .stream()
                .collect(Collectors.toMap(Workspace::getId, w -> w));

        // 批量查询每个工作空间的成员数
        Map<String, Long> memberCountMap = workspaceUserPage.getList().stream()
                .collect(Collectors.toMap(
                        WorkspaceUser::getWorkspaceId,
                        wu -> workspaceUserMapper.selectCount(WorkspaceUser::getWorkspaceId, wu.getWorkspaceId()),
                        (v1, v2) -> v1
                ));

        // 组装响应
        List<WorkspaceMyRespDTO> records = workspaceUserPage.getList().stream().map(wu -> {
            WorkspaceMyRespDTO dto = new WorkspaceMyRespDTO();
            dto.setId(wu.getWorkspaceId());
            dto.setWorkspaceRole(wu.getWorkspaceRole());
            dto.setDefaultProjectId(wu.getDefaultProjectId());

            Workspace workspace = workspaceMap.get(wu.getWorkspaceId());
            if (workspace != null) {
                dto.setName(workspace.getName());
                dto.setDescription(workspace.getDescription());
                dto.setStatus(workspace.getStatus());
                dto.setCreatedAt(workspace.getCreatedAt());
            }

            dto.setMemberCount(memberCountMap.getOrDefault(wu.getWorkspaceId(), 0L));
            dto.setProjectCount(0L);
            dto.setDefaultProjectName(null);

            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(records, workspaceUserPage.getTotal());
    }

    @Override
    public void setActiveWorkspace(String userId, String workspaceId) {
        // 校验用户存在
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }

        // 校验工作空间存在
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        // 校验用户属于该工作空间
        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        // 更新用户的活跃工作空间
        user.setLastActiveWorkspaceId(workspaceId);
        userMapper.updateById(user);
    }
}
