package io.github.xiaomisum.robotest.service.ai.assistant.tool;

/**
 * 工具类别（详设 4.1）：决定循环行为——QUERY 即时执行并回填、CLARIFY 校验后生成反问并终止本轮、
 * CHANGE 无执行体（调用即进入服务端拦截规范化，确认前零业务写入）。
 */
public enum ToolCategory {
    QUERY,
    CLARIFY,
    CHANGE
}
