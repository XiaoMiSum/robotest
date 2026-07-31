package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface AiAnalysisTaskMapper extends BaseMapperX<AiAnalysisTask> {

    default AiAnalysisTask findLatestByType(String type) {
        return selectOne(new LambdaQueryWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getType, type)
                .orderByDesc(AiAnalysisTask::getCreatedAt)
                .last("LIMIT 1"));
    }

    default AiAnalysisTask findLatestByTypeAndTarget(String type, UUID targetId) {
        return selectOne(new LambdaQueryWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getType, type)
                .eq(AiAnalysisTask::getTargetId, targetId)
                .orderByDesc(AiAnalysisTask::getCreatedAt)
                .last("LIMIT 1"));
    }

    /**
     * 创建前防并发双建校验：行级锁定进行中的同类任务（SELECT … FOR UPDATE）
     */
    default List<AiAnalysisTask> lockInProgress(String type, UUID targetId, UUID projectId) {
        return selectList(new LambdaQueryWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getType, type)
                .eqIfPresent(AiAnalysisTask::getTargetId, targetId)
                .eqIfPresent(AiAnalysisTask::getProjectId, projectId)
                .in(AiAnalysisTask::getStatus, Constants.AiTaskStatus.PENDING, Constants.AiTaskStatus.RUNNING)
                .last("FOR UPDATE"));
    }

    default List<AiAnalysisTask> findPendingTasks(int limit) {
        return selectList(new LambdaQueryWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getStatus, Constants.AiTaskStatus.PENDING)
                .orderByAsc(AiAnalysisTask::getCreatedAt)
                .last("LIMIT " + limit));
    }

    default List<AiAnalysisTask> findInProgressByTypeAndTarget(String type, UUID targetId) {
        return selectList(new LambdaQueryWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getType, type)
                .eq(AiAnalysisTask::getTargetId, targetId)
                .in(AiAnalysisTask::getStatus, Constants.AiTaskStatus.PENDING, Constants.AiTaskStatus.RUNNING));
    }

    default List<AiAnalysisTask> findAllInProgress() {
        return selectList(new LambdaQueryWrapperX<AiAnalysisTask>()
                .in(AiAnalysisTask::getStatus, Constants.AiTaskStatus.PENDING, Constants.AiTaskStatus.RUNNING));
    }
}
