package io.github.xiaomisum.robotest.service.project;

import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugStatisticsRespDTO;
import io.github.xiaomisum.robotest.model.entity.Bug;
import io.github.xiaomisum.robotest.model.entity.BugLog;
import io.github.xiaomisum.robotest.model.entity.Project;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.BugLogMapper;
import io.github.xiaomisum.robotest.repository.BugMapper;
import io.github.xiaomisum.robotest.repository.ProjectMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
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
                                             String priority, String bugType, UUID assigneeId, String keyword,
                                             Integer pageNo, Integer pageSize) {
        LambdaQueryWrapperX<Bug> wrapper = new LambdaQueryWrapperX<Bug>()
                .eq(Bug::getProjectId, projectId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(Bug::getStatus, status);
        }
        if (StringUtils.hasText(severity)) {
            wrapper.eq(Bug::getSeverity, severity);
        }
        if (StringUtils.hasText(priority)) {
            wrapper.eq(Bug::getPriority, priority);
        }
        if (StringUtils.hasText(bugType)) {
            wrapper.eq(Bug::getBugType, bugType);
        }
        if (assigneeId != null) {
            wrapper.eq(Bug::getAssigneeId, assigneeId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Bug::getTitle, keyword);
        }
        wrapper.orderByDesc(Bug::getCreatedAt);

        PageResult<Bug> page = bugMapper.selectPage(
                new PageParam() {{ setPageNo(pageNo); setPageSize(pageSize); }}, wrapper);

        List<BugListRespDTO> dtos = page.getList().stream().map(bug -> {
            BugListRespDTO dto = new BugListRespDTO();
            dto.setId(bug.getId());
            dto.setTitle(bug.getTitle());
            dto.setSeverity(bug.getSeverity());
            dto.setPriority(bug.getPriority());
            dto.setStatus(bug.getStatus());
            dto.setBugType(bug.getBugType());
            dto.setConfirmed(bug.getConfirmed());
            dto.setResolution(bug.getResolution());
            dto.setDueDate(bug.getDueDate());
            dto.setCreatedAt(bug.getCreatedAt());

            SysUser reporter = userMapper.selectById(bug.getReporterId());
            if (reporter != null) {
                BugListRespDTO.UserInfo info = new BugListRespDTO.UserInfo();
                info.setId(reporter.getId());
                info.setName(reporter.getUsername());
                dto.setReporter(info);
            }
            if (bug.getAssigneeId() != null) {
                SysUser assignee = userMapper.selectById(bug.getAssigneeId());
                if (assignee != null) {
                    BugListRespDTO.UserInfo info = new BugListRespDTO.UserInfo();
                    info.setId(assignee.getId());
                    info.setName(assignee.getUsername());
                    dto.setAssignee(info);
                }
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

        BugDetailRespDTO dto = new BugDetailRespDTO();
        dto.setId(bug.getId());
        dto.setTitle(bug.getTitle());
        dto.setSeverity(bug.getSeverity());
        dto.setPriority(bug.getPriority());
        dto.setStatus(bug.getStatus());
        dto.setBugType(bug.getBugType());
        dto.setReproSteps(bug.getReproSteps());
        dto.setKeywords(bug.getKeywords());
        dto.setDueDate(bug.getDueDate());
        dto.setConfirmed(bug.getConfirmed());
        dto.setReopenCount(bug.getReopenCount());
        dto.setLastReopenedAt(bug.getLastReopenedAt());
        dto.setResolution(bug.getResolution());
        dto.setDuplicateOfBugId(bug.getDuplicateOfBugId());
        dto.setResolvedAt(bug.getResolvedAt());
        dto.setClosedAt(bug.getClosedAt());
        dto.setCreatedAt(bug.getCreatedAt());
        dto.setUpdatedAt(bug.getUpdatedAt());

        if (bug.getModuleId() != null) {
            dto.setModuleId(bug.getModuleId());
            TestCaseModule module = testCaseModuleMapper.selectById(bug.getModuleId());
            if (module != null) {
                dto.setModuleName(module.getName());
            }
        }
        dto.setResolvedBy(buildUserInfo(bug.getResolvedBy()));
        dto.setClosedBy(buildUserInfo(bug.getClosedBy()));

        if (bug.getRelatedCaseId() != null) {
            dto.setRelatedCaseId(bug.getRelatedCaseId());
        }
        if (bug.getRelatedPlanId() != null) {
            dto.setRelatedPlanId(bug.getRelatedPlanId());
        }

        SysUser reporter = userMapper.selectById(bug.getReporterId());
        if (reporter != null) {
            BugDetailRespDTO.UserInfo info = new BugDetailRespDTO.UserInfo();
            info.setId(reporter.getId());
            info.setName(reporter.getUsername());
            dto.setReporter(info);
        }
        if (bug.getAssigneeId() != null) {
            SysUser assignee = userMapper.selectById(bug.getAssigneeId());
            if (assignee != null) {
                BugDetailRespDTO.UserInfo info = new BugDetailRespDTO.UserInfo();
                info.setId(assignee.getId());
                info.setName(assignee.getUsername());
                dto.setAssignee(info);
            }
        }

        // 返回最近 10 条操作日志
        List<BugLog> recentLogs = bugLogMapper.selectList(
                new LambdaQueryWrapperX<BugLog>()
                        .eq(BugLog::getBugId, bugId)
                        .orderByDesc(BugLog::getCreatedAt)
                        .last("LIMIT 10"));
        dto.setRecentLogs(recentLogs.stream().map(log -> {
            BugLogRespDTO logDto = new BugLogRespDTO();
            logDto.setId(log.getId());
            logDto.setOperatorId(log.getOperatorId());
            logDto.setOperationType(log.getOperationType());
            logDto.setContent(log.getContent());
            logDto.setCreatedAt(log.getCreatedAt());
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

        Bug bug = new Bug();
        bug.setProjectId(projectId);
        bug.setTitle(reqDTO.getTitle());
        bug.setSeverity(reqDTO.getSeverity());
        bug.setPriority(reqDTO.getPriority());
        bug.setBugType(reqDTO.getBugType());
        bug.setStatus(Constants.BugStatus.ACTIVE);
        bug.setReproSteps(reqDTO.getReproSteps());
        bug.setModuleId(reqDTO.getModuleId());
        bug.setKeywords(reqDTO.getKeywords());
        bug.setDueDate(reqDTO.getDueDate());
        bug.setConfirmed(false);
        bug.setReopenCount(0);
        bug.setReporterId(userId);
        bug.setAssigneeId(reqDTO.getAssigneeId());
        bug.setRelatedCaseId(reqDTO.getRelatedCaseId());
        bug.setRelatedPlanId(reqDTO.getRelatedPlanId());
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

        if (StringUtils.hasText(reqDTO.getTitle())) {
            bug.setTitle(reqDTO.getTitle());
        }
        if (StringUtils.hasText(reqDTO.getSeverity())) {
            bug.setSeverity(reqDTO.getSeverity());
        }
        if (StringUtils.hasText(reqDTO.getPriority())) {
            bug.setPriority(reqDTO.getPriority());
        }
        if (StringUtils.hasText(reqDTO.getBugType())) {
            validateBugType(reqDTO.getBugType());
            bug.setBugType(reqDTO.getBugType());
        }
        if (reqDTO.getReproSteps() != null) {
            bug.setReproSteps(reqDTO.getReproSteps());
        }
        if (reqDTO.getModuleId() != null) {
            validateModuleInProject(bug.getProjectId(), reqDTO.getModuleId());
            bug.setModuleId(reqDTO.getModuleId());
        }
        if (reqDTO.getKeywords() != null) {
            bug.setKeywords(reqDTO.getKeywords());
        }
        if (reqDTO.getDueDate() != null) {
            bug.setDueDate(reqDTO.getDueDate());
        }
        if (reqDTO.getAssigneeId() != null) {
            validateAssigneeInWorkspace(bug.getProjectId(), reqDTO.getAssigneeId());
            bug.setAssigneeId(reqDTO.getAssigneeId());
        }
        // status 不再通过 updateBug 修改，须走 changeBugStatus 状态机
        bugMapper.updateById(bug);

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
            case Constants.BugStatus.CLOSED -> closeBug(bug, userId, reqDTO.getComment());
            case Constants.BugStatus.ACTIVE -> reopenBug(bug, userId, reqDTO.getComment());
            default -> throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_INVALID_STATUS_TRANSITION);
        }
    }

    /**
     * 解决缺陷：必填合法 resolution，duplicate 需指定同项目且非自身的原始缺陷
     */
    private void resolveBug(Bug bug, UUID userId, BugStatusChangeReqDTO reqDTO) {
        String resolution = reqDTO.getResolution();
        if (!StringUtils.hasText(resolution)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_RESOLUTION_REQUIRED);
        }
        if (!VALID_RESOLUTIONS.contains(resolution)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_RESOLUTION_INVALID);
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

        bug.setStatus(Constants.BugStatus.RESOLVED);
        bug.setResolution(resolution);
        bug.setDuplicateOfBugId(duplicateOfBugId);
        bug.setResolvedBy(userId);
        bug.setResolvedAt(LocalDateTime.now());
        bugMapper.updateById(bug);

        writeBugLog(bug.getId(), userId, Constants.BugOperation.RESOLVE,
                String.format("解决缺陷，方案「%s」%s", resolution,
                        StringUtils.hasText(reqDTO.getComment()) ? "，说明：" + reqDTO.getComment() : ""));
    }

    /**
     * 关闭缺陷：必填关闭说明
     */
    private void closeBug(Bug bug, UUID userId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_CLOSE_COMMENT_REQUIRED);
        }

        bug.setStatus(Constants.BugStatus.CLOSED);
        bug.setClosedBy(userId);
        bug.setClosedAt(LocalDateTime.now());
        bugMapper.updateById(bug);

        writeBugLog(bug.getId(), userId, Constants.BugOperation.CLOSE, "关闭缺陷，说明：" + comment);
    }

    /**
     * 重开（激活）缺陷：必填说明，计数累加并清空解决/关闭信息
     */
    private void reopenBug(Bug bug, UUID userId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_REOPEN_COMMENT_REQUIRED);
        }

        bug.setStatus(Constants.BugStatus.ACTIVE);
        bug.setReopenCount(bug.getReopenCount() == null ? 1 : bug.getReopenCount() + 1);
        bug.setLastReopenedAt(LocalDateTime.now());
        bug.setResolution(null);
        bug.setDuplicateOfBugId(null);
        bug.setResolvedBy(null);
        bug.setResolvedAt(null);
        bug.setClosedBy(null);
        bug.setClosedAt(null);
        bugMapper.updateById(bug);

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

        bug.setConfirmed(true);
        bugMapper.updateById(bug);

        writeBugLog(bugId, userId, Constants.BugOperation.CONFIRM, "确认缺陷");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignBug(UUID bugId, UUID userId, UUID assigneeId) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }

        SysUser assignee = userMapper.selectById(assigneeId);
        if (assignee == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ASSIGNEE_NOT_IN_WORKSPACE);
        }
        validateAssigneeInWorkspace(bug.getProjectId(), assigneeId);

        bug.setAssigneeId(assigneeId);
        bugMapper.updateById(bug);

        writeBugLog(bugId, userId, Constants.BugOperation.ASSIGN,
                String.format("指派处理人为「%s」", assignee.getUsername()));
    }

    @Override
    public BugStatisticsRespDTO getBugStatistics(UUID projectId) {
        List<Bug> bugs = bugMapper.selectList(
                new LambdaQueryWrapperX<Bug>().eq(Bug::getProjectId, projectId));

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
        WorkspaceUser wu = workspaceUserMapper.selectOne(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, assigneeId)
                        .eq(WorkspaceUser::getWorkspaceId, project.getWorkspaceId()));
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
        // 三态状态机：active → resolved → closed，重开：resolved/closed → active
        Map<String, Set<String>> transitions = Map.of(
                Constants.BugStatus.ACTIVE, Set.of(Constants.BugStatus.RESOLVED),
                Constants.BugStatus.RESOLVED, Set.of(Constants.BugStatus.CLOSED, Constants.BugStatus.ACTIVE),
                Constants.BugStatus.CLOSED, Set.of(Constants.BugStatus.ACTIVE)
        );
        Set<String> allowed = transitions.getOrDefault(currentStatus, Set.of());
        return allowed.contains(targetStatus);
    }

    @Override
    public List<BugLogRespDTO> getBugLogs(UUID bugId) {
        List<BugLog> logs = bugLogMapper.selectList(
                new LambdaQueryWrapperX<BugLog>()
                        .eq(BugLog::getBugId, bugId)
                        .orderByAsc(BugLog::getCreatedAt));

        return logs.stream().map(log -> {
            BugLogRespDTO dto = new BugLogRespDTO();
            dto.setId(log.getId());
            dto.setOperatorId(log.getOperatorId());
            dto.setOperationType(log.getOperationType());
            dto.setContent(log.getContent());
            dto.setCreatedAt(log.getCreatedAt());

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
