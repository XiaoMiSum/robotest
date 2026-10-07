package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantConversationStatRowDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiAssistantMessageMapper extends BaseMapperX<AiAssistantMessage> {

    /** 会话消息历史（详设 3.6）：倒序分页 */
    default PageResult<AiAssistantMessage> findPage(PageParam pageParam, UUID conversationId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<AiAssistantMessage>()
                .eq(AiAssistantMessage::getConversationId, conversationId)
                .orderByDesc(AiAssistantMessage::getCreatedAt));
    }

    /** 近 N 条消息（详设 4.4 多轮上下文 N=20）：倒序取近条，调用方按时间正序还原 */
    default List<AiAssistantMessage> listRecent(UUID conversationId, int limit) {
        return selectList(new LambdaQueryWrapperX<AiAssistantMessage>()
                .eq(AiAssistantMessage::getConversationId, conversationId)
                .orderByDesc(AiAssistantMessage::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /** 会话列表的消息数与最近消息时间（详设 3.2）：页内会话一次聚合，避免逐会话计数 */
    @Select("<script>SELECT m.conversation_id AS conversationId, COUNT(*) AS messageCount, "
            + "MAX(m.created_at) AS lastMessageAt "
            + "FROM ai_assistant_message m WHERE m.is_deleted = FALSE AND m.conversation_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY m.conversation_id</script>")
    List<AiAssistantConversationStatRowDTO> statByConversationIds(@Param("ids") Collection<UUID> ids);
}
