package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskProposal;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskProposalMapper;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 执行终态的唯一事务边界。所有路径按 task → latest execution 加锁，只有本轮执行的
 * 条件写赢家可以更新任务投影、累计用量、写提案和终态轨迹。
 */
@Component
public class ExecutionLifecycle {

    private static final int TITLE_MAX = 200;
    private static final int MSG_MAX = 500;

    @Resource
    private AiTaskMapper taskMapper;
    @Resource
    private AiTaskExecutionMapper executionMapper;
    @Resource
    private AiTaskProposalMapper proposalMapper;
    @Resource
    private TraceRecorder traceRecorder;

    public record Usage(int steps, int tokensIn, int tokensOut, BigDecimal cost, int durationMs) {
        public Usage {
            cost = cost == null ? BigDecimal.ZERO : cost;
        }
    }

    /** 外部调用前的短事务门禁：仅最新且仍活动的执行可以继续。 */
    @Transactional(readOnly = true)
    public boolean isCurrentActive(UUID taskId, UUID executionId) {
        AiTaskExecution latest = executionMapper.selectLatestByTaskId(taskId);
        return latest != null && Objects.equals(latest.getId(), executionId)
                && isActive(latest.getStatus());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean succeed(UUID taskId, UUID executionId, List<ProposalDraft> drafts,
            Map<String, Object> resultMeta, Usage usage) {
        LockedRun run = lockCurrent(taskId, executionId);
        if (run == null || !isActive(run.execution().getStatus())) {
            return false;
        }
        if (!executionMapper.succeed(executionId, usage.steps(), usage.tokensIn(), usage.tokensOut(),
                usage.cost(), usage.durationMs())) {
            return false;
        }
        if (resultMeta != null && !taskMapper.markResultMeta(taskId, resultMeta)) {
            throw new IllegalStateException("任务结果元数据写入失败: " + taskId);
        }
        if (!taskMapper.accumulateTokens(taskId, usage.tokensIn(), usage.tokensOut())) {
            throw new IllegalStateException("任务用量累计失败: " + taskId);
        }
        List<ProposalDraft> safeDrafts = drafts == null ? List.of() : drafts;
        for (ProposalDraft draft : safeDrafts) {
            AiTaskProposal proposal = new AiTaskProposal();
            proposal.setTaskId(taskId);
            proposal.setExecutionId(executionId);
            proposal.setProposalKey(draft.proposalKey());
            proposal.setKind(draft.kind());
            proposal.setParentKey(draft.parentKey());
            proposal.setTitle(fit(ModelOutputSupport.nvl(draft.title()), TITLE_MAX));
            proposal.setContent(draft.content());
            proposal.setConfirmStatus(Constants.AiProposalConfirmStatus.PENDING);
            proposalMapper.insert(proposal);
            traceRecorder.record(executionId, Constants.AiTraceEventType.PROPOSAL_EMITTED,
                    "提案产出: " + draft.title(),
                    Map.of("proposalKey", ModelOutputSupport.nvl(draft.proposalKey()),
                            "kind", ModelOutputSupport.nvl(draft.kind())));
        }
        String taskStatus = safeDrafts.isEmpty()
                ? Constants.AiTaskFlowStatus.CLOSED : Constants.AiTaskFlowStatus.PROPOSALS_READY;
        traceRecorder.record(executionId, Constants.AiTraceEventType.STATUS_CHANGE,
                safeDrafts.isEmpty() ? "执行完成，无待采纳提案" : "执行完成，产出 " + safeDrafts.size() + " 项提案",
                Map.of("execution", Constants.AiTaskExecutionStatus.SUCCEEDED));
        if (!taskMapper.markFlowStatus(taskId, taskStatus)) {
            throw new IllegalStateException("任务终态写入失败: " + taskId);
        }
        traceRecorder.record(executionId, Constants.AiTraceEventType.STATUS_CHANGE,
                safeDrafts.isEmpty() ? "任务关闭" : "任务待采纳", Map.of("task", taskStatus));
        return true;
    }

    /** Runner 失败路径：传入本地最新计量；事务失败会回滚，调用方不得补写任务投影。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean terminate(UUID taskId, UUID executionId, int code, String message, Usage usage) {
        return terminateLocked(taskId, executionId, code, message, usage, null);
    }

    /** 外部中止使用锁内最新计量快照，避免用查询阶段的旧值覆盖心跳。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean terminate(UUID taskId, UUID executionId, int code, String message) {
        return terminateLocked(taskId, executionId, code, message, null, null);
    }

    /** 清扫器在锁内复查 heartbeat cutoff，扫描后已刷新的执行不会被终止。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean terminateIfHeartbeatStale(UUID taskId, UUID executionId, LocalDateTime cutoff,
            int code, String message) {
        return terminateLocked(taskId, executionId, code, message, null, cutoff);
    }

    /** 总开关关闭复用同一终态机制，逐执行保留条件写、用量与终态轨迹语义。 */
    @Transactional(rollbackFor = Exception.class)
    public int terminateAllActive(int code, String message) {
        int terminated = 0;
        for (AiTaskExecution execution : executionMapper.selectList(
                new LambdaQueryWrapperX<AiTaskExecution>()
                        .in(AiTaskExecution::getStatus,
                                Constants.AiTaskExecutionStatus.RUNNING,
                                Constants.AiTaskExecutionStatus.WAITING_CONFIRMATION))) {
            if (terminateLocked(execution.getTaskId(), execution.getId(), code, message, null, null)) {
                terminated++;
            }
        }
        return terminated;
    }

    private boolean terminateLocked(UUID taskId, UUID executionId, int code, String message,
            Usage suppliedUsage, LocalDateTime heartbeatCutoff) {
        LockedRun run = lockCurrent(taskId, executionId);
        if (run == null || !isActive(run.execution().getStatus())) {
            return false;
        }
        AiTaskExecution execution = run.execution();
        if (heartbeatCutoff != null && (execution.getHeartbeatAt() == null
                || !execution.getHeartbeatAt().isBefore(heartbeatCutoff))) {
            return false;
        }
        Usage usage = suppliedUsage == null ? usageOf(execution) : suppliedUsage;
        String fittedMessage = fit(ModelOutputSupport.nvl(message), MSG_MAX);
        if (!executionMapper.abort(executionId, code, fittedMessage, usage.steps(), usage.tokensIn(),
                usage.tokensOut(), usage.cost(), usage.durationMs())) {
            return false;
        }
        if (!taskMapper.markAborted(taskId, code, fittedMessage)) {
            throw new IllegalStateException("任务终止写入失败: " + taskId);
        }
        if (!taskMapper.accumulateTokens(taskId, usage.tokensIn(), usage.tokensOut())) {
            throw new IllegalStateException("任务用量累计失败: " + taskId);
        }
        traceRecorder.record(executionId, Constants.AiTraceEventType.STATUS_CHANGE,
                "执行终止: " + fittedMessage, Map.of("terminateCode", code));
        traceRecorder.record(executionId, Constants.AiTraceEventType.STATUS_CHANGE,
                "任务终止", Map.of("task", Constants.AiTaskFlowStatus.ABORTED, "terminateCode", code));
        return true;
    }

    private LockedRun lockCurrent(UUID taskId, UUID executionId) {
        AiTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            return null;
        }
        AiTaskExecution latest = executionMapper.selectLatestByTaskIdForUpdate(taskId);
        return latest != null && Objects.equals(latest.getId(), executionId)
                ? new LockedRun(task, latest) : null;
    }

    private static Usage usageOf(AiTaskExecution execution) {
        int durationMs = execution.getCreatedAt() == null ? 0
                : clamp(Duration.between(execution.getCreatedAt(), LocalDateTime.now()).toMillis());
        return new Usage(value(execution.getSteps()), value(execution.getTokensIn()),
                value(execution.getTokensOut()), execution.getCost(), durationMs);
    }

    private static boolean isActive(String status) {
        return Constants.AiTaskExecutionStatus.RUNNING.equals(status)
                || Constants.AiTaskExecutionStatus.WAITING_CONFIRMATION.equals(status);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static int clamp(long value) {
        return (int) Math.min(Math.max(value, 0), Integer.MAX_VALUE);
    }

    private static String fit(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }

    private record LockedRun(AiTask task, AiTaskExecution execution) {
    }
}
