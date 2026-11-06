package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
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

    /** 每跳心跳：步数与 token 汇总 + heartbeat_at 刷新（超时清扫器扫描依据，详设 2.6.2） */
    default boolean heartbeat(UUID id, int steps, int tokensIn, int tokensOut) {
        return update(null, new LambdaUpdateWrapperX<AiTaskExecution>()
                .eq(AiTaskExecution::getId, id)
                .set(AiTaskExecution::getSteps, steps)
                .set(AiTaskExecution::getTokensIn, tokensIn)
                .set(AiTaskExecution::getTokensOut, tokensOut)
                .set(AiTaskExecution::getHeartbeatAt, LocalDateTime.now())
                .set(AiTaskExecution::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** running → succeeded（条件写：与终止竞争时以先到者为准） */
    default boolean succeed(UUID id, int steps, int tokensIn, int tokensOut, int durationMs) {
        return update(null, new LambdaUpdateWrapperX<AiTaskExecution>()
                .eq(AiTaskExecution::getId, id)
                .eq(AiTaskExecution::getStatus, "running")
                .set(AiTaskExecution::getStatus, "succeeded")
                .set(AiTaskExecution::getSteps, steps)
                .set(AiTaskExecution::getTokensIn, tokensIn)
                .set(AiTaskExecution::getTokensOut, tokensOut)
                .set(AiTaskExecution::getDurationMs, durationMs)
                .set(AiTaskExecution::getHeartbeatAt, LocalDateTime.now())
                .set(AiTaskExecution::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    /** running / waiting_confirmation → aborted（用户中止、预算耗尽、执行错误、总开关关闭，详设 4.2） */
    default boolean abort(UUID id, int terminateCode, String terminateMsg,
            int steps, int tokensIn, int tokensOut, int durationMs) {
        return update(null, new LambdaUpdateWrapperX<AiTaskExecution>()
                .eq(AiTaskExecution::getId, id)
                .in(AiTaskExecution::getStatus, "running", "waiting_confirmation")
                .set(AiTaskExecution::getStatus, "aborted")
                .set(AiTaskExecution::getTerminateCode, terminateCode)
                .set(AiTaskExecution::getTerminateMsg, terminateMsg)
                .set(AiTaskExecution::getSteps, steps)
                .set(AiTaskExecution::getTokensIn, tokensIn)
                .set(AiTaskExecution::getTokensOut, tokensOut)
                .set(AiTaskExecution::getDurationMs, durationMs)
                .set(AiTaskExecution::getHeartbeatAt, LocalDateTime.now())
                .set(AiTaskExecution::getUpdatedAt, LocalDateTime.now())) == 1;
    }
}
