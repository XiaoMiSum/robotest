package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.task.DispatchableTask;
import io.github.xiaomisum.robotest.framework.task.TaskDispatchContext;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * AiTaskHandler → DispatchableTask 适配器。
 * 将已有 AI 任务处理器适配为统一分发接口，使 AiTaskService 可通过 TaskExecutorRegistry 统一查找。
 */
@Component
public class AiTaskDispatchAdapter implements DispatchableTask {

    private final Map<String, AiTaskHandler> handlerMap;

    public AiTaskDispatchAdapter(java.util.List<AiTaskHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AiTaskHandler::type, h -> h, (a, b) -> a));
    }

    @Override
    public String type() {
        return "_ai_all";
    }

    @Override
    public Map<String, Object> execute(TaskDispatchContext context) {
        // AiTaskAdapter 不直接 execute，而是通过 AiTaskService 分发到具体 handler
        throw new UnsupportedOperationException(
                "AiTaskDispatchAdapter 不支持直接 execute，请通过 AiTaskService 分发");
    }

    /**
     * 按 AI 任务类型查找对应 handler 并执行。
     * 供 AiTaskServiceImpl 使用，替代其内部 handlerMap。
     */
    public Map<String, Object> dispatch(String aiTaskType, AiAnalysisTask task) {
        AiTaskHandler handler = handlerMap.get(aiTaskType);
        if (handler == null) {
            return null;
        }
        return handler.execute(task);
    }

    /** 是否包含指定 AI 任务类型的 handler */
    public boolean supports(String aiTaskType) {
        return handlerMap.containsKey(aiTaskType);
    }
}
