package io.github.xiaomisum.robotest.framework.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务执行器注册表 — 统一管理所有 {@link DispatchableTask} 实现的注册与查找。
 * Spring 自动收集所有 DispatchableTask Bean，按 type() 建立索引。
 */
@Slf4j
@Component
public class TaskExecutorRegistry implements InitializingBean {

    private final List<DispatchableTask> tasks;
    private Map<String, DispatchableTask> registry;

    public TaskExecutorRegistry(List<DispatchableTask> tasks) {
        this.tasks = tasks != null ? tasks : List.of();
    }

    @Override
    public void afterPropertiesSet() {
        registry = tasks.stream()
                .collect(Collectors.toMap(
                        DispatchableTask::type,
                        Function.identity(),
                        (a, b) -> {
                            log.warn("[TaskRegistry] 重复注册 type={}，保留先注册者", a.type());
                            return a;
                        }));
        log.info("[TaskRegistry] 已注册任务类型: {}", registry.keySet());
    }

    /** 获取指定类型的执行器，不存在返回 null */
    public DispatchableTask get(String type) {
        return registry.get(type);
    }

    /** 获取指定类型的执行器，不存在抛异常 */
    public DispatchableTask require(String type) {
        DispatchableTask task = registry.get(type);
        if (task == null) {
            throw new IllegalArgumentException("任务类型 " + type + " 未注册执行器");
        }
        return task;
    }

    /** 已注册的所有任务类型 */
    public Set<String> registeredTypes() {
        return registry.keySet();
    }

    /** 是否包含指定类型 */
    public boolean supports(String type) {
        return registry.containsKey(type);
    }
}
