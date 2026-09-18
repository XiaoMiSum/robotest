package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.BugConvertMapper;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugStatisticsRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BugQueryServiceImpl implements BugQueryService {

    @Resource
    private BugMapper bugMapper;
    @Resource
    private BugLogMapper bugLogMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Override
    public PageResult<BugListRespDTO> getBugPage(UUID projectId, UUID userId, String status, String severity,
                                             String priority, String bugType, UUID assigneeId,
                                             UUID reporterId, UUID resolvedBy, UUID closedBy, String keyword,
                                             Integer pageNo, Integer pageSize) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        PageResult<Bug> page = bugMapper.findPage(new PageParam() {{
            setPageNo(pageNo);
            setPageSize(pageSize);
        }}, projectId, status, severity, priority, bugType, assigneeId, reporterId, resolvedBy, closedBy, keyword);

        // 一次性批量查询本页涉及的全部用户，避免每行最多 5 次 selectById 的 N+1
        Set<UUID> userIds = page.getList().stream()
                .flatMap(bug -> Stream.of(bug.getReporterId(), bug.getAssigneeId(),
                        bug.getResolvedBy(), bug.getRejectedBy(), bug.getClosedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.listByIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));

        List<BugListRespDTO> dtos = page.getList().stream().map(bug -> {
            BugListRespDTO dto = BugConvertMapper.INSTANCE.toListRespDTO(bug);
            dto.setReporter(BugConvertMapper.INSTANCE.toUserInfo(userMap.get(bug.getReporterId())));
            if (bug.getAssigneeId() != null) {
                dto.setAssignee(BugConvertMapper.INSTANCE.toUserInfo(userMap.get(bug.getAssigneeId())));
            }
            if (bug.getResolvedBy() != null) {
                dto.setResolvedBy(BugConvertMapper.INSTANCE.toUserInfo(userMap.get(bug.getResolvedBy())));
            }
            if (bug.getRejectedBy() != null) {
                dto.setRejectedBy(BugConvertMapper.INSTANCE.toUserInfo(userMap.get(bug.getRejectedBy())));
            }
            if (bug.getClosedBy() != null) {
                dto.setClosedBy(BugConvertMapper.INSTANCE.toUserInfo(userMap.get(bug.getClosedBy())));
            }
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(dtos, page.getTotal());
    }

    @Override
    public BugDetailRespDTO getBugDetail(UUID bugId, UUID userId) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(bug.getProjectId(), userId);

        BugDetailRespDTO dto = BugConvertMapper.INSTANCE.toDetailRespDTO(bug);

        if (bug.getModuleId() != null) {
            dto.setModuleId(bug.getModuleId());
            ProjectModule module = projectModuleMapper.selectById(bug.getModuleId());
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
    public BugStatisticsRespDTO getBugStatistics(UUID projectId, UUID userId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
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

    @Override
    public List<BugLogRespDTO> getBugLogs(UUID bugId, UUID userId) {
        // 先定位缺陷实体以取得 projectId，再做项目归属校验（getBugLogs 不能绕过授权直接读日志）
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(bug.getProjectId(), userId);
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
}