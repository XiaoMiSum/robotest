package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceDefaultProjectReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceContextRespDTO;
import io.github.xiaomisum.robotest.model.entity.Project;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

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
    public WorkspaceContextRespDTO getWorkspaceContext(UUID userId, UUID workspaceId) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        return buildContextRespDTO(workspace, workspaceUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceContextRespDTO updateWorkspace(UUID userId, UUID workspaceId, WorkspaceUpdateReqDTO reqDTO) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null || !Constants.WorkspaceRole.ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        Workspace update = new Workspace();
        update.setId(workspaceId);
        if (reqDTO.getName() != null && !reqDTO.getName().isEmpty()) {
            Workspace existing = workspaceMapper.findByName(reqDTO.getName());
            if (existing != null && !existing.getId().equals(workspaceId)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NAME_EXISTS);
            }
            update.setName(reqDTO.getName());
            workspace.setName(reqDTO.getName());
        }
        if (reqDTO.getDescription() != null) {
            update.setDescription(reqDTO.getDescription());
            workspace.setDescription(reqDTO.getDescription());
        }
        workspaceMapper.updateById(update);

        return buildContextRespDTO(workspace, workspaceUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceContextRespDTO setDefaultProject(UUID userId, UUID workspaceId, WorkspaceDefaultProjectReqDTO reqDTO) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        if (reqDTO.getProjectId() != null) {
            Project project = projectMapper.selectById(reqDTO.getProjectId());
            if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
            }
            if (!Constants.Status.ACTIVE.equals(project.getStatus())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.DEFAULT_PROJECT_MUST_BE_ACTIVE);
            }
        }

        workspaceUserMapper.update(null, new LambdaUpdateWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getId, workspaceUser.getId())
                .set(WorkspaceUser::getDefaultProjectId, reqDTO.getProjectId()));
        workspaceUser.setDefaultProjectId(reqDTO.getProjectId());

        return buildContextRespDTO(workspace, workspaceUser);
    }

    private WorkspaceContextRespDTO buildContextRespDTO(Workspace workspace, WorkspaceUser workspaceUser) {
        WorkspaceContextRespDTO dto = new WorkspaceContextRespDTO();
        dto.setId(workspace.getId());
        dto.setName(workspace.getName());
        dto.setDescription(workspace.getDescription());
        dto.setStatus(workspace.getStatus());
        dto.setCreatedAt(workspace.getCreatedAt());
        dto.setWorkspaceRole(workspaceUser.getWorkspaceRole().toString());
        dto.setDefaultProjectId(workspaceUser.getDefaultProjectId());

        long memberCount = workspaceUserMapper.countByWorkspaceId(workspace.getId());
        dto.setMemberCount(memberCount);

        long projectCount = projectMapper.countByWorkspaceId(workspace.getId());
        dto.setProjectCount(projectCount);

        if (workspaceUser.getDefaultProjectId() != null) {
            Project defaultProject = projectMapper.selectById(workspaceUser.getDefaultProjectId());
            if (defaultProject != null) {
                dto.setDefaultProjectName(defaultProject.getName());
            }
        }

        return dto;
    }
}
