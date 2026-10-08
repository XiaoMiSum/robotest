package io.github.xiaomisum.robotest.repository.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface BugLogMapper extends BaseMapperX<BugLog> {

    default List<BugLog> findRecentLogs(UUID bugId, int limit) {
        return selectList(new LambdaQueryWrapperX<BugLog>()
                .eq(BugLog::getBugId, bugId)
                .orderByDesc(BugLog::getCreatedAt)
                .last("LIMIT " + limit));
    }

    default List<BugLog> findByBugId(UUID bugId) {
        return selectList(new LambdaQueryWrapperX<BugLog>()
                .eq(BugLog::getBugId, bugId));
    }

    /** 激活日志（缺陷分析详设 3.3 修复时长口径）：批量取多缺陷的激活记录，激活时刻在服务侧按缺陷收敛 */
    default List<BugLog> findReopensByBugIds(List<UUID> bugIds) {
        if (bugIds == null || bugIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<BugLog>()
                .in(BugLog::getBugId, bugIds)
                .eq(BugLog::getOperationType, Constants.BugOperation.REOPEN));
    }
}
