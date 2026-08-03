package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 助手会话列表项（详细设计 3.1）
 */
@Data
public class AiConversationItemRespDTO {

    private UUID id;
    private String title;
    private LocalDateTime lastActiveAt;
}
