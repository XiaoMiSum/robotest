package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 会话响应（详设 3.2 单条 / 3.3 创建响应）：列表项含 messageCount 与 lastMessageAt 聚合，
 * contextSnapshot 为创建时上下文快照（不参与权限判定）。
 */
@Data
public class AiAssistantConversationRespDTO {

    private UUID id;

    private String title;

    /** active / archived */
    private String status;

    /** 最近一条消息时间（无消息为 null） */
    private LocalDateTime lastMessageAt;

    private Integer messageCount;

    /** { workspaceId, context, capturedAt } */
    private Map<String, Object> contextSnapshot;
}
