package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class BugStatusChangeServiceImpl implements BugStatusChangeService {

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
    private BugWorkflow bugWorkflow;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeBugStatus(Bug bug, UUID userId, BugStatusChangeReqDTO reqDTO) {
        BugStatus from = BugStatus.fromCode(bug.getStatus());
        BugStatus target = BugStatus.fromCode(reqDTO.getStatus());
        bugWorkflow.assertTransition(from, target);

        switch (target) {
            case RESOLVED -> resolveBug(bug, userId, reqDTO);
            case REJECTED -> rejectBug(bug, userId, reqDTO.getComment());
            case CLOSED -> closeBug(bug, userId, reqDTO.getComment());
            case ACTIVE -> reopenBug(bug, userId, reqDTO.getComment());
        }

        if (target == BugStatus.CLOSED) {
            eventPublisher.publishEvent(new BugChangedEvent(bug.getId(), BugChangeOp.CLOSED));
        }
    }

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

        bugMapper.resolveById(bug.getId(), userId, resolution, duplicateOfBugId, bug.getReporterId());
        writeBugLog(bug.getId(), userId, Constants.BugOperation.RESOLVE,
                String.format("解决缺陷，方案「%s」%s", resolution,
                        StringUtils.hasText(reqDTO.getComment()) ? "，说明：" + reqDTO.getComment() : ""));
    }

    private void rejectBug(Bug bug, UUID userId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_REJECT_COMMENT_REQUIRED);
        }

        Bug update = new Bug();
        update.setId(bug.getId());
        update.setStatus(BugStatus.REJECTED.getCode());
        update.setAssigneeId(bug.getReporterId());
        update.setRejectedBy(userId);
        bugMapper.updateById(update);
        writeBugLog(bug.getId(), userId, Constants.BugOperation.REJECT, "拒绝缺陷，说明：" + comment);
    }

    private void closeBug(Bug bug, UUID userId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_CLOSE_COMMENT_REQUIRED);
        }

        Bug update = new Bug();
        update.setId(bug.getId());
        update.setStatus(BugStatus.CLOSED.getCode());
        update.setClosedBy(userId);
        update.setClosedAt(LocalDateTime.now());
        bugMapper.updateById(update);
        writeBugLog(bug.getId(), userId, Constants.BugOperation.CLOSE, "关闭缺陷，说明：" + comment);
    }

    private void reopenBug(Bug bug, UUID userId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_REOPEN_COMMENT_REQUIRED);
        }

        int nextReopenCount = bug.getReopenCount() == null ? 1 : bug.getReopenCount() + 1;
        UUID nextAssigneeId = bug.getResolvedBy() != null ? bug.getResolvedBy() : bug.getRejectedBy();
        bugMapper.reopenById(bug.getId(), nextReopenCount, nextAssigneeId);
        writeBugLog(bug.getId(), userId, Constants.BugOperation.REOPEN, "重开缺陷，说明：" + comment);
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
