package io.github.xiaomisum.robotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.Constants;
import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceDefaultProjectReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceContextRespDTO;
import io.github.xiaomisum.robotest.model.entity.Project;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.ProjectMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.WorkspaceContextService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;

import java.util.UUID;

@Service
public class WorkspaceContextServiceImpl implements WorkspaceContextService {

    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private ProjectMapper projectMapper;

    @Override
    public WorkspaceContextRespDTO getWorkspaceContext(String userId, String workspaceId) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        return buildContextRespDTO(workspace, workspaceUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceContextRespDTO updateWorkspace(String userId, String workspaceId, WorkspaceUpdateReqDTO reqDTO) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null || !ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        if (StringUtils.hasText(reqDTO.getName())) {
            Workspace existing = workspaceMapper.selectOne(
                    new LambdaQueryWrapper<Workspace>()
                            .eq(Workspace::getName, reqDTO.getName())
                            .ne(Workspace::getId, workspaceId));
            if (existing != null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NAME_EXISTS);
            }
            workspace.setName(reqDTO.getName());
        }
        if (reqDTO.getDescription() != null) {
            workspace.setDescription(reqDTO.getDescription());
        }
        workspaceMapper.updateById(workspace);

        return buildContextRespDTO(workspace, workspaceUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceContextRespDTO setDefaultProject(String userId, String workspaceId, WorkspaceDefaultProjectReqDTO reqDTO) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        if (reqDTO.getProjectId() != null) {
            Project project = projectMapper.selectById(reqDTO.getProjectId().toString());
            if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
            }
            if (!Constants.Status.ACTIVE.equals(project.getStatus())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.DEFAULT_PROJECT_MUST_BE_ACTIVE);
            }
        }

        workspaceUser.setDefaultProjectId(reqDTO.getProjectId().toString());
        workspaceUserMapper.updateById(workspaceUser);

        return buildContextRespDTO(workspace, workspaceUser);
    }

    private WorkspaceContextRespDTO buildContextRespDTO(Workspace workspace, WorkspaceUser workspaceUser) {
        WorkspaceContextRespDTO dto = new WorkspaceContextRespDTO();
        dto.setId(workspace.getId());
        dto.setName(workspace.getName());
        dto.setDescription(workspace.getDescription());
        dto.setStatus(workspace.getStatus());
        dto.setCreatedAt(workspace.getCreatedAt());
        dto.setWorkspaceRole(workspaceUser.getWorkspaceRole());
        dto.setDefaultProjectId(workspaceUser.getDefaultProjectId() != null ? UUID.fromString(workspaceUser.getDefaultProjectId()) : null);

        Long memberCount = workspaceUserMapper.selectCount(
                WorkspaceUser::getWorkspaceId, workspace.getId());
        dto.setMemberCount(memberCount);

        Long projectCount = projectMapper.selectCount(
                Project::getWorkspaceId, workspace.getId());
        dto.setProjectCount(projectCount);

        if (StringUtils.hasText(workspaceUser.getDefaultProjectId())) {
            Project defaultProject = projectMapper.selectById(workspaceUser.getDefaultProjectId());
            if (defaultProject != null) {
                dto.setDefaultProjectName(defaultProject.getName());
            }
        }

        return dto;
    }
}
