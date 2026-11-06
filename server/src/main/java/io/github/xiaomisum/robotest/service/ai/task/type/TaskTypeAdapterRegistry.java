package io.github.xiaomisum.robotest.service.ai.task.type;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 类型适配器注册表（总册 3.6.1）：Spring 收集 {@link TaskTypeAdapter} Bean；
 * 同一 type 重复注册为装配错误，启动即失败（不信赖运行期发现）。
 */
@Component
public class TaskTypeAdapterRegistry {

    private final Map<String, TaskTypeAdapter> adapters = new LinkedHashMap<>();

    public TaskTypeAdapterRegistry(List<TaskTypeAdapter> registered) {
        for (TaskTypeAdapter adapter : registered) {
            TaskTypeAdapter prior = adapters.putIfAbsent(adapter.type(), adapter);
            if (prior != null) {
                throw new IllegalStateException("重复注册的任务类型: " + adapter.type());
            }
        }
    }

    /** 未注册返回 null（提交期映射 1000018114，执行期为装配缺陷） */
    public TaskTypeAdapter get(String type) {
        return type == null ? null : adapters.get(type);
    }

    public Collection<TaskTypeAdapter> all() {
        return adapters.values();
    }
}
