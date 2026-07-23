package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugStatisticsRespDTO;
import io.github.xiaomisum.robotest.model.entity.Bug;
import io.github.xiaomisum.robotest.model.entity.BugLog;
import io.github.xiaomisum.robotest.model.entity.Project;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.BugLogMapper;
import io.github.xiaomisum.robotest.repository.BugMapper;
import io.github.xiaomisum.robotest.repository.ProjectMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.project.BugService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BugServiceImpl implements BugService {

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

    @Override
    public PageResult<BugListRespDTO> getBugPage(String projectId, String status, String severity,
                                             String priority, String assigneeId, String keyword,
                                             Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<Bug> wrapper = new LambdaQueryWrapper<Bug>()
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
        if (StringUtils.hasText(assigneeId)) {
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
            dto.setCreatedAt(bug.getCreatedAt());

            SysUser reporter = userMapper.selectById(bug.getReporterId());
            if (reporter != null) {
                BugListRespDTO.UserInfo info = new BugListRespDTO.UserInfo();
                info.setId(reporter.getId());
                info.setName(reporter.getUsername());
                dto.setReporter(info);
            }
            if (StringUtils.hasText(bug.getAssigneeId())) {
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
    public BugDetailRespDTO getBugDetail(String bugId) {
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
        dto.setDescription(bug.getDescription());
        dto.setCreatedAt(bug.getCreatedAt());
        dto.setUpdatedAt(bug.getUpdatedAt());

        if (StringUtils.hasText(bug.getRelatedCaseId())) {
            dto.setRelatedCaseId(UUID.fromString(bug.getRelatedCaseId()));
        }
        if (StringUtils.hasText(bug.getRelatedPlanId())) {
            dto.setRelatedPlanId(UUID.fromString(bug.getRelatedPlanId()));
        }

        SysUser reporter = userMapper.selectById(bug.getReporterId());
        if (reporter != null) {
            BugDetailRespDTO.UserInfo info = new BugDetailRespDTO.UserInfo();
            info.setId(reporter.getId());
            info.setName(reporter.getUsername());
            dto.setReporter(info);
        }
        if (StringUtils.hasText(bug.getAssigneeId())) {
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
                new LambdaQueryWrapper<BugLog>()
                        .eq(BugLog::getBugId, bugId)
                        .orderByDesc(BugLog::getCreatedAt)
                        .last("LIMIT 10"));
        dto.setRecentLogs(recentLogs.stream().map(log -> {
            BugLogRespDTO logDto = new BugLogRespDTO();
            logDto.setId(log.getId());
            logDto.setOperatorId(UUID.fromString(log.getOperatorId()));
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
    public String createBug(String projectId, String userId, BugCreateReqDTO reqDTO) {
        // 校验指派人是当前工作空间成员
        if (reqDTO.getAssigneeId() != null) {
            Project project = projectMapper.selectById(projectId);
            if (project != null) {
                WorkspaceUser wu = workspaceUserMapper.selectOne(
                        new LambdaQueryWrapper<WorkspaceUser>()
                                .eq(WorkspaceUser::getUserId, reqDTO.getAssigneeId().toString())
                                .eq(WorkspaceUser::getWorkspaceId, project.getWorkspaceId()));
                if (wu == null) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ASSIGNEE_NOT_IN_WORKSPACE);
                }
            }
        }

        Bug bug = new Bug();
        bug.setProjectId(projectId);
        bug.setTitle(reqDTO.getTitle());
        bug.setSeverity(reqDTO.getSeverity());
        bug.setPriority(reqDTO.getPriority());
        bug.setStatus(Constants.Status.NEW);
        bug.setDescription(reqDTO.getDescription());
        bug.setReporterId(userId);
        bug.setAssigneeId(reqDTO.getAssigneeId() != null ? reqDTO.getAssigneeId().toString() : null);
        bug.setRelatedCaseId(reqDTO.getRelatedCaseId() != null ? reqDTO.getRelatedCaseId().toString() : null);
        bug.setRelatedPlanId(reqDTO.getRelatedPlanId() != null ? reqDTO.getRelatedPlanId().toString() : null);
        bugMapper.insert(bug);

        writeBugLog(bug.getId().toString(), userId, Constants.BugOperation.CREATE, "鍒涘缓缂洪櫡");

        return bug.getId().toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBug(String bugId, String userId, BugUpdateReqDTO reqDTO) {
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
        if (reqDTO.getDescription() != null) {
            bug.setDescription(reqDTO.getDescription());
        }
        if (reqDTO.getAssigneeId() != null) {
            bug.setAssigneeId(reqDTO.getAssigneeId().toString());
        }
        // status 不再通过 updateBug 修改，须走 changeBugStatus 状态机
        bugMapper.updateById(bug);

        writeBugLog(bugId, userId, Constants.BugOperation.UPDATE, "鏇存柊缂洪櫡");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeBugStatus(String bugId, String userId, String targetStatus, String comment) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }

        String currentStatus = bug.getStatus();

        // 重开缺陷：CLOSED/VERIFIED → FIXING，必须填写说明
        if (Constants.BugStatus.FIXING.equals(targetStatus)
                && (Constants.BugStatus.CLOSED.equals(currentStatus)
                    || Constants.BugStatus.VERIFIED.equals(currentStatus))) {
            if (!StringUtils.hasText(comment)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_REOPEN_COMMENT_REQUIRED);
            }
        }

        // 关闭缺陷：必须填写关闭说明
        if (Constants.BugStatus.CLOSED.equals(targetStatus)
                && !StringUtils.hasText(comment)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_CLOSE_COMMENT_REQUIRED);
        }

        // 校验状态流转合法性
        if (!isValidTransition(currentStatus, targetStatus)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_INVALID_STATUS_TRANSITION);
        }

        bug.setStatus(targetStatus);
        bugMapper.updateById(bug);

        String operationType = Constants.BugStatus.FIXING.equals(targetStatus)
                && (Constants.BugStatus.CLOSED.equals(currentStatus)
                    || Constants.BugStatus.VERIFIED.equals(currentStatus))
                ? Constants.BugOperation.REOPEN
                : Constants.BugOperation.STATUS_CHANGE;
        String logContent = String.format("状态由「%s」变更为「%s」%s",
                currentStatus, targetStatus,
                StringUtils.hasText(comment) ? "，说明：" + comment : "");
        writeBugLog(bugId, userId, operationType, logContent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignBug(String bugId, String userId, String assigneeId) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }

        SysUser assignee = userMapper.selectById(assigneeId);
        if (assignee == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ASSIGNEE_NOT_IN_WORKSPACE);
        }

        bug.setAssigneeId(assigneeId);
        bugMapper.updateById(bug);

        writeBugLog(bugId, userId, Constants.BugOperation.ASSIGN,
                String.format("指派处理人为「%s」", assignee.getUsername()));
    }

    @Override
    public BugStatisticsRespDTO getBugStatistics(String projectId) {
        List<Bug> bugs = bugMapper.selectList(
                new LambdaQueryWrapper<Bug>().eq(Bug::getProjectId, projectId));

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
                .filter(b -> StringUtils.hasText(b.getAssigneeId()))
                .collect(Collectors.groupingBy(Bug::getAssigneeId, Collectors.counting())));
        stats.setByReporter(bugs.stream()
                .filter(b -> StringUtils.hasText(b.getReporterId()))
                .collect(Collectors.groupingBy(Bug::getReporterId, Collectors.counting())));
        return stats;
    }

    private boolean isValidTransition(String currentStatus, String targetStatus) {
        // 合法状态流转定义：currentStatus → targetStatus
        Map<String, Set<String>> transitions = Map.of(
                Constants.BugStatus.NEW, Set.of(Constants.BugStatus.ASSIGNED),
                Constants.BugStatus.ASSIGNED, Set.of(Constants.BugStatus.FIXING),
                Constants.BugStatus.FIXING, Set.of(Constants.BugStatus.FIXED),
                Constants.BugStatus.FIXED, Set.of(Constants.BugStatus.VERIFIED),
                Constants.BugStatus.VERIFIED, Set.of(Constants.BugStatus.CLOSED, Constants.BugStatus.FIXING),
                Constants.BugStatus.CLOSED, Set.of(Constants.BugStatus.FIXING)
        );
        Set<String> allowed = transitions.getOrDefault(currentStatus, Set.of());
        return allowed.contains(targetStatus);
    }

    @Override
    public List<BugLogRespDTO> getBugLogs(String bugId) {
        List<BugLog> logs = bugLogMapper.selectList(
                new LambdaQueryWrapper<BugLog>()
                        .eq(BugLog::getBugId, bugId)
                        .orderByAsc(BugLog::getCreatedAt));

        return logs.stream().map(log -> {
            BugLogRespDTO dto = new BugLogRespDTO();
            dto.setId(log.getId());
            dto.setOperatorId(UUID.fromString(log.getOperatorId()));
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

    private void writeBugLog(String bugId, String userId, String operationType, String content) {
        BugLog log = new BugLog();
        log.setBugId(bugId);
        log.setOperatorId(userId);
        log.setOperationType(operationType);
        log.setContent(content);
        bugLogMapper.insert(log);
    }
}
