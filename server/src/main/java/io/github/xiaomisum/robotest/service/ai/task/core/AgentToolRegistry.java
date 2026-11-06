package io.github.xiaomisum.robotest.service.ai.task.core;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务工具注册表（总册 2.6.5：代码注册侧）：Spring 收集 {@link AgentTool} Bean；
 * 执行期白名单 = 类型适配器声明 ∩ ai_task_tool 启用行 ∩ 本表（不信赖模型，清单外拒绝）。
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public AgentToolRegistry(List<AgentTool> registered) {
        for (AgentTool tool : registered) {
            AgentToolDefinition definition = tool.definition();
            AgentTool prior = tools.putIfAbsent(definition.code(), tool);
            if (prior != null) {
                throw new IllegalStateException("重复注册的任务工具: " + definition.code());
            }
        }
    }

    /** 清单外返回 null，由执行核回喂拒绝文本 */
    public AgentTool get(String code) {
        return code == null ? null : tools.get(code);
    }

    public AgentToolDefinition definition(String code) {
        AgentTool tool = get(code);
        return tool == null ? null : tool.definition();
    }
}
