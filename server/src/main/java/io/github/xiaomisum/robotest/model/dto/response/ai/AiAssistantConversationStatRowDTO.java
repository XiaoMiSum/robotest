package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 会话消息聚合行（详设 3.2 列表项 messageCount / lastMessageAt）：按页内会话一次聚合，避免逐会话计数
 */
@Data
public class AiAssistantConversationStatRowDTO {

    private UUID conversationId;

    private Integer messageCount;

    private LocalDateTime lastMessageAt;
}
