package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.ProjectConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.workspace.ProjectArchiveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.ProjectCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.ProjectUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private TestPlanMapper testPlanMapper;

    @Override
    public PageResult<ProjectRespDTO> getProjectPage(UUID workspaceId, UUID userId, String keyword,
                                                     String status, Integer pageNo, Integer pageSize) {
        PageResult<Project> page = projectMapper.findPage(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, workspaceId, keyword, status);

        WorkspaceUser currentUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);

        List<ProjectRespDTO> records = page.getList().stream()
                .map(p -> {
                    String defaultProjectIdStr = currentUser != null && currentUser.getDefaultProjectId() != null
                            ? currentUser.getDefaultProjectId().toString() : null;
                    ProjectRespDTO dto = ProjectConvertMapper.INSTANCE.toRespDTO(p, defaultProjectIdStr);
                    SysUser creator = userMapper.selectById(p.getCreatedBy());
                    dto.setCreatedBy(ProjectConvertMapper.INSTANCE.toCreatorInfo(
                            creator != null ? creator.getId() : null,
                            creator != null ? creator.getUsername() : null));
                    return dto;
                })
                .collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    @Override
    public ProjectRespDTO getProjectDetail(UUID workspaceId, UUID projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }
        ProjectRespDTO dto = ProjectConvertMapper.INSTANCE.toRespDTO(project, null);
        SysUser creator = userMapper.selectById(project.getCreatedBy());
        dto.setCreatedBy(ProjectConvertMapper.INSTANCE.toCreatorInfo(
                creator != null ? creator.getId() : null,
                creator != null ? creator.getUsername() : null));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectRespDTO createProject(UUID userId, UUID workspaceId, ProjectCreateReqDTO reqDTO) {
        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        if (projectMapper.findByName(workspaceId, reqDTO.getName()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NAME_EXISTS);
        }

        if (reqDTO.getStartTime() != null && reqDTO.getEndTime() != null
                && reqDTO.getStartTime().isAfter(reqDTO.getEndTime())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        Project project = ProjectConvertMapper.INSTANCE.toEntity(reqDTO);
        project.setWorkspaceId(workspaceId);
        project.setStatus(Constants.Status.ACTIVE);
        project.setCreatedBy(userId.toString());
        projectMapper.insert(project);

        ProjectRespDTO dto = ProjectConvertMapper.INSTANCE.toRespDTO(project, null);
        SysUser creator = userMapper.selectById(project.getCreatedBy());
        dto.setCreatedBy(ProjectConvertMapper.INSTANCE.toCreatorInfo(
                creator != null ? creator.getId() : null,
                creator != null ? creator.getUsername() : null));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectRespDTO updateProject(UUID userId, UUID workspaceId, UUID projectId,
                                        ProjectUpdateReqDTO reqDTO) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }

        if (Constants.Status.ARCHIVED.equals(project.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_ARCHIVED);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        boolean isAdmin = Constants.WorkspaceRole.ADMIN_ID.equals(workspaceUser.getWorkspaceRole());
        boolean isCreator = userId.toString().equals(project.getCreatedBy());
        if (!isAdmin && !isCreator) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        // 查询结果仅用于校验；更新载体只携带前端传入的字段，避免全列覆盖导致并发丢失更新
        Project update = new Project();
        update.setId(projectId);
        if (StringUtils.hasText(reqDTO.getName())) {
            if (projectMapper.findByNameExcludingId(workspaceId, reqDTO.getName(), projectId) != null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NAME_EXISTS);
            }
            update.setName(reqDTO.getName());
            project.setName(reqDTO.getName());
        }
        if (reqDTO.getDescription() != null) {
            update.setDescription(reqDTO.getDescription());
            project.setDescription(reqDTO.getDescription());
        }
        if (reqDTO.getStartTime() != null) {
            update.setStartTime(reqDTO.getStartTime());
            project.setStartTime(reqDTO.getStartTime());
        }
        if (reqDTO.getEndTime() != null) {
            update.setEndTime(reqDTO.getEndTime());
            project.setEndTime(reqDTO.getEndTime());
        }
        projectMapper.updateById(update);

        ProjectRespDTO dto = ProjectConvertMapper.INSTANCE.toRespDTO(project, null);
        SysUser creator = userMapper.selectById(project.getCreatedBy());
        dto.setCreatedBy(ProjectConvertMapper.INSTANCE.toCreatorInfo(
                creator != null ? creator.getId() : null,
                creator != null ? creator.getUsername() : null));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveProject(UUID userId, UUID workspaceId, UUID projectId,
                                ProjectArchiveReqDTO reqDTO) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null || !Constants.WorkspaceRole.ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        Project update = new Project();
        update.setId(projectId);
        if (reqDTO.getArchived()) {
            if (testPlanMapper.countActiveByProjectId(projectId,
                    List.of(Constants.Status.NEW, Constants.Status.IN_PROGRESS)) > 0) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_HAS_ACTIVE_PLANS);
            }
            update.setStatus(Constants.Status.ARCHIVED);
        } else {
            update.setStatus(Constants.Status.ACTIVE);
        }
        projectMapper.updateById(update);

        if (reqDTO.getArchived()) {
            workspaceUserMapper.clearDefaultProjectId(workspaceId, projectId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(UUID userId, UUID workspaceId, UUID projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null || !Constants.WorkspaceRole.ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        projectMapper.deleteById(projectId);
        workspaceUserMapper.clearDefaultProjectId(workspaceId, projectId);
    }
}
