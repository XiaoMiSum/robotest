package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轨迹事件行（详设 3.6.4 events）：只增不改，游标增量拉取。
 */
@Data
public class AiTaskTraceEventRespDTO {

    private Integer seq;

    /** plan / ai_decision / tool_call / tool_result / proposal_emitted / status_change */
    private String eventType;

    private String summary;

    private java.util.Map<String, Object> detail;

    private LocalDateTime createdAt;
}
