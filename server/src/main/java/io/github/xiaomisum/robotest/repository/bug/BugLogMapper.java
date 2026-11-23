package io.github.xiaomisum.robotest.repository.bug;

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
}
