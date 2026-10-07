package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

/**
 * 消息历史查询（GET /api/ai/conversations/{conversationId}/messages，详设 3.6）：倒序分页。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiAssistantMessagePageReqDTO extends PageParam {

    public AiAssistantMessagePageReqDTO() {
        // 详设 3.6 规定默认 pageSize 20，覆盖框架 PageParam 默认 10
        setPageSize(20);
    }
}
