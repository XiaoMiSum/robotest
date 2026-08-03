package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 助手消息响应（详细设计 3.1；role=tool 的消息前端渲染为工具调用卡片）
 */
@Data
public class AiMessageRespDTO {

    private UUID id;
    private String role;
    private String content;
    /** assistant 消息发起的工具调用载荷（name/arguments/callId），非工具消息为空 */
    private List<Map<String, Object>> toolCalls;
    private String toolCallId;
    private LocalDateTime createdAt;
}
