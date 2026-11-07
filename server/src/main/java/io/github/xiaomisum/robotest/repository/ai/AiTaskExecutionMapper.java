package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AiTaskExecutionMapper extends BaseMapperX<AiTaskExecution> {

    /** 计划快照落库（执行开始写入一次，轨迹 plan 事件的展示源） */
    default boolean markPlan(UUID id, Map<String, Object> plan) {
        return update(null, new LambdaUpdateWrapperX<AiTaskExecution>()
                .eq(AiTaskExecution::getId, id)
                .set(AiTaskExecution::getPlan, plan)
                .set(AiTaskExecution::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /**
     * 每跳心跳：步数与 token 汇总 + 成本 + heartbeat_at 刷新（超时清扫器扫描依据，详设 2.6.2）。
     * 条件写仅进行中执行生效——返回 false 表示执行已被外部终止（用户中止 / 清扫），
     * 执行核据此停跑，保证终止后不再产出提案。
     */
    default boolean heartbeat(UUID id, int steps, int tokensIn, int tokensOut,
            java.math.BigDecimal cost) {
        return update(null, new LambdaUpdateWrapperX<AiTaskExecution>()
                .eq(AiTaskExecution::getId, id)
                .in(AiTaskExecution::getStatus, "running", "waiting_confirmation")
                .set(AiTaskExecution::getSteps, steps)
                .set(AiTaskExecution::getTokensIn, tokensIn)
                .set(AiTaskExecution::getTokensOut, tokensOut)
                .set(AiTaskExecution::getCost, cost)
                .set(AiTaskExecution::getHeartbeatAt, LocalDateTime.now())
                .set(AiTaskExecution::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** 最新一次执行（详情 / 确认 / 再次执行的定位依据，seq 最大者） */
    default AiTaskExecution selectLatestByTaskId(UUID taskId) {
        return selectOne(new LambdaQueryWrapperX<AiTaskExecution>()
                .eq(AiTaskExecution::getTaskId, taskId)
                .orderByDesc(AiTaskExecution::getSeq)
                .last("LIMIT 1"));
    }

    /** 同任务进行中执行数（再次执行「至多一个进行中执行」门禁，总册 3.6.4） */
    default long countActiveByTaskId(UUID taskId) {
        return selectCount(new LambdaQueryWrapperX<AiTaskExecution>()
                .eq(AiTaskExecution::getTaskId, taskId)
                .in(AiTaskExecution::getStatus, "running", "waiting_confirmation"));
    }

    /** 同任务最大执行序（再次执行的 seq 自增依据） */
    default int selectMaxSeqByTaskId(UUID taskId) {
        List<AiTaskExecution> latest = selectList(new LambdaQueryWrapperX<AiTaskExecution>()
                .eq(AiTaskExecution::getTaskId, taskId)
                .orderByDesc(AiTaskExecution::getSeq)
                .last("LIMIT 1"));
        return latest.isEmpty() || latest.get(0).getSeq() == null ? 0 : latest.get(0).getSeq();
    }

    /**
     * 心跳超时扫描（总册 4.2 生命周期）：进行中且 heartbeat_at 早于截止点的执行；
     * 调用方逐条 abort（带终止码）并同步任务态。
     */
    default List<AiTaskExecution> selectStale(LocalDateTime cutoff) {
        return selectList(new LambdaQueryWrapperX<AiTaskExecution>()
                .in(AiTaskExecution::getStatus, "running", "waiting_confirmation")
                .lt(AiTaskExecution::getHeartbeatAt, cutoff));
    }

    /** 总开关关闭的批量副作用（详设 4.2）：全部进行中执行置 aborted，返回受影响行数 */
    default int abortActive(int terminateCode, String terminateMsg) {
        return update(null, new LambdaUpdateWrapperX<AiTaskExecution>()
                .in(AiTaskExecution::getStatus, "running", "waiting_confirmation")
                .set(AiTaskExecution::getStatus, "aborted")
                .set(AiTaskExecution::getTerminateCode, terminateCode)
                .set(AiTaskExecution::getTerminateMsg, terminateMsg)
                .set(AiTaskExecution::getUpdatedAt, LocalDateTime.now()));
    }

    /** running → succeeded（条件写：与终止竞争时以先到者为准） */
    default boolean succeed(UUID id, int steps, int tokensIn, int tokensOut,
            java.math.BigDecimal cost, int durationMs) {
        return update(null, new LambdaUpdateWrapperX<AiTaskExecution>()
                .eq(AiTaskExecution::getId, id)
                .eq(AiTaskExecution::getStatus, "running")
                .set(AiTaskExecution::getStatus, "succeeded")
                .set(AiTaskExecution::getSteps, steps)
                .set(AiTaskExecution::getTokensIn, tokensIn)
                .set(AiTaskExecution::getTokensOut, tokensOut)
                .set(AiTaskExecution::getCost, cost)
                .set(AiTaskExecution::getDurationMs, durationMs)
                .set(AiTaskExecution::getHeartbeatAt, LocalDateTime.now())
                .set(AiTaskExecution::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** running / waiting_confirmation → aborted（用户中止、预算耗尽、执行错误、总开关关闭，详设 4.2） */
    default boolean abort(UUID id, int terminateCode, String terminateMsg,
            int steps, int tokensIn, int tokensOut, java.math.BigDecimal cost, int durationMs) {
        return update(null, new LambdaUpdateWrapperX<AiTaskExecution>()
                .eq(AiTaskExecution::getId, id)
                .in(AiTaskExecution::getStatus, "running", "waiting_confirmation")
                .set(AiTaskExecution::getStatus, "aborted")
                .set(AiTaskExecution::getTerminateCode, terminateCode)
                .set(AiTaskExecution::getTerminateMsg, terminateMsg)
                .set(AiTaskExecution::getSteps, steps)
                .set(AiTaskExecution::getTokensIn, tokensIn)
                .set(AiTaskExecution::getTokensOut, tokensOut)
                .set(AiTaskExecution::getCost, cost)
                .set(AiTaskExecution::getDurationMs, durationMs)
                .set(AiTaskExecution::getHeartbeatAt, LocalDateTime.now())
                .set(AiTaskExecution::getUpdatedAt, LocalDateTime.now())) == 1;
    }
}
