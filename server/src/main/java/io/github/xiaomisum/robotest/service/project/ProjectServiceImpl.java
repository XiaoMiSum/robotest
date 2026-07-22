package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.ProjectConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.ProjectArchiveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ProjectCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ProjectUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ProjectRespDTO;
import io.github.xiaomisum.robotest.model.entity.Project;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.ProjectMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.project.ProjectService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;

import java.time.LocalDateTime;
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

    @Override
    public PageResult<ProjectRespDTO> getProjectPage(String workspaceId, String userId, String keyword,
                                                      String status, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .eq(Project::getWorkspaceId, workspaceId);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Project::getName, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Project::getStatus, status);
        }
        wrapper.orderByDesc(Project::getCreatedAt);

        PageResult<Project> page = projectMapper.selectPage(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, wrapper);

        WorkspaceUser currentUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        String defaultProjectId = currentUser != null ? currentUser.getDefaultProjectId() : null;

        List<ProjectRespDTO> records = page.getList().stream()
                .map(p -> {
                    ProjectRespDTO dto = ProjectConvertMapper.INSTANCE.toRespDTO(p, defaultProjectId);
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
    public ProjectRespDTO getProjectDetail(String workspaceId, String projectId) {
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
    public ProjectRespDTO createProject(String userId, String workspaceId, ProjectCreateReqDTO reqDTO) {
        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        Project existing = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getWorkspaceId, workspaceId)
                        .eq(Project::getName, reqDTO.getName()));
        if (existing != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NAME_EXISTS);
        }

        if (reqDTO.getStartTime() != null && reqDTO.getEndTime() != null
                && reqDTO.getStartTime().isAfter(reqDTO.getEndTime())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        Project project = new Project();
        project.setWorkspaceId(workspaceId);
        project.setName(reqDTO.getName());
        project.setDescription(reqDTO.getDescription());
        project.setStatus(Constants.Status.ACTIVE);
        project.setStartTime(reqDTO.getStartTime());
        project.setEndTime(reqDTO.getEndTime());
        project.setCreatedBy(userId);
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
    public ProjectRespDTO updateProject(String userId, String workspaceId, String projectId,
                                         ProjectUpdateReqDTO reqDTO) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }

        if (Constants.Status.ARCHIVED.equals(project.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_ARCHIVED);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        boolean isAdmin = ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(workspaceUser.getWorkspaceRole());
        boolean isCreator = userId.equals(project.getCreatedBy());
        if (!isAdmin && !isCreator) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        if (StringUtils.hasText(reqDTO.getName())) {
            Project existing = projectMapper.selectOne(
                    new LambdaQueryWrapper<Project>()
                            .eq(Project::getWorkspaceId, workspaceId)
                            .eq(Project::getName, reqDTO.getName())
                            .ne(Project::getId, projectId));
            if (existing != null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NAME_EXISTS);
            }
            project.setName(reqDTO.getName());
        }
        if (reqDTO.getDescription() != null) {
            project.setDescription(reqDTO.getDescription());
        }
        if (reqDTO.getStartTime() != null) {
            project.setStartTime(reqDTO.getStartTime());
        }
        if (reqDTO.getEndTime() != null) {
            project.setEndTime(reqDTO.getEndTime());
        }
        projectMapper.updateById(project);

        ProjectRespDTO dto = ProjectConvertMapper.INSTANCE.toRespDTO(project, null);
        SysUser creator = userMapper.selectById(project.getCreatedBy());
        dto.setCreatedBy(ProjectConvertMapper.INSTANCE.toCreatorInfo(
                creator != null ? creator.getId() : null,
                creator != null ? creator.getUsername() : null));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveProject(String userId, String workspaceId, String projectId,
                                ProjectArchiveReqDTO reqDTO) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null || !ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        if (reqDTO.getArchived()) {
            project.setStatus(Constants.Status.ARCHIVED);
        } else {
            project.setStatus(Constants.Status.ACTIVE);
        }
        projectMapper.updateById(project);

        if (reqDTO.getArchived()) {
            workspaceUserMapper.update(null,
                    new LambdaUpdateWrapper<WorkspaceUser>()
                            .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                            .eq(WorkspaceUser::getDefaultProjectId, projectId)
                            .set(WorkspaceUser::getDefaultProjectId, null));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(String userId, String workspaceId, String projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null || !ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        projectMapper.deleteById(projectId);

        workspaceUserMapper.update(null,
                new LambdaUpdateWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                        .eq(WorkspaceUser::getDefaultProjectId, projectId)
                        .set(WorkspaceUser::getDefaultProjectId, null));
    }
}
