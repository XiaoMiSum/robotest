package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;

import java.util.List;

/**
 * 发送消息（POST /api/ai/conversations/{id}/messages，详设 3.5）：content 与附件的业务校验
 * （1000018260 / 1000018262）在服务层按错误码口径执行，DTO 不做注解约束。
 */
@Data
public class AiAssistantMessageSendReqDTO {

    /** 消息正文（Markdown，非空且 ≤4000 字符） */
    private String content;

    /** 上下文实体引用集合（可选，须属当前活跃工作空间） */
    private List<AiAssistantEntityRefReqDTO> attachments;
}
