package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConversationItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConversationListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMessageRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiMessage;
import io.github.xiaomisum.robotest.repository.ai.AiConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiMessageMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 全局智能助手会话管理实现（详细设计 3.1）。
 *
 * <p>游标为不透明字符串，编码 {@code lastActiveAt 毫秒 + ":" + id} 后 Base64；
 * 非法游标按首页处理（解码失败视为首页）。</p>
 */
@Service
public class AiConversationServiceImpl implements AiConversationService {

    private static final String NEW_CONVERSATION_TITLE = "新会话";
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    @Resource
    private AiConversationMapper conversationMapper;
    @Resource
    private AiMessageMapper messageMapper;

    @Override
    public AiConversationListRespDTO listConversations(UUID userId, UUID workspaceId, String cursor, Integer size) {
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        Cursor decoded = decodeCursor(cursor);

        List<AiConversation> rows = conversationMapper.selectCursorPage(
                userId, workspaceId, decoded.lastActiveAt(), decoded.id(), pageSize);

        boolean hasMore = rows.size() > pageSize;
        List<AiConversation> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

        AiConversationListRespDTO resp = new AiConversationListRespDTO();
        resp.setItems(pageRows.stream().map(this::toItem).toList());
        if (hasMore) {
            AiConversation last = pageRows.get(pageRows.size() - 1);
            resp.setNextCursor(encodeCursor(last.getLastActiveAt(), last.getId()));
        }
        return resp;
    }

    @Override
    public AiConversationItemRespDTO createConversation(UUID userId, UUID workspaceId) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setWorkspaceId(workspaceId);
        conversation.setTitle(NEW_CONVERSATION_TITLE);
        conversation.setLastActiveAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return toItem(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(UUID userId, UUID workspaceId, UUID conversationId) {
        AiConversation conversation = requireOwned(userId, workspaceId, conversationId);
        messageMapper.deleteByConversationIds(List.of(conversation.getId()));
        conversationMapper.deleteById(conversation.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearConversations(UUID userId, UUID workspaceId) {
        List<UUID> conversationIds = conversationMapper.selectIdsByUserAndWorkspace(userId, workspaceId);
        messageMapper.deleteByConversationIds(conversationIds);
        conversationMapper.delete(new LambdaQueryWrapperX<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getWorkspaceId, workspaceId));
    }

    @Override
    public List<AiMessageRespDTO> listMessages(UUID userId, UUID workspaceId, UUID conversationId) {
        requireOwned(userId, workspaceId, conversationId);
        return messageMapper.selectByConversationId(conversationId).stream().map(this::toMessage).toList();
    }

    private AiConversation requireOwned(UUID userId, UUID workspaceId, UUID conversationId) {
        AiConversation conversation = conversationMapper.selectOwned(userId, workspaceId, conversationId);
        // 归属校验：非本人或跨空间一律按不存在处理（3.1，不暴露存在性）
        if (conversation == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    private AiConversationItemRespDTO toItem(AiConversation conversation) {
        AiConversationItemRespDTO dto = new AiConversationItemRespDTO();
        dto.setId(conversation.getId());
        dto.setTitle(conversation.getTitle());
        dto.setLastActiveAt(conversation.getLastActiveAt());
        return dto;
    }

    private AiMessageRespDTO toMessage(AiMessage message) {
        AiMessageRespDTO dto = new AiMessageRespDTO();
        dto.setId(message.getId());
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setToolCalls(message.getToolCalls());
        dto.setToolCallId(message.getToolCallId());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    private String encodeCursor(LocalDateTime lastActiveAt, UUID id) {
        long millis = lastActiveAt.toInstant(ZoneOffset.UTC).toEpochMilli();
        return Base64.getUrlEncoder().withoutPadding().encodeToString((millis + ":" + id).getBytes());
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(null, null);
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor));
            int colon = raw.indexOf(':');
            if (colon <= 0) {
                return new Cursor(null, null);
            }
            long millis = Long.parseLong(raw.substring(0, colon));
            UUID id = UUID.fromString(raw.substring(colon + 1));
            return new Cursor(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC), id);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return new Cursor(null, null);
        }
    }

    private record Cursor(LocalDateTime lastActiveAt, UUID id) {
    }
}
