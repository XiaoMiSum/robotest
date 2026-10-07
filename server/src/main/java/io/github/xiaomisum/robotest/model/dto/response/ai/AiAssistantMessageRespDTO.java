package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 消息响应（详设 3.6）：intent 为固化预览结构，execution 为执行回执，
 * citations 为来源引用（只读问答必带）。
 */
@Data
public class AiAssistantMessageRespDTO {

    private UUID id;

    private UUID conversationId;

    /** user / assistant / system */
    private String role;

    /** 消息正文（Markdown） */
    private String content;

    /** 用户选中的上下文实体引用集合 */
    private List<Map<String, Object>> attachments;

    /** 意图解析结构化预览（结构见详设 4.3，无预览为 null） */
    private Map<String, Object> intent;

    /** 来源引用集合 */
    private List<Map<String, Object>> citations;

    /** 执行回执 {status: executed/rejected, …}，未执行为 null */
    private Map<String, Object> execution;

    /** streaming / done / interrupted / error */
    private String status;

    private LocalDateTime createdAt;
}
