package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.ProjectConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.ProjectArchiveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ProjectCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ProjectUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ProjectRespDTO;
import io.github.xiaomisum.robotest.model.entity.Project;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.TestPlan;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.ProjectMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

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
        LambdaQueryWrapperX<Project> wrapper = new LambdaQueryWrapperX<Project>()
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
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));

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
        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        Project existing = projectMapper.selectOne(
                new LambdaQueryWrapperX<Project>()
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

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
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
            Project existing = projectMapper.selectOne(
                    new LambdaQueryWrapperX<Project>()
                            .eq(Project::getWorkspaceId, workspaceId)
                            .eq(Project::getName, reqDTO.getName())
                            .ne(Project::getId, projectId));
            if (existing != null) {
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

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null || !Constants.WorkspaceRole.ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        Project update = new Project();
        update.setId(projectId);
        if (reqDTO.getArchived()) {
            // 归档前校验无进行中的测试计划
            Long activePlanCount = testPlanMapper.selectCount(
                    new LambdaQueryWrapperX<TestPlan>()
                            .eq(TestPlan::getProjectId, projectId)
                            .in(TestPlan::getStatus, Constants.Status.NEW, Constants.Status.IN_PROGRESS));
            if (activePlanCount > 0) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_HAS_ACTIVE_PLANS);
            }
            update.setStatus(Constants.Status.ARCHIVED);
        } else {
            update.setStatus(Constants.Status.ACTIVE);
        }
        projectMapper.updateById(update);

        if (reqDTO.getArchived()) {
            workspaceUserMapper.update(null,
                    new LambdaUpdateWrapperX<WorkspaceUser>()
                            .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                            .eq(WorkspaceUser::getDefaultProjectId, projectId)
                            .set(WorkspaceUser::getDefaultProjectId, null));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(UUID userId, UUID workspaceId, UUID projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, userId)
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null || !Constants.WorkspaceRole.ADMIN_ID.equals(workspaceUser.getWorkspaceRole())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        projectMapper.deleteById(projectId);

        workspaceUserMapper.update(null,
                new LambdaUpdateWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                        .eq(WorkspaceUser::getDefaultProjectId, projectId)
                        .set(WorkspaceUser::getDefaultProjectId, null));
    }
}
