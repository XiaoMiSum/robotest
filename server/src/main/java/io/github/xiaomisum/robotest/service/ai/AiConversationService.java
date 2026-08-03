package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiConversationItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConversationListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMessageRespDTO;

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
}
