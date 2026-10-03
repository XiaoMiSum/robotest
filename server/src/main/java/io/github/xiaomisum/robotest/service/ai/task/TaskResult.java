package io.github.xiaomisum.robotest.service.ai.task;

import java.util.Map;

/**
 * 任务执行结果：result 为产物明细（单一事实源，详设 2.6），tokens 以模型账单汇总（4.3）。
 */
public record TaskResult(Map<String, Object> result, int tokensIn, int tokensOut) {
}
