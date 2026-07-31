package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
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

    // ========== 状态机条件更新（封装 wrapper 于 Mapper，Service 层不直接构建） ==========

    /**
     * 乐观抢占：pending → running 并写执行实例，影响行数为 0 即被其他实例消费
     */
    default int claimForExecution(UUID id, String executorInstance) {
        return update(null, new LambdaUpdateWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getId, id)
                .eq(AiAnalysisTask::getStatus, Constants.AiTaskStatus.PENDING)
                .set(AiAnalysisTask::getStatus, Constants.AiTaskStatus.RUNNING)
                .set(AiAnalysisTask::getExecutorInstance, executorInstance)
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * running → success 并写结果（协作式取消：被置 cancelled 时不覆盖终态）
     */
    default int markSuccessIfRunning(UUID id, String resultJson) {
        return update(null, new LambdaUpdateWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getId, id)
                .eq(AiAnalysisTask::getStatus, Constants.AiTaskStatus.RUNNING)
                .set(AiAnalysisTask::getStatus, Constants.AiTaskStatus.SUCCESS)
                .set(AiAnalysisTask::getProgress, 100)
                .set(resultJson != null, AiAnalysisTask::getResult, resultJson)
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now()));
    }

    default int markFailedIfRunning(UUID id, String errorMessage) {
        return update(null, new LambdaUpdateWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getId, id)
                .eq(AiAnalysisTask::getStatus, Constants.AiTaskStatus.RUNNING)
                .set(AiAnalysisTask::getStatus, Constants.AiTaskStatus.FAILED)
                .set(AiAnalysisTask::getErrorMessage, errorMessage)
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now()));
    }

    default int markCancelledById(UUID id) {
        return update(null, new LambdaUpdateWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getId, id)
                .in(AiAnalysisTask::getStatus, Constants.AiTaskStatus.PENDING, Constants.AiTaskStatus.RUNNING)
                .set(AiAnalysisTask::getStatus, Constants.AiTaskStatus.CANCELLED)
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now()));
    }

    default int cancelByTypeAndTarget(String type, UUID targetId) {
        return update(null, new LambdaUpdateWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getType, type)
                .eq(AiAnalysisTask::getTargetId, targetId)
                .in(AiAnalysisTask::getStatus, Constants.AiTaskStatus.PENDING, Constants.AiTaskStatus.RUNNING)
                .set(AiAnalysisTask::getStatus, Constants.AiTaskStatus.CANCELLED)
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now()));
    }

    default int cancelAllInProgress() {
        return update(null, new LambdaUpdateWrapperX<AiAnalysisTask>()
                .in(AiAnalysisTask::getStatus, Constants.AiTaskStatus.PENDING, Constants.AiTaskStatus.RUNNING)
                .set(AiAnalysisTask::getStatus, Constants.AiTaskStatus.CANCELLED)
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now()));
    }

    default int resetToPending(UUID id) {
        return update(null, new LambdaUpdateWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getId, id)
                .set(AiAnalysisTask::getStatus, Constants.AiTaskStatus.PENDING)
                .set(AiAnalysisTask::getProgress, 0)
                .set(AiAnalysisTask::getErrorMessage, null)
                .set(AiAnalysisTask::getExecutorInstance, null)
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 孤儿回收：running 且超过 updated_at 阈值未推进的任务置 failed
     */
    default int recoverOrphans(LocalDateTime before, String errorMessage) {
        return update(null, new LambdaUpdateWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getStatus, Constants.AiTaskStatus.RUNNING)
                .lt(AiAnalysisTask::getUpdatedAt, before)
                .set(AiAnalysisTask::getStatus, Constants.AiTaskStatus.FAILED)
                .set(AiAnalysisTask::getErrorMessage, errorMessage)
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now()));
    }
}
