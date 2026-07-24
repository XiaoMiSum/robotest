package io.github.xiaomisum.robotest.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMyRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.admin.MyWorkspaceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

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
        // 鏌ヨ鐢ㄦ埛鍏宠仈鐨勫伐浣滅┖闂?
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

        // 鎵归噺鏌ヨ宸ヤ綔绌洪棿淇℃伅
        List<UUID> workspaceIds = workspaceUserPage.getList().stream()
                .map(WorkspaceUser::getWorkspaceId)
                .collect(Collectors.toList());
        Map<UUID, Workspace> workspaceMap = workspaceMapper.selectList(Workspace::getId, workspaceIds)
                .stream()
                .collect(Collectors.toMap(Workspace::getId, w -> w));

        // 鎵归噺鏌ヨ姣忎釜宸ヤ綔绌洪棿鐨勬垚鍛樻暟
        Map<UUID, Long> memberCountMap = workspaceUserPage.getList().stream()
                .collect(Collectors.toMap(
                        WorkspaceUser::getWorkspaceId,
                        wu -> workspaceUserMapper.selectCount(WorkspaceUser::getWorkspaceId, wu.getWorkspaceId()),
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
            dto.setDefaultProjectName(null);

            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(records, workspaceUserPage.getTotal());
    }

    @Override
    public void setActiveWorkspace(UUID userId, UUID workspaceId) {
        // 鏍￠獙鐢ㄦ埛瀛樺湪
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }

        // 鏍￠獙宸ヤ綔绌洪棿瀛樺湪
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        // 鏍￠獙鐢ㄦ埛灞炰簬璇ュ伐浣滅┖闂?
        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        // 鏇存柊鐢ㄦ埛鐨勬椿璺冨伐浣滅┖闂?
        user.setLastActiveWorkspaceId(workspaceId.toString());
        userMapper.updateById(user);
    }
}
