package io.github.xiaomisum.robotest.framework.task;

import java.util.Map;
import java.util.UUID;

/**
 * 统一可分发任务接口（Command 模式）。
 * 将 ScheduledTaskRunner（定时/手动）与 AiTaskHandler（AI 异步）收口为同一分发语义。
 * 各处理器实现此接口并注册到 {@link TaskExecutorRegistry}，由调度入口按 type 统一分发。
 */
public interface DispatchableTask {

    /** 任务类型标识（唯一键，如 scene_execute / review_check / bug_clustering） */
    String type();

    /**
     * 执行任务。
     *
     * @param context 分发上下文（任务 ID、项目 ID、触发方式、扩展参数）
     * @return 执行结果快照（可选，null 表示无结果）
     */
    Map<String, Object> execute(TaskDispatchContext context);

    /**
     * 幂等键：默认取 taskId，同类型同目标不重复执行。
     * 覆盖此方法可自定义幂等策略（如 AI 任务按 targetId 去重）。
     */
    default String idempotencyKey(TaskDispatchContext context) {
        return context.taskId().toString();
    }
}
