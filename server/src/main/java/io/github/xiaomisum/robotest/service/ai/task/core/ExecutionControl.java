package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 单轮执行控制：维护本地计量，并在每次外部调用前后执行活动状态、预算、心跳与轨迹检查。
 * 所有检查都是短事务或条件写，不跨越模型、向量或工具调用。
 */
public final class ExecutionControl {

    private final AiTaskExecution execution;
    private final BudgetGuard budgetGuard;
    private final AiTaskExecutionMapper executionMapper;
    private final TraceRecorder traceRecorder;
    private final ExecutionLifecycle executionLifecycle;
    private final long startedAtMs;

    private int steps;
    private long tokensIn;
    private long tokensOut;
    private BigDecimal cost;

    ExecutionControl(AiTaskExecution execution, BudgetGuard budgetGuard,
            AiTaskExecutionMapper executionMapper, TraceRecorder traceRecorder,
            ExecutionLifecycle executionLifecycle) {
        this.execution = execution;
        this.budgetGuard = budgetGuard;
        this.executionMapper = executionMapper;
        this.traceRecorder = traceRecorder;
        this.executionLifecycle = executionLifecycle;
        this.startedAtMs = System.currentTimeMillis();
        this.steps = value(execution.getSteps());
        this.tokensIn = value(execution.getTokensIn());
        this.tokensOut = value(execution.getTokensOut());
        this.cost = execution.getCost() == null ? BigDecimal.ZERO : execution.getCost();
    }

    /** 外部调用前检查；旧轮次或已终止轮次会立即停止，不触发网络或工具调用。 */
    public void beforeCall() {
        ensureActive();
        ensureWithinBudget();
    }

    /** 模型调用只在响应返回后计量一次，并在继续处理输出前完成心跳及预算复验。 */
    public void afterModelCall(long consumedTokensIn, long consumedTokensOut, BigDecimal consumedCost) {
        steps++;
        tokensIn += consumedTokensIn;
        tokensOut += consumedTokensOut;
        cost = cost.add(consumedCost == null ? BigDecimal.ZERO : consumedCost);
        heartbeatAndCheck();
    }

    /** 工具计为一步；达到步数上限时在工具执行前终止。 */
    public void beforeToolCall() {
        beforeCall();
        steps++;
        heartbeatAndCheck();
    }

    /** 工具返回后再次检查活动状态、时长及其他预算维度。 */
    public void afterToolCall() {
        heartbeatAndCheck();
    }

    public void recordTrace(String eventType, String summary, Map<String, Object> detail) {
        if (!traceRecorder.recordIfActive(execution.getId(), eventType, summary, detail)) {
            throw new InactiveExecutionException();
        }
    }

    public void recordPlan(Map<String, Object> plan) {
        recordTrace(Constants.AiTraceEventType.PLAN, "执行计划", plan);
        if (!executionMapper.markPlan(execution.getId(), plan)) {
            throw new InactiveExecutionException();
        }
    }

    public int maxSteps(int fallback) {
        Object configured = budget().get("maxSteps");
        return configured instanceof Number number ? number.intValue() : fallback;
    }

    public ExecutionLifecycle.Usage usage() {
        return new ExecutionLifecycle.Usage(steps, clamp(tokensIn), clamp(tokensOut), cost,
                clamp(System.currentTimeMillis() - startedAtMs));
    }

    private void heartbeatAndCheck() {
        if (!executionMapper.heartbeat(execution.getId(), steps, clamp(tokensIn), clamp(tokensOut), cost)) {
            throw new InactiveExecutionException();
        }
        ensureActive();
        ensureWithinBudget();
    }

    private void ensureActive() {
        if (!executionLifecycle.isCurrentActive(execution.getTaskId(), execution.getId())) {
            throw new InactiveExecutionException();
        }
    }

    private void ensureWithinBudget() {
        if (budgetGuard.exceeded(budget(), BudgetGuard.Consumed.of(
                steps, elapsedSeconds(), tokensIn, tokensOut, cost))) {
            throw new BudgetExceededException();
        }
    }

    private Map<String, Object> budget() {
        return execution.getBudget() == null ? Map.of() : execution.getBudget();
    }

    private long elapsedSeconds() {
        return (System.currentTimeMillis() - startedAtMs) / 1000;
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    private static int clamp(long number) {
        return (int) Math.min(Math.max(number, 0), Integer.MAX_VALUE);
    }

    static final class InactiveExecutionException extends RuntimeException {
    }

    static final class BudgetExceededException extends RuntimeException {
    }
}
