package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.Map;

/**
 * 确认执行 / 取消预览回执（详设 3.7 / 3.8）：整体 200、逐项成败，
 * execution 结构与消息 execution 列同构（status / executedBy / executedAt / results / link）。
 */
@Data
public class AiAssistantExecuteRespDTO {

    private Map<String, Object> execution;
}
