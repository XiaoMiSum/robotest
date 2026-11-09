package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapterRegistry;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.UUID;

/** 异步执行单入口：解析类型策略，并统一提交成功或失败终态。 */
@Component
public class TaskExecutionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionDispatcher.class);

    @Resource
    private TaskTypeAdapterRegistry adapterRegistry;
    @Resource
    private AgentExecutionRunner agentExecutionRunner;
    @Resource
    private BudgetGuard budgetGuard;
    @Resource
    private AiTaskExecutionMapper executionMapper;
    @Resource
    private TraceRecorder traceRecorder;
    @Resource
    private ExecutionLifecycle executionLifecycle;

    public record DispatchContext(AiTask task, AiTaskExecution execution, AiModelConfig model,
            UUID workspaceId, UUID projectId, String feedback) {
    }

    @Async
    public void dispatch(DispatchContext context) {
        AiTask task = context.task();
        AiTaskExecution execution = context.execution();
        ExecutionControl control = new ExecutionControl(execution, budgetGuard, executionMapper,
                traceRecorder, executionLifecycle);
        try {
            TaskTypeAdapter adapter = adapterRegistry.get(task.getType());
            if (adapter == null) {
                terminate(context, control, ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED.code(),
                        "不支持的任务类型");
                return;
            }
            control.beforeCall();
            TaskExecutionStrategy strategy = adapter.executionStrategy(agentExecutionRunner);
            if (strategy == null) {
                throw new IllegalStateException("任务类型未注册执行策略: " + task.getType());
            }
            TaskExecutionStrategy.ExecutionResult result = strategy.execute(
                    new TaskExecutionStrategy.ExecutionContext(task, execution, context.model(),
                            context.workspaceId(), context.projectId(), context.feedback(), adapter),
                    control);
            // 收尾检查：终态竞争必查；步数按已走口径（恰满上限的正常完成不得被误杀为预算耗尽）
            control.ensureFinishing();
            executionLifecycle.succeed(task.getId(), execution.getId(), result.proposals(),
                    result.resultMeta(), control.usage());
        } catch (ExecutionControl.InactiveExecutionException ignored) {
            // 终态竞争或旧执行轮次已由赢家收尾，不重复累计用量。
        } catch (ExecutionControl.BudgetExceededException e) {
            terminate(context, control, ErrorCodeConstants.AI_EXECUTION_FINISHED.code(), "预算耗尽");
        } catch (ServiceException e) {
            terminate(context, control, e.getCode(), e.getMessage());
        } catch (DuplicateKeyException e) {
            terminate(context, control, ErrorCodeConstants.AI_PROPOSAL_ALREADY_CONFIRMED.code(), "提案键重复");
        } catch (RuntimeException e) {
            log.warn("[AI] 执行策略运行失败 taskId={} type={}", task.getId(), task.getType(), e);
            terminate(context, control, ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    "执行策略运行异常: " + ModelOutputSupport.nvl(e.getMessage()));
        }
    }

    private void terminate(DispatchContext context, ExecutionControl control, int code, String message) {
        executionLifecycle.terminate(context.task().getId(), context.execution().getId(), code,
                ModelOutputSupport.nvl(message), control.usage());
    }
}
