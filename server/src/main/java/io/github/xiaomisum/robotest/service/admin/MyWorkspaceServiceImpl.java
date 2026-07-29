package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MyWorkspaceServiceImpl implements MyWorkspaceService {

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;

    @Override
    public PageResult<WorkspaceMyRespDTO> getMyWorkspacePage(UUID userId, Integer pageNo, Integer pageSize) {
        PageResult<WorkspaceUser> workspaceUserPage = workspaceUserMapper.selectPage(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, new LambdaQueryWrapperX<WorkspaceUser>().eq(WorkspaceUser::getUserId, userId));

        if (workspaceUserPage.getList().isEmpty()) {
            return new PageResult<>(List.of(), 0L);
        }

        List<UUID> workspaceIds = workspaceUserPage.getList().stream()
                .map(WorkspaceUser::getWorkspaceId)
                .collect(Collectors.toList());
        Map<UUID, Workspace> workspaceMap = workspaceMapper.listByIds(workspaceIds)
                .stream()
                .collect(Collectors.toMap(Workspace::getId, w -> w));

        Map<UUID, Long> memberCountMap = workspaceUserPage.getList().stream()
                .collect(Collectors.toMap(
                        WorkspaceUser::getWorkspaceId,
                        wu -> workspaceUserMapper.countByWorkspaceId(wu.getWorkspaceId()),
                        (v1, v2) -> v1
                ));

        // 缁勮鍝嶅簲
        List<WorkspaceMyRespDTO> records = workspaceUserPage.getList().stream().map(wu -> {
            WorkspaceMyRespDTO dto = new WorkspaceMyRespDTO();
            dto.setId(wu.getWorkspaceId());
            dto.setWorkspaceRole(wu.getWorkspaceRole().toString());
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
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(records, workspaceUserPage.getTotal());
    }

    @Override
    public void setActiveWorkspace(UUID userId, UUID workspaceId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }

        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setLastActiveWorkspaceId(workspaceId.toString());
        userMapper.updateById(update);
    }
}
