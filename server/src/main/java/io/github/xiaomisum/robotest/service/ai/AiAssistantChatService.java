package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantSendReqDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 全局智能助手对话服务（详细设计 3.2 / 3.3 / 4.2）。
 *
 * <p>Function Calling 执行循环、消息落库、标题自动更名、悬空补偿与 SSE 帧发送。
 * 写操作经确认令牌中断后由 approve/cancel 继续。</p>
 */
public interface AiAssistantChatService {

    /**
     * 发送消息并以 SSE 流式返回助手回复（详细设计 3.2）。
     * Function Calling 循环 ≤5 次，遇到写工具中断等待确认。
     *
     * @param userId         当前用户
     * @param workspaceId    当前工作空间
     * @param conversationId 目标会话
     * @param reqDTO         消息内容 + pageContext + modelId
     * @return SSE emitter（delta/tool_call/confirm_required/done/error 帧）
     */
    SseEmitter sendMessage(UUID userId, UUID workspaceId, UUID conversationId,
                           AiAssistantSendReqDTO reqDTO);

    /**
     * 确认执行写操作（详细设计 3.3.1），SSE 流式返回最终答复。
     * 令牌校验（不存在/超时/已消费/归属不匹配）返回 6011。
     *
     * @param userId       当前用户
     * @param workspaceId  当前工作空间
     * @param confirmToken 确认令牌
     * @return SSE emitter（delta/done 帧）
     */
    SseEmitter approve(UUID userId, UUID workspaceId, String confirmToken);

    /**
     * 取消写操作（详细设计 3.3.2），落一条 tool 消息供后续上下文感知。
     *
     * @param userId       当前用户
     * @param workspaceId  当前工作空间
     * @param confirmToken 确认令牌
     */
    void cancel(UUID userId, UUID workspaceId, String confirmToken);
}
