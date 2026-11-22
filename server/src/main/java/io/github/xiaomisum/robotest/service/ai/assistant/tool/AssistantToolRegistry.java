package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表（详设 4.1：服务端固化 8 个（7 查询/检索 + 1 澄清），清单外调用按 1000018255 拒绝）。
 *
 * <p>QUERY / CLARIFY 经 Spring 收集 {@link AssistantTool} Bean 注册（新增执行体自动入表）。</p>
 */
@Component
public class AssistantToolRegistry {

    private final Map<String, AssistantToolDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, AssistantTool> executables = new LinkedHashMap<>();

    public AssistantToolRegistry(List<AssistantTool> registered) {
        for (AssistantTool tool : registered) {
            AssistantToolDefinition definition = tool.definition();
            definitions.put(definition.name(), definition);
            executables.put(definition.name(), tool);
        }
    }

    /** 清单外返回 null，由循环按 1000018255 拒绝 */
    public AssistantToolDefinition definition(String name) {
        return name == null ? null : definitions.get(name);
    }

    /** 可执行体（QUERY / CLARIFY） */
    public AssistantTool executable(String name) {
        return name == null ? null : executables.get(name);
    }

    public Collection<AssistantToolDefinition> all() {
        return definitions.values();
    }
}
