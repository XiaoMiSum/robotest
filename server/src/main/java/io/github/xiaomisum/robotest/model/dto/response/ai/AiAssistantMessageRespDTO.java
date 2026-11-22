package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 消息响应（详设 3.6）：citations 为来源引用（只读问答必带）。
 */
@Data
public class AiAssistantMessageRespDTO {

    private UUID id;

    private UUID conversationId;

    /** user / assistant / system / tool（role = tool 的中间消息不返回，详设 3.6） */
    private String role;

    /** 消息正文（Markdown） */
    private String content;

    /** 用户选中的上下文实体引用集合 */
    private List<Map<String, Object>> attachments;

    /** 用户消息的附图引用 [{fileId, name}]（仅存引用不存字节），助手消息恒为空 */
    private List<Map<String, Object>> images;

    /** assistant 消息的工具调用载荷（结构见详设 4.1；非 assistant 消息为 null） */
    private List<Map<String, Object>> toolCalls;

    /** 来源引用集合 */
    private List<Map<String, Object>> citations;

    /** streaming / done / interrupted / error */
    private String status;

    private LocalDateTime createdAt;
}
