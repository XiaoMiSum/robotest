package io.github.xiaomisum.robotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.entity.Bug;
import io.github.xiaomisum.robotest.model.entity.BugLog;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.repository.BugLogMapper;
import io.github.xiaomisum.robotest.repository.BugMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.service.BugService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BugServiceImpl implements BugService {

    @Resource
    private BugMapper bugMapper;
    @Resource
    private BugLogMapper bugLogMapper;
    @Resource
    private SysUserMapper userMapper;

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
            dto.setId(bug.getId().toString());
            dto.setTitle(bug.getTitle());
            dto.setSeverity(bug.getSeverity());
            dto.setPriority(bug.getPriority());
            dto.setStatus(bug.getStatus());
            dto.setCreatedAt(bug.getCreatedAt());

            SysUser reporter = userMapper.selectById(bug.getReporterId());
            if (reporter != null) {
                BugListRespDTO.UserInfo info = new BugListRespDTO.UserInfo();
                info.setId(reporter.getId().toString());
                info.setName(reporter.getUsername());
                dto.setReporter(info);
            }
            if (StringUtils.hasText(bug.getAssigneeId())) {
                SysUser assignee = userMapper.selectById(bug.getAssigneeId());
                if (assignee != null) {
                    BugListRespDTO.UserInfo info = new BugListRespDTO.UserInfo();
                    info.setId(assignee.getId().toString());
                    info.setName(assignee.getUsername());
                    dto.setAssignee(info);
                }
            }
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(dtos, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createBug(String projectId, String userId, BugCreateReqDTO reqDTO) {
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(projectId);
        bug.setTitle(reqDTO.getTitle());
        bug.setSeverity(reqDTO.getSeverity());
        bug.setPriority(reqDTO.getPriority());
        bug.setStatus("new");
        bug.setDescription(reqDTO.getDescription());
        bug.setReporterId(userId);
        bug.setAssigneeId(reqDTO.getAssigneeId());
        bug.setRelatedCaseId(reqDTO.getRelatedCaseId());
        bug.setRelatedPlanId(reqDTO.getRelatedPlanId());
        bugMapper.insert(bug);

        writeBugLog(bug.getId().toString(), userId, "create", "创建缺陷");

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
            bug.setAssigneeId(reqDTO.getAssigneeId());
        }
        if (StringUtils.hasText(reqDTO.getStatus())) {
            bug.setStatus(reqDTO.getStatus());
        }
        bugMapper.updateById(bug);

        writeBugLog(bugId, userId, "update", "更新缺陷");
    }

    @Override
    public List<BugLogRespDTO> getBugLogs(String bugId) {
        List<BugLog> logs = bugLogMapper.selectList(
                new LambdaQueryWrapper<BugLog>()
                        .eq(BugLog::getBugId, bugId)
                        .orderByAsc(BugLog::getCreatedAt));

        return logs.stream().map(log -> {
            BugLogRespDTO dto = new BugLogRespDTO();
            dto.setId(log.getId().toString());
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

    private void writeBugLog(String bugId, String userId, String operationType, String content) {
        BugLog log = new BugLog();
        log.setId(UUID.randomUUID());
        log.setBugId(bugId);
        log.setOperatorId(userId);
        log.setOperationType(operationType);
        log.setContent(content);
        bugLogMapper.insert(log);
    }
}
