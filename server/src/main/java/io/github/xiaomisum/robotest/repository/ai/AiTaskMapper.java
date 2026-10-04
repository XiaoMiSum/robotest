package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public interface AiTaskMapper extends BaseMapperX<AiTask> {

    /**
     * 任务列表（详设 3.6.3）：带 X-Active-Project 头按项目过滤，未附带则返回本人提交的任务。
     */
    default PageResult<AiTask> findPage(PageParam pageParam, UUID projectId, UUID submittedBy, String type,
            String status) {
        LambdaQueryWrapperX<AiTask> wrapper = new LambdaQueryWrapperX<AiTask>();
        if (projectId != null) {
            wrapper.eq(AiTask::getProjectId, projectId);
        } else {
            wrapper.eq(AiTask::getSubmittedBy, submittedBy);
        }
        wrapper.eqIfPresent(AiTask::getType, type)
                .eqIfPresent(AiTask::getStatus, status)
                .orderByDesc(AiTask::getCreatedAt);
        return selectPage(pageParam, wrapper);
    }

    /** pending → running 的 CAS 起跑（超时清扫器依赖 updated_at 走最新进度，故显式刷新） */
    default boolean casStart(UUID id) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, "pending")
                .set(AiTask::getStatus, "running")
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** 进度上报仅对 running 生效：已取消的任务不再回写 */
    default boolean reportProgress(UUID id, int progress, String phase) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, "running")
                .set(AiTask::getProgress, progress)
                .set(AiTask::getPhase, phase)
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** running → succeeded（条件写：与取消竞争时以先到者为准） */
    default boolean casSucceed(UUID id, Map<String, Object> result, int tokensIn, int tokensOut) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, "running")
                .set(AiTask::getStatus, "succeeded")
                .set(AiTask::getProgress, 100)
                .set(AiTask::getPhase, null)
                .set(AiTask::getResult, result)
                .set(AiTask::getTokensIn, tokensIn)
                .set(AiTask::getTokensOut, tokensOut)
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** running → failed（handler 业务失败 / 模型调用失败耗尽重试） */
    default boolean casFail(UUID id, int errorCode, String errorMsg) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, "running")
                .set(AiTask::getStatus, "failed")
                .set(AiTask::getErrorCode, errorCode)
                .set(AiTask::getErrorMsg, errorMsg)
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** 总开关关闭的批量副作用（详设 4.2）：全部 pending / running 置 failed */
    default int failActive(String errorMsg, int errorCode) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .in(AiTask::getStatus, "pending", "running")
                .set(AiTask::getStatus, "failed")
                .set(AiTask::getErrorCode, errorCode)
                .set(AiTask::getErrorMsg, errorMsg)
                .set(AiTask::getUpdatedAt, LocalDateTime.now()));
    }

    /** pending / running → cancelled（3.6.4；取消后执行线程的条件写全部失效） */
    default boolean casCancel(UUID id) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .in(AiTask::getStatus, "pending", "running")
                .set(AiTask::getStatus, "cancelled")
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** 超时清扫（详设 4.2）：按 status + updated_at 索引扫描，超时置 failed（1000018117） */
    default int failStale(LocalDateTime cutoff, int errorCode, String errorMsg) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .in(AiTask::getStatus, "pending", "running")
                .lt(AiTask::getUpdatedAt, cutoff)
                .set(AiTask::getStatus, "failed")
                .set(AiTask::getErrorCode, errorCode)
                .set(AiTask::getErrorMsg, errorMsg)
                .set(AiTask::getUpdatedAt, LocalDateTime.now()));
    }
}
