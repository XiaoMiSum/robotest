package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

public interface AiAssistantConversationMapper extends BaseMapperX<AiAssistantConversation> {

    /** 本人当前空间会话列表（详设 3.2）：归属人 + 归属空间过滤 + 标题包含检索，按创建时间倒序 */
    default PageResult<AiAssistantConversation> findPage(PageParam pageParam, UUID userId, UUID workspaceId,
            String keyword) {
        return selectPage(pageParam, new LambdaQueryWrapperX<AiAssistantConversation>()
                .eq(AiAssistantConversation::getUserId, userId)
                .eq(AiAssistantConversation::getWorkspaceId, workspaceId)
                .likeIfPresent(AiAssistantConversation::getTitle, keyword)
                .orderByDesc(AiAssistantConversation::getCreatedAt));
    }
}
