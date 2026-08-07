package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiConversationItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConversationListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMessageRespDTO;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ToolCall;

import java.util.List;
import java.util.UUID;

/**
 * 全局智能助手会话管理（详细设计 3.1）
 */
public interface AiConversationService {

    /**
     * 会话列表（键集分页，按 last_active_at DESC, id DESC）
     *
     * @param cursor 不透明游标，可空表示首页；非法游标按首页处理
     * @param size   每页大小（默认 20，上限 50）
     */
    AiConversationListRespDTO listConversations(UUID userId, UUID workspaceId, String cursor, Integer size);

    /**
     * 新建空会话（title = 新会话，首条消息后自动更名）
     */
    AiConversationItemRespDTO createConversation(UUID userId, UUID workspaceId);

    /**
     * 逻辑删除会话及其全部消息（仅本人）
     */
    void deleteConversation(UUID userId, UUID workspaceId, UUID conversationId);

    /**
     * 清空当前用户当前空间全部会话（级联删除消息）
     */
    void clearConversations(UUID userId, UUID workspaceId);

    /**
     * 消息历史（按时间升序全量返回；归属校验同 3.1，非本人会话按不存在处理）
     */
    List<AiMessageRespDTO> listMessages(UUID userId, UUID workspaceId, UUID conversationId);

    /**
     * 追加用户消息并触碰 lastActiveAt；若会话标题仍为"新会话"则自动更名（首条消息前 30 字）
     *
     * @return 持久化的消息 ID
     */
    UUID appendUserMessage(UUID conversationId, String content);

    /**
     * 追加 assistant 消息（可能携带工具调用载荷）
     *
     * @return 持久化的消息 ID
     */
    UUID appendAssistantMessage(UUID conversationId, String content, List<ToolCall> toolCalls);

    /**
     * 追加 tool 消息（工具执行结果）
     *
     * @return 持久化的消息 ID
     */
    UUID appendToolMessage(UUID conversationId, String toolCallId, String content);

    /**
     * 校验会话归属并返回会话实体（归属不匹配抛 AI_CONVERSATION_NOT_FOUND）
     */
    io.github.xiaomisum.robotest.model.entity.ai.AiConversation requireOwned(
            UUID userId, UUID workspaceId, UUID conversationId);
}
