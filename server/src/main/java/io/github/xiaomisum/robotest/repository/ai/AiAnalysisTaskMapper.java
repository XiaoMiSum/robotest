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
     * 最近一次成功任务（评审摘要查询：仅 success 记录参与展示，详细设计 3.2.2）
     */
    default AiAnalysisTask findLatestSuccessByTypeAndTarget(String type, UUID targetId) {
        return selectOne(new LambdaQueryWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getType, type)
                .eq(AiAnalysisTask::getTargetId, targetId)
                .eq(AiAnalysisTask::getStatus, Constants.AiTaskStatus.SUCCESS)
                .orderByDesc(AiAnalysisTask::getCreatedAt)
                .last("LIMIT 1"));
    }

    /**
     * 覆盖语义：逻辑删除同 type+target 除当前记录外的全部 success 记录（评审摘要重复生成，详细设计 3.2.1）
     */
    default void deleteSuccessExcept(String type, UUID targetId, UUID exceptId) {
        delete(new LambdaQueryWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getType, type)
                .eq(AiAnalysisTask::getTargetId, targetId)
                .eq(AiAnalysisTask::getStatus, Constants.AiTaskStatus.SUCCESS)
                .ne(AiAnalysisTask::getId, exceptId));
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

    /**
     * 是否存在进行中的向量重建任务（增量写入/补偿的互斥判定，详细设计 4.1）
     */
    default boolean hasInProgressRebuild() {
        return selectCount(new LambdaQueryWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getType, Constants.AiTaskType.EMBEDDING_REBUILD)
                .in(AiAnalysisTask::getStatus, Constants.AiTaskStatus.PENDING, Constants.AiTaskStatus.RUNNING)) > 0;
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

    /**
     * 分批任务进度心跳：running → 更新进度并累计写入结果快照（review_check 部分结果可见性，基础设施 3.5.2）。
     * 影响行数为 0 表示任务已被取消或置失败（协作式取消），执行器应立即中止返回部分结果。
     */
    default int updateProgressIfRunning(UUID id, int progress, String resultJson) {
        return update(null, new LambdaUpdateWrapperX<AiAnalysisTask>()
                .eq(AiAnalysisTask::getId, id)
                .eq(AiAnalysisTask::getStatus, Constants.AiTaskStatus.RUNNING)
                .set(AiAnalysisTask::getProgress, progress)
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
