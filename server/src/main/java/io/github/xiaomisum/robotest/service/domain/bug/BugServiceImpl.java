package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.BugConvertMapper;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.*;

@Service
public class BugServiceImpl implements BugService {

    private static final Set<String> VALID_BUG_TYPES = Set.of(
            Constants.BugType.CODE_ERROR, Constants.BugType.UI_IMPROVEMENT,
            Constants.BugType.DESIGN_DEFECT, Constants.BugType.CONFIGURATION,
            Constants.BugType.INSTALLATION, Constants.BugType.SECURITY,
            Constants.BugType.PERFORMANCE, Constants.BugType.STANDARD_SPEC,
            Constants.BugType.OTHER);

    @Resource
    private BugMapper bugMapper;
    @Resource
    private BugLogMapper bugLogMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private BugStatusChangeService bugStatusChangeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createBug(UUID projectId, UUID userId, BugCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        validateBugType(reqDTO.getBugType());
        validateModuleInProject(projectId, reqDTO.getModuleId());
        validateAssigneeInWorkspace(projectId, reqDTO.getAssigneeId());

        Bug bug = BugConvertMapper.INSTANCE.toEntity(reqDTO);
        bug.setProjectId(projectId);
        bug.setStatus(BugStatus.ACTIVE.getCode());
        bug.setConfirmed(false);
        bug.setReopenCount(0);
        bug.setReporterId(userId);
        bugMapper.insert(bug);

        writeBugLog(bug.getId(), userId, Constants.BugOperation.CREATE, "创建缺陷");
        eventPublisher.publishEvent(new BugChangedEvent(bug.getId(), BugChangeOp.CREATED));

        return bug.getId().toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBug(UUID bugId, UUID userId, BugUpdateReqDTO reqDTO) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(bug.getProjectId(), userId);
        if (BugStatus.CLOSED.getCode().equals(bug.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_CLOSED_EDIT_FORBIDDEN);
        }

        Bug update = new Bug();
        update.setId(bugId);
        if (StringUtils.hasText(reqDTO.getTitle())) {
            update.setTitle(reqDTO.getTitle());
        }
        if (StringUtils.hasText(reqDTO.getSeverity())) {
            update.setSeverity(reqDTO.getSeverity());
        }
        if (StringUtils.hasText(reqDTO.getPriority())) {
            update.setPriority(reqDTO.getPriority());
        }
        if (StringUtils.hasText(reqDTO.getBugType())) {
            validateBugType(reqDTO.getBugType());
            update.setBugType(reqDTO.getBugType());
        }
        if (reqDTO.getReproSteps() != null) {
            update.setReproSteps(reqDTO.getReproSteps());
        }
        if (reqDTO.getModuleId() != null) {
            validateModuleInProject(bug.getProjectId(), reqDTO.getModuleId());
            update.setModuleId(reqDTO.getModuleId());
        }
        if (reqDTO.getKeywords() != null) {
            update.setKeywords(reqDTO.getKeywords());
        }
        if (reqDTO.getDueDate() != null) {
            update.setDueDate(reqDTO.getDueDate());
        }
        if (reqDTO.getAssigneeId() != null) {
            validateAssigneeInWorkspace(bug.getProjectId(), reqDTO.getAssigneeId());
            update.setAssigneeId(reqDTO.getAssigneeId());
        }
        boolean clearCase = "".equals(reqDTO.getRelatedCaseId());
        boolean clearPlan = "".equals(reqDTO.getRelatedPlanId());
        if (StringUtils.hasText(reqDTO.getRelatedCaseId())) {
            update.setRelatedCaseId(parseRelationId(reqDTO.getRelatedCaseId()));
        }
        if (StringUtils.hasText(reqDTO.getRelatedPlanId())) {
            update.setRelatedPlanId(parseRelationId(reqDTO.getRelatedPlanId()));
        }
        bugMapper.updateById(update);
        if (clearCase || clearPlan) {
            bugMapper.clearRelationById(bugId, clearCase, clearPlan);
        }

        writeBugLog(bugId, userId, Constants.BugOperation.UPDATE, "更新缺陷");
        if (StringUtils.hasText(reqDTO.getTitle()) || reqDTO.getReproSteps() != null) {
            eventPublisher.publishEvent(new BugChangedEvent(bugId, BugChangeOp.UPDATED));
        }
    }

    private UUID parseRelationId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_RELATION_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeBugStatus(UUID bugId, UUID userId, BugStatusChangeReqDTO reqDTO) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(bug.getProjectId(), userId);
        bugStatusChangeService.changeBugStatus(bug, userId, reqDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmBug(UUID bugId, UUID userId) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(bug.getProjectId(), userId);
        if (!BugStatus.ACTIVE.getCode().equals(bug.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_CONFIRM_INVALID_STATUS);
        }
        if (Boolean.TRUE.equals(bug.getConfirmed())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ALREADY_CONFIRMED);
        }

        Bug update = new Bug();
        update.setId(bugId);
        update.setConfirmed(true);
        bugMapper.updateById(update);
        writeBugLog(bugId, userId, Constants.BugOperation.CONFIRM, "确认缺陷");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignBug(UUID bugId, UUID userId, UUID assigneeId) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(bug.getProjectId(), userId);
        if (BugStatus.CLOSED.getCode().equals(bug.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_CLOSED_EDIT_FORBIDDEN);
        }

        SysUser assignee = userMapper.selectById(assigneeId);
        if (assignee == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ASSIGNEE_NOT_IN_WORKSPACE);
        }
        validateAssigneeInWorkspace(bug.getProjectId(), assigneeId);

        Bug update = new Bug();
        update.setId(bugId);
        update.setAssigneeId(assigneeId);
        bugMapper.updateById(update);
        writeBugLog(bugId, userId, Constants.BugOperation.ASSIGN,
                String.format("指派处理人为「%s」", assignee.getUsername()));
    }

    private void validateAssigneeInWorkspace(UUID projectId, UUID assigneeId) {
        if (assigneeId == null || projectId == null) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return;
        }
        WorkspaceUser wu = workspaceUserMapper.findByWorkspaceIdAndUserId(project.getWorkspaceId(), assigneeId);
        if (wu == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ASSIGNEE_NOT_IN_WORKSPACE);
        }
    }

    private void validateBugType(String bugType) {
        if (!VALID_BUG_TYPES.contains(bugType)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_TYPE_INVALID);
        }
    }

    private void validateModuleInProject(UUID projectId, UUID moduleId) {
        if (moduleId == null) {
            return;
        }
        ProjectModule module = projectModuleMapper.selectById(moduleId);
        if (module == null || !projectId.equals(module.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_MODULE_NOT_FOUND);
        }
    }

    private void writeBugLog(UUID bugId, UUID userId, String operationType, String content) {
        BugLog log = new BugLog();
        log.setBugId(bugId);
        log.setOperatorId(userId);
        log.setOperationType(operationType);
        log.setContent(content);
        bugLogMapper.insert(log);
    }
}
