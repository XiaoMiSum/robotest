package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import java.util.Map;

/**
 * 可执行工具（QUERY / CLARIFY）接口；CHANGE 类无执行体——注册表只登记定义，
 * 调用即转 {@link AssistantIntentBuilder} 拦截规范化（详设 4.1）。
 */
public interface AssistantTool {

    AssistantToolDefinition definition();

    /**
     * 执行工具（详设 4.1：查询失败不中断会话，由循环回填错误文本给模型）
     *
     * @param context 执行上下文（活跃空间限权、用户身份、页面附件）
     * @param args    模型按 paramsSchema 生成的参数
     * @return 执行结果 JSON 文本
     */
    String execute(AssistantToolContext context, Map<String, Object> args);
}
