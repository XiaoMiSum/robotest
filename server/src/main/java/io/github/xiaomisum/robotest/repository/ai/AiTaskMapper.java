package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 任务主表数据访问（总册 2.6.1 新状态机：running → proposals_ready → closed / aborted）；
 * 执行态与心跳在 {@link AiTaskExecutionMapper}，本表只做任务态流转与汇总回写。
 */
public interface AiTaskMapper extends BaseMapperX<AiTask> {

    /** 生命周期统一锁序的第一把锁：task → execution。 */
    default AiTask selectByIdForUpdate(UUID id) {
        return selectOne(new LambdaQueryWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .last("FOR UPDATE"));
    }

    /**
     * 同项目同类型的进行中任务（生成链 3.6.2 同输入去重比对用）：
     * 未终态 = running / proposals_ready（已 aborted / closed 不占位）。
     */
    default List<AiTask> listInProgressByType(UUID projectId, String type) {
        return selectList(new LambdaQueryWrapperX<AiTask>()
                .eq(AiTask::getProjectId, projectId)
                .eq(AiTask::getType, type)
                .in(AiTask::getStatus, List.of(Constants.AiTaskFlowStatus.RUNNING,
                        Constants.AiTaskFlowStatus.PROPOSALS_READY)));
    }

    /**
     * 任务列表（详设 3.6.3）：始终只返回当前用户提交的任务，项目上下文为附加过滤条件。
     */
    default PageResult<AiTask> findPage(PageParam pageParam, UUID projectId, UUID submittedBy, String type,
            String status) {
        LambdaQueryWrapperX<AiTask> wrapper = new LambdaQueryWrapperX<AiTask>()
                .eq(AiTask::getSubmittedBy, submittedBy)
                .eqIfPresent(AiTask::getProjectId, projectId)
                .eqIfPresent(AiTask::getType, type)
                .eqIfPresent(AiTask::getStatus, status)
                .orderByDesc(AiTask::getCreatedAt);
        return selectPage(pageParam, wrapper);
    }

    /**
     * 进行中任务计数（详设 3.6.3 顶栏指示器）：当前用户提交且 status = running（含执行挂起），过滤口径与列表一致。
     */
    default long countActive(UUID projectId, UUID submittedBy) {
        LambdaQueryWrapperX<AiTask> wrapper = new LambdaQueryWrapperX<AiTask>()
                .eq(AiTask::getStatus, Constants.AiTaskFlowStatus.RUNNING)
                .eq(AiTask::getSubmittedBy, submittedBy)
                .eqIfPresent(AiTask::getProjectId, projectId);
        return selectCount(wrapper);
    }

    /** 任务标题回填（总册 2.6.1）：执行产出提案后写回首个根提案标题，部分更新（C11） */
    default boolean markTitle(UUID id, String title) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .set(AiTask::getTitle, title)
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /**
     * 任务态流转（总册 3.6：running → proposals_ready → closed / aborted）：
     * 条件写——仅 running / proposals_ready 可流转，与终止竞争以先到者为准。
     */
    default boolean markFlowStatus(UUID id, String status) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .in(AiTask::getStatus,
                        Constants.AiTaskFlowStatus.RUNNING, Constants.AiTaskFlowStatus.PROPOSALS_READY)
                .set(AiTask::getStatus, status)
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** 任务终止（用户中止 / 预算耗尽 / 执行错误 / 总开关关闭 / 心跳超时）：带终止原因，条件写同 markFlowStatus */
    default boolean markAborted(UUID id, int terminateCode, String terminateMsg) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .in(AiTask::getStatus,
                        Constants.AiTaskFlowStatus.RUNNING, Constants.AiTaskFlowStatus.PROPOSALS_READY)
                .set(AiTask::getStatus, Constants.AiTaskFlowStatus.ABORTED)
                .set(AiTask::getTerminateCode, terminateCode)
                .set(AiTask::getTerminateMsg, terminateMsg)
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /**
     * 再次执行（总册 3.6.4）：aborted / proposals_ready → running，并清空上一轮终止原因；
     * 同任务至多一个进行中执行由执行表唯一性在服务层保证。
     */
    default boolean markRerun(UUID id) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .in(AiTask::getStatus,
                        Constants.AiTaskFlowStatus.ABORTED, Constants.AiTaskFlowStatus.PROPOSALS_READY)
                .set(AiTask::getStatus, Constants.AiTaskFlowStatus.RUNNING)
                .set(AiTask::getTerminateCode, null)
                .set(AiTask::getTerminateMsg, null)
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** 文档级元数据落库（总册 2.6.1 result_meta，执行成功时由执行核写入一次） */
    default boolean markResultMeta(UUID id, Map<String, Object> resultMeta) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                // wrapper.set 不套用 typeHandler，Map 直传会被 pgjdbc 当 hstore 解析
                .set(AiTask::getResultMeta, resultMeta == null ? null : JsonUtils.toJsonString(resultMeta))
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** 任务级 token 汇总（总册 2.6.1：跨全部执行累加，执行终态时回写） */
    default boolean accumulateTokens(UUID id, int tokensIn, int tokensOut) {
        return update(null, new LambdaUpdateWrapperX<AiTask>()
                .eq(AiTask::getId, id)
                .setSql("tokens_in = tokens_in + " + tokensIn)
                .setSql("tokens_out = tokens_out + " + tokensOut)
                .set(AiTask::getUpdatedAt, LocalDateTime.now())) == 1;
    }
}
