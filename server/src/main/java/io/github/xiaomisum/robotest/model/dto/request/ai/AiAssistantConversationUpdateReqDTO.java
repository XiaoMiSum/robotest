package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会话重命名 / 归档（PATCH /api/ai/conversations/{conversationId}，详设 3.4）：
 * 部分更新（C11），title 与 status 至少其一，归档动作仅接受 archived。
 */
@Data
public class AiAssistantConversationUpdateReqDTO {

    /** 新标题（≤100 字符，非空白） */
    @Size(max = 100, message = "会话标题不能超过 100 字符")
    private String title;

    /** 归档标记：详设 3.4 仅定义 archived 动作 */
    private String status;
}
