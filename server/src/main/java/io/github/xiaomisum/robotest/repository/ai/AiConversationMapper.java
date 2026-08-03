package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiConversation;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 助手会话 Mapper。列表查询为键集分页（last_active_at DESC, id DESC，详细设计 3.1），
 * 避免 last_active_at 动态前移时偏移分页的重复与漏项。
 */
public interface AiConversationMapper extends BaseMapperX<AiConversation> {

    /**
     * 键集分页：命中既有索引 idx_conv_user_ws，多取 1 条供调用方判定是否有下一页
     */
    default List<AiConversation> selectCursorPage(UUID userId, UUID workspaceId,
            LocalDateTime cursorTime, UUID cursorId, int size) {
        return selectList(new LambdaQueryWrapperX<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getWorkspaceId, workspaceId)
                .apply(cursorTime != null && cursorId != null,
                        "(last_active_at, id) < ({0}, {1})", cursorTime, cursorId)
                .orderByDesc(AiConversation::getLastActiveAt)
                .orderByDesc(AiConversation::getId)
                .last("LIMIT " + (size + 1)));
    }

    /**
     * 归属校验：按用户 + 空间定位会话，命中为空即视为不存在（不暴露存在性，详细设计 3.1）
     */
    default AiConversation selectOwned(UUID userId, UUID workspaceId, UUID conversationId) {
        return selectOne(new LambdaQueryWrapperX<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getWorkspaceId, workspaceId));
    }

    /**
     * 清空会话：返回当前用户当前空间全部会话 id（供消息级联逻辑删除）
     */
    default List<UUID> selectIdsByUserAndWorkspace(UUID userId, UUID workspaceId) {
        return selectList(new LambdaQueryWrapperX<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getWorkspaceId, workspaceId)
                .select(AiConversation::getId))
                .stream().map(AiConversation::getId).toList();
    }
}
