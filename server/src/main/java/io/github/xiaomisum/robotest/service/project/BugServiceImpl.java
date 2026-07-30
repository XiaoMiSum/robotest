package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.BugConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugStatisticsRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.project.BugService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BugServiceImpl implements BugService {

    private static final Set<String> VALID_BUG_TYPES = Set.of(
            Constants.BugType.CODE_ERROR, Constants.BugType.UI_IMPROVEMENT,
            Constants.BugType.DESIGN_DEFECT, Constants.BugType.CONFIGURATION,
            Constants.BugType.INSTALLATION, Constants.BugType.SECURITY,
            Constants.BugType.PERFORMANCE, Constants.BugType.STANDARD_SPEC,
            Constants.BugType.OTHER);

    private static final Set<String> VALID_RESOLUTIONS = Set.of(
            Constants.BugResolution.FIXED, Constants.BugResolution.BY_DESIGN,
            Constants.BugResolution.DUPLICATE, Constants.BugResolution.EXTERNAL,
            Constants.BugResolution.CANNOT_REPRODUCE, Constants.BugResolution.DEFERRED,
            Constants.BugResolution.WONT_FIX);

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
    private TestCaseModuleMapper testCaseModuleMapper;

    @Override
    public PageResult<BugListRespDTO> getBugPage(UUID projectId, String status, String severity,
                                             String priority, String bugType, UUID assigneeId,
                                             UUID reporterId, UUID resolvedBy, UUID closedBy, String keyword,
                                             Integer pageNo, Integer pageSize) {
        PageResult<Bug> page = bugMapper.findPage(new PageParam() {{
            setPageNo(pageNo);
            setPageSize(pageSize);
        }}, projectId, status, severity, priority, bugType, assigneeId, reporterId, resolvedBy, closedBy, keyword);

        List<BugListRespDTO> dtos = page.getList().stream().map(bug -> {
            BugListRespDTO dto = BugConvertMapper.INSTANCE.toListRespDTO(bug);
            dto.setReporter(BugConvertMapper.INSTANCE.toUserInfo(userMapper.selectById(bug.getReporterId())));
            if (bug.getAssigneeId() != null) {
                dto.setAssignee(BugConvertMapper.INSTANCE.toUserInfo(userMapper.selectById(bug.getAssigneeId())));
            }
            if (bug.getResolvedBy() != null) {
                dto.setResolvedBy(BugConvertMapper.INSTANCE.toUserInfo(userMapper.selectById(bug.getResolvedBy())));
            }
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(dtos, page.getTotal());
    }

    @Override
    public BugDetailRespDTO getBugDetail(UUID bugId) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }

        BugDetailRespDTO dto = BugConvertMapper.INSTANCE.toDetailRespDTO(bug);

        if (bug.getModuleId() != null) {
            dto.setModuleId(bug.getModuleId());
            TestCaseModule module = testCaseModuleMapper.selectById(bug.getModuleId());
            if (module != null) {
                dto.setModuleName(module.getName());
            }
        }
        dto.setResolvedBy(BugConvertMapper.INSTANCE.toDetailUserInfo(userMapper.selectById(bug.getResolvedBy())));
        dto.setClosedBy(BugConvertMapper.INSTANCE.toDetailUserInfo(userMapper.selectById(bug.getClosedBy())));
        dto.setReporter(BugConvertMapper.INSTANCE.toDetailUserInfo(userMapper.selectById(bug.getReporterId())));
        if (bug.getAssigneeId() != null) {
            dto.setAssignee(BugConvertMapper.INSTANCE.toDetailUserInfo(userMapper.selectById(bug.getAssigneeId())));
        }

        List<BugLog> recentLogs = bugLogMapper.findRecentLogs(bugId, 10);
        dto.setRecentLogs(recentLogs.stream().map(log -> {
            BugLogRespDTO logDto = BugConvertMapper.INSTANCE.toLogRespDTO(log);
            SysUser operator = userMapper.selectById(log.getOperatorId());
            if (operator != null) {
                logDto.setOperatorName(operator.getUsername());
            }
            return logDto;
        }).collect(Collectors.toList()));

        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createBug(UUID projectId, UUID userId, BugCreateReqDTO reqDTO) {
        validateBugType(reqDTO.getBugType());
        validateModuleInProject(projectId, reqDTO.getModuleId());
        validateAssigneeInWorkspace(projectId, reqDTO.getAssigneeId());

        Bug bug = BugConvertMapper.INSTANCE.toEntity(reqDTO);
        bug.setProjectId(projectId);
        bug.setStatus(Constants.BugStatus.ACTIVE);
        bug.setConfirmed(false);
        bug.setReopenCount(0);
        bug.setReporterId(userId);
        bugMapper.insert(bug);

        writeBugLog(bug.getId(), userId, Constants.BugOperation.CREATE, "创建缺陷");

        return bug.getId().toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBug(UUID bugId, UUID userId, BugUpdateReqDTO reqDTO) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }
        // 已关闭缺陷不允许编辑，仅允许通过状态机重新激活
        if (Constants.BugStatus.CLOSED.equals(bug.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_CLOSED_EDIT_FORBIDDEN);
        }

        // 查询结果仅用于校验；更新载体只携带前端传入的字段，避免全列覆盖导致并发丢失更新
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
        // status 不再通过 updateBug 修改，须走 changeBugStatus 状态机
        bugMapper.updateById(update);

        writeBugLog(bugId, userId, Constants.BugOperation.UPDATE, "更新缺陷");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeBugStatus(UUID bugId, UUID userId, BugStatusChangeReqDTO reqDTO) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }

        String currentStatus = bug.getStatus();
        String targetStatus = reqDTO.getStatus();

        if (!isValidTransition(currentStatus, targetStatus)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_INVALID_STATUS_TRANSITION);
        }

        switch (targetStatus) {
            case Constants.BugStatus.RESOLVED -> resolveBug(bug, userId, reqDTO);
            case Constants.BugStatus.REJECTED -> rejectBug(bug, userId, reqDTO.getComment());
            case Constants.BugStatus.CLOSED -> closeBug(bug, userId, reqDTO.getComment());
            case Constants.BugStatus.ACTIVE -> reopenBug(bug, userId, reqDTO.getComment());
            default -> throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_INVALID_STATUS_TRANSITION);
        }
    }

    /**
     * 解决缺陷：必填合法 resolution 与备注说明，duplicate 需指定同项目且非自身的原始缺陷
     */
    private void resolveBug(Bug bug, UUID userId, BugStatusChangeReqDTO reqDTO) {
        String resolution = reqDTO.getResolution();
        if (!StringUtils.hasText(resolution)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_RESOLUTION_REQUIRED);
        }
        if (!VALID_RESOLUTIONS.contains(resolution)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_RESOLUTION_INVALID);
        }
        if (!StringUtils.hasText(reqDTO.getComment())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_RESOLVE_COMMENT_REQUIRED);
        }

        UUID duplicateOfBugId = null;
        if (Constants.BugResolution.DUPLICATE.equals(resolution)) {
            duplicateOfBugId = reqDTO.getDuplicateOfBugId();
            if (duplicateOfBugId == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_DUPLICATE_OF_REQUIRED);
            }
            Bug original = bugMapper.selectById(duplicateOfBugId);
            if (original == null || duplicateOfBugId.equals(bug.getId())
                    || !bug.getProjectId().equals(original.getProjectId())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_DUPLICATE_OF_NOT_FOUND);
            }
        }

        // 修复完成后交回创建人验证，处理人回设为创建人
        bugMapper.resolveById(bug.getId(), userId, resolution, duplicateOfBugId, bug.getReporterId());

        writeBugLog(bug.getId(), userId, Constants.BugOperation.RESOLVE,
                String.format("解决缺陷，方案「%s」%s", resolution,
                        StringUtils.hasText(reqDTO.getComment()) ? "，说明：" + reqDTO.getComment() : ""));
    }

    /**
     * 拒绝缺陷：必填拒绝说明，处理人回设为创建人，由创建人决定重新激活或关闭
     */
    private void rejectBug(Bug bug, UUID userId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_REJECT_COMMENT_REQUIRED);
        }

        Bug update = new Bug();
        update.setId(bug.getId());
        update.setStatus(Constants.BugStatus.REJECTED);
        update.setAssigneeId(bug.getReporterId());
        // 记录拒绝人，重开时处理人回设给他
        update.setRejectedBy(userId);
        bugMapper.updateById(update);

        writeBugLog(bug.getId(), userId, Constants.BugOperation.REJECT, "拒绝缺陷，说明：" + comment);
    }

    /**
     * 关闭缺陷：必填关闭说明
     */
    private void closeBug(Bug bug, UUID userId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_CLOSE_COMMENT_REQUIRED);
        }

        Bug update = new Bug();
        update.setId(bug.getId());
        update.setStatus(Constants.BugStatus.CLOSED);
        update.setClosedBy(userId);
        update.setClosedAt(LocalDateTime.now());
        bugMapper.updateById(update);

        writeBugLog(bug.getId(), userId, Constants.BugOperation.CLOSE, "关闭缺陷，说明：" + comment);
    }

    /**
     * 重开（激活）缺陷：必填说明，计数累加并清空解决/关闭信息。
     * 处理人流转：已修复重开给修复人，已拒绝重开给拒绝人（reopenById 会清空两者，须先取值）
     */
    private void reopenBug(Bug bug, UUID userId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_REOPEN_COMMENT_REQUIRED);
        }

        int nextReopenCount = bug.getReopenCount() == null ? 1 : bug.getReopenCount() + 1;
        UUID nextAssigneeId = bug.getResolvedBy() != null ? bug.getResolvedBy() : bug.getRejectedBy();
        bugMapper.reopenById(bug.getId(), nextReopenCount, nextAssigneeId);

        writeBugLog(bug.getId(), userId, Constants.BugOperation.REOPEN, "重开缺陷，说明：" + comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmBug(UUID bugId, UUID userId) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }
        if (!Constants.BugStatus.ACTIVE.equals(bug.getStatus())) {
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
        // 已关闭缺陷不允许改派处理人
        if (Constants.BugStatus.CLOSED.equals(bug.getStatus())) {
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

    @Override
    public BugStatisticsRespDTO getBugStatistics(UUID projectId) {
        List<Bug> bugs = bugMapper.findByProjectId(projectId);

        BugStatisticsRespDTO stats = new BugStatisticsRespDTO();
        stats.setTotal(bugs.size());
        stats.setByStatus(bugs.stream()
                .filter(b -> StringUtils.hasText(b.getStatus()))
                .collect(Collectors.groupingBy(Bug::getStatus, Collectors.counting())));
        stats.setBySeverity(bugs.stream()
                .filter(b -> StringUtils.hasText(b.getSeverity()))
                .collect(Collectors.groupingBy(Bug::getSeverity, Collectors.counting())));
        stats.setByPriority(bugs.stream()
                .filter(b -> StringUtils.hasText(b.getPriority()))
                .collect(Collectors.groupingBy(Bug::getPriority, Collectors.counting())));
        stats.setByAssignee(bugs.stream()
                .filter(b -> b.getAssigneeId() != null)
                .collect(Collectors.groupingBy(Bug::getAssigneeId, Collectors.counting())));
        stats.setByReporter(bugs.stream()
                .filter(b -> b.getReporterId() != null)
                .collect(Collectors.groupingBy(Bug::getReporterId, Collectors.counting())));
        return stats;
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
        TestCaseModule module = testCaseModuleMapper.selectById(moduleId);
        if (module == null || !projectId.equals(module.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_MODULE_NOT_FOUND);
        }
    }

    private BugDetailRespDTO.UserInfo buildUserInfo(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        BugDetailRespDTO.UserInfo info = new BugDetailRespDTO.UserInfo();
        info.setId(user.getId());
        info.setName(user.getUsername());
        return info;
    }

    private boolean isValidTransition(String currentStatus, String targetStatus) {
        // 四态状态机：active → resolved/rejected → closed，重开：resolved/rejected/closed → active
        Map<String, Set<String>> transitions = Map.of(
                Constants.BugStatus.ACTIVE, Set.of(Constants.BugStatus.RESOLVED, Constants.BugStatus.REJECTED),
                Constants.BugStatus.RESOLVED, Set.of(Constants.BugStatus.CLOSED, Constants.BugStatus.ACTIVE),
                Constants.BugStatus.REJECTED, Set.of(Constants.BugStatus.CLOSED, Constants.BugStatus.ACTIVE),
                Constants.BugStatus.CLOSED, Set.of(Constants.BugStatus.ACTIVE)
        );
        Set<String> allowed = transitions.getOrDefault(currentStatus, Set.of());
        return allowed.contains(targetStatus);
    }

    @Override
    public List<BugLogRespDTO> getBugLogs(UUID bugId) {
        List<BugLog> logs = bugLogMapper.findByBugId(bugId);

        return logs.stream().map(log -> {
            BugLogRespDTO dto = BugConvertMapper.INSTANCE.toLogRespDTO(log);
            SysUser operator = userMapper.selectById(log.getOperatorId());
            if (operator != null) {
                dto.setOperatorName(operator.getUsername());
            }
            return dto;
        }).collect(Collectors.toList());
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
