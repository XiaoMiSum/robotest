package io.github.xiaomisum.robotest.repository.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BugMapper extends BaseMapperX<Bug> {

    default long countByProjectId(UUID projectId) {
        return selectCount(Bug::getProjectId, projectId);
    }

    default PageResult<Bug> findPage(PageParam pageParam, UUID projectId, String status, String severity,
                                      String priority, String bugType, UUID assigneeId,
                                      UUID reporterId, UUID resolvedBy, UUID closedBy, String keyword) {
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
        if (reporterId != null) {
            wrapper.eq(Bug::getReporterId, reporterId);
        }
        if (resolvedBy != null) {
            wrapper.eq(Bug::getResolvedBy, resolvedBy);
        }
        if (closedBy != null) {
            wrapper.eq(Bug::getClosedBy, closedBy);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Bug::getTitle, keyword);
        }
        wrapper.orderByDesc(Bug::getCreatedAt);
        return selectPage(pageParam, wrapper);
    }

    default List<Bug> findByProjectId(UUID projectId) {
        return selectList(new LambdaQueryWrapperX<Bug>().eq(Bug::getProjectId, projectId));
    }

    default void resolveById(UUID id, UUID userId, String resolution, UUID duplicateOfBugId) {
        update(null, new LambdaUpdateWrapperX<Bug>()
                .eq(Bug::getId, id)
                .set(Bug::getStatus, Constants.BugStatus.RESOLVED)
                .set(Bug::getConfirmed, true)
                .set(Bug::getResolution, resolution)
                .set(Bug::getDuplicateOfBugId, duplicateOfBugId)
                .set(Bug::getResolvedBy, userId)
                .set(Bug::getResolvedAt, LocalDateTime.now()));
    }

    default void reopenById(UUID id, int reopenCount) {
        update(null, new LambdaUpdateWrapperX<Bug>()
                .eq(Bug::getId, id)
                .set(Bug::getStatus, Constants.BugStatus.ACTIVE)
                .set(Bug::getReopenCount, reopenCount)
                .set(Bug::getResolution, null)
                .set(Bug::getDuplicateOfBugId, null)
                .set(Bug::getResolvedBy, null)
                .set(Bug::getResolvedAt, null)
                .set(Bug::getClosedBy, null)
                .set(Bug::getClosedAt, null)
                .set(Bug::getLastReopenedAt, LocalDateTime.now()));
    }

    default long countOpenBugs(UUID projectId) {
        return selectCount(new LambdaQueryWrapperX<Bug>()
                .eq(Bug::getProjectId, projectId)
                .eq(Bug::getStatus, Constants.BugStatus.ACTIVE));
    }

    default List<Bug> findRecentBugs(UUID projectId, int limit) {
        return selectList(new LambdaQueryWrapperX<Bug>()
                .eq(Bug::getProjectId, projectId)
                .orderByDesc(Bug::getCreatedAt)
                .last("LIMIT " + limit));
    }
}
