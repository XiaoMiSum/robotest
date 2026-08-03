package io.github.xiaomisum.robotest.service.ai.assistant;

import java.util.Map;

/**
 * 全局智能助手工具执行器（每个工具一个实现，由 {@link ToolRegistry} 汇总注册）。
 *
 * <p>返回值为工具执行结果文本（只读为查询数据 JSON，写为执行结果摘要），
 * 由调用方落库为 tool 消息并回填 LLM。</p>
 */
public interface AiTool {

    AiToolDefinition definition();

    /**
     * 执行工具
     *
     * @param context 调用上下文
     * @param args    LLM 按 paramsSchema 生成的参数（JsonNode 已转 Map）
     * @return 执行结果文本
     */
    String execute(AiToolContext context, Map<String, Object> args);
}
