package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiMessage;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 助手消息 Mapper。历史按时间升序全量返回（详细设计 3.1）。
 */
public interface AiMessageMapper extends BaseMapperX<AiMessage> {

    /**
     * 会话历史：created_at 升序（同轮次 id 决胜，UUID v7 时序性保证稳定）
     */
    default List<AiMessage> selectByConversationId(UUID conversationId) {
        return selectList(new LambdaQueryWrapperX<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getCreatedAt)
                .orderByAsc(AiMessage::getId));
    }

    /**
     * 级联逻辑删除：删除会话时清理其全部消息
     */
    default void deleteByConversationIds(Collection<UUID> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return;
        }
        delete(new LambdaQueryWrapperX<AiMessage>()
                .in(AiMessage::getConversationId, conversationIds));
    }

    /**
     * 超时悬空补偿：找带 tool_calls 但缺少对应 tool 消息的 assistant 消息（详细设计 4.2），
     * 调用方补齐 tool 消息后再回填 LLM，保证 tool_calls 后必跟 tool 的协议约束。
     */
    default List<AiMessage> selectAssistantWithPendingToolCalls(UUID conversationId) {
        return selectList(new LambdaQueryWrapperX<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getRole, AiMessage.ROLE_ASSISTANT)
                .isNotNull(AiMessage::getToolCalls));
    }
}
