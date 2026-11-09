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

    /** 外部调用前检查（步数闸门）；旧轮次或已终止轮次会立即停止，不触发网络或工具调用。 */
    public void beforeCall() {
        ensureActive();
        ensureWithinBudget(steps);
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

    /**
     * 策略返回后的收尾检查：活动状态必查（终态竞争的最后闸门），预算按「已走步数落在上限内」判定——
     * 刚完成的最后一步允许正好占满步数预算（助手 5 跳用尽正常 done 的先到者口径），时长 / token /
     * 成本维度仍按耗尽即超限。
     */
    public void ensureFinishing() {
        ensureActive();
        ensureWithinBudget(Math.max(steps - 1, 0));
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
        // 步数按「本步已走完、可占满上限」判定：步数闸门只在 beforeCall 拦截下一步，
        // 否则恰好走满上限的收尾（如助手 5 跳）会被本步心跳误杀为预算耗尽
        ensureWithinBudget(Math.max(steps - 1, 0));
    }

    private void ensureActive() {
        if (!executionLifecycle.isCurrentActive(execution.getTaskId(), execution.getId())) {
            throw new InactiveExecutionException();
        }
    }

    private void ensureWithinBudget(int budgetedSteps) {
        if (budgetGuard.exceeded(budget(), BudgetGuard.Consumed.of(
                budgetedSteps, elapsedSeconds(), tokensIn, tokensOut, cost))) {
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

    /** 终态竞争 / 执行已被中止：赢家收尾，迟到调用方不得再写汇总与终态（策略层可识别并按需收口）。 */
    public static final class InactiveExecutionException extends RuntimeException {
    }

    /** 预算任一维度耗尽（总册 4.2，终止码 1000018127 口径）。 */
    public static final class BudgetExceededException extends RuntimeException {
    }
}
