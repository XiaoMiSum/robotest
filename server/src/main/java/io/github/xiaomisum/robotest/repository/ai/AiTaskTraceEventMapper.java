package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTraceEvent;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface AiTaskTraceEventMapper extends BaseMapperX<AiTaskTraceEvent> {

    /** 游标增量拉取（总册 3.6.4：只增不改，seq 严格递增） */
    default List<AiTaskTraceEvent> selectAfterSeq(UUID executionId, int afterSeq, int pageSize) {
        return selectList(new LambdaQueryWrapperX<AiTaskTraceEvent>()
                .eq(AiTaskTraceEvent::getExecutionId, executionId)
                .gt(AiTaskTraceEvent::getSeq, afterSeq)
                .orderByAsc(AiTaskTraceEvent::getSeq)
                .last("LIMIT " + pageSize));
    }
}
