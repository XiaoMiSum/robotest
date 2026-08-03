package io.github.xiaomisum.robotest.service.ai.assistant;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局智能助手工具注册表（详细设计 4.1）。
 *
 * <p>汇集全部 {@link AiTool} 实现；写工具实际可用集 = 注册表 ∩ assistantWriteToolWhitelist
 * （系统配置），白名单过滤发生在 Function Calling 工具清单组装时（4.2）。</p>
 */
@Component
public class ToolRegistry {

    private final Map<String, AiTool> byName = new LinkedHashMap<>();

    @Resource
    public void init(List<AiTool> tools) {
        for (AiTool tool : tools) {
            byName.put(tool.definition().name(), tool);
        }
    }

    public AiTool get(String name) {
        return byName.get(name);
    }

    public boolean contains(String name) {
        return byName.containsKey(name);
    }

    public List<AiTool> all() {
        return new ArrayList<>(byName.values());
    }
}
