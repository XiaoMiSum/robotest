package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.Constants;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 迁移期旧处理器注册表：仅保留尚未迁移的助手解析与向量重建，普通任务不得回落双轨。
 */
@Component
public class TaskHandlerRegistry {

    private final Map<String, TaskHandler> handlers;

    public TaskHandlerRegistry(List<TaskHandler> beans) {
        this.handlers = beans.stream()
                .filter(handler -> Constants.AiTaskType.ASSISTANT_PARSE.equals(handler.type())
                        || Constants.AiTaskType.VECTOR_REINDEX.equals(handler.type()))
                .collect(Collectors.toMap(TaskHandler::type, Function.identity(), (a, b) -> a));
    }

    public TaskHandler get(String type) {
        return handlers.get(type);
    }
}
