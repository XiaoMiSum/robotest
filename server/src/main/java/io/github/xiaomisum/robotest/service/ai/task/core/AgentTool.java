package io.github.xiaomisum.robotest.service.ai.task.core;

import java.util.Map;

/**
 * 可执行任务工具（总册 2.6.5 / 4.2）：执行体只读或确认型（side_effect 仅 readonly / confirmation），
 * 结果以 JSON 文本回喂模型；查询失败不中断循环，由执行核回填错误文本让模型改道。
 */
public interface AgentTool {

    AgentToolDefinition definition();

    /**
     * 执行工具。
     *
     * @param context 执行上下文（作用域限权）
     * @param args    模型按 paramsSchema 生成的参数
     * @return 执行结果 JSON 文本
     */
    String execute(AgentToolContext context, Map<String, Object> args);
}
