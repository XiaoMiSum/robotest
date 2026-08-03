package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.service.ai.assistant.AiToolContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.Map;

/**
 * 工具统一执行入口（详细设计 4.1/4.2）。
 *
 * <p>按工具名从 {@link ToolRegistry} 解析并执行；权限不足/业务规则校验失败等异常
 * 一律转为错误 JSON 返回，由 LLM 转述，不抛异常中断会话（详细设计 4.1 AD-6）。</p>
 */
@Component
public class AiToolExecutor {

    @Resource
    private ToolRegistry toolRegistry;

    /**
     * 执行工具
     *
     * @param context  调用上下文
     * @param toolName 工具名（注册表唯一名）
     * @param args     LLM 生成的参数
     * @return 工具执行结果文本（只读为查询数据 JSON，失败为 error JSON）
     */
    public String execute(AiToolContext context, String toolName, Map<String, Object> args) {
        AiTool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return errorResult("未知工具: " + toolName);
        }
        try {
            return tool.execute(context, args);
        } catch (ServiceException e) {
            return errorResult(e.getMessage());
        } catch (Exception e) {
            return errorResult("工具执行异常: " + e.getMessage());
        }
    }

    private String errorResult(String message) {
        return JsonUtils.toJsonString(Map.of("error", message));
    }
}
