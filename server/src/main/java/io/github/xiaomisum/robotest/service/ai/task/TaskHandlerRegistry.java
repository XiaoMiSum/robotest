package io.github.xiaomisum.robotest.service.ai.task;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务处理器注册表：type → TaskHandler 的分发依据（详设 4.2），空集时全部类型报 1000018114。
 */
@Component
public class TaskHandlerRegistry {

    private final Map<String, TaskHandler> handlers;

    public TaskHandlerRegistry(List<TaskHandler> beans) {
        this.handlers = beans.stream()
                .collect(Collectors.toMap(TaskHandler::type, Function.identity(), (a, b) -> a));
    }

    public TaskHandler get(String type) {
        return handlers.get(type);
    }
}
