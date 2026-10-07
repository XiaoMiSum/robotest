package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

/**
 * 会话列表查询（GET /api/ai/conversations，详设 3.2）：仅按本人归属 + 标题包含检索，
 * 会话按人隔离（详设 3.1），不携带工作空间 / 项目条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiAssistantConversationPageReqDTO extends PageParam {

    /** 标题包含检索（可选） */
    private String keyword;

    public AiAssistantConversationPageReqDTO() {
        // 详设 3.2 规定默认 pageSize 20，覆盖框架 PageParam 默认 10
        setPageSize(20);
    }
}
