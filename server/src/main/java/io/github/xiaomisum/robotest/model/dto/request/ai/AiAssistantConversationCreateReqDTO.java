package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建会话（POST /api/ai/conversations，详设 3.3）：title 缺省「新会话」（首问后回填），
 * context 为页面实体引用（非上下文标识），工作空间标识由服务端从请求头写入 context_snapshot。
 */
@Data
public class AiAssistantConversationCreateReqDTO {

    /** 会话标题（≤100 字符，可省略） */
    @Size(max = 100, message = "会话标题不能超过 100 字符")
    private String title;

    /** 页面实体引用（可省略，entityId 提供时须属当前用户可见范围） */
    private AiAssistantEntityRefReqDTO context;
}
