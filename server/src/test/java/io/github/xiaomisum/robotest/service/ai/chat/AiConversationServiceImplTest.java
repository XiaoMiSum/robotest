package io.github.xiaomisum.robotest.service.ai.chat;

import io.github.xiaomisum.robotest.model.entity.ai.AiConversation;
import io.github.xiaomisum.robotest.repository.ai.AiConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceImplTest {

    @Mock
    private AiConversationMapper conversationMapper;
    @Mock
    private AiMessageMapper messageMapper;
    @InjectMocks
    private AiConversationServiceImpl service;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    @Test
    void appendUserMessage_autoRenameUsesPartialUpdateCarriers() {
        LocalDateTime previousLastActiveAt = LocalDateTime.now().minusDays(1);
        AiConversation existing = conversation("新会话", previousLastActiveAt);
        when(conversationMapper.selectById(conversationId)).thenReturn(existing);
        String content = "1234567890123456789012345678901234567890";

        service.appendUserMessage(conversationId, content);

        ArgumentCaptor<AiConversation> updates = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationMapper, times(2)).updateById(updates.capture());

        AiConversation titleUpdate = updates.getAllValues().get(0);
        assertEquals(conversationId, titleUpdate.getId());
        assertEquals(content.substring(0, 30), titleUpdate.getTitle());
        assertNull(titleUpdate.getUserId());
        assertNull(titleUpdate.getWorkspaceId());
        assertNull(titleUpdate.getLastActiveAt());
        assertNotSame(existing, titleUpdate);

        AiConversation lastActiveUpdate = updates.getAllValues().get(1);
        assertEquals(conversationId, lastActiveUpdate.getId());
        assertNotNull(lastActiveUpdate.getLastActiveAt());
        assertTrue(lastActiveUpdate.getLastActiveAt().isAfter(previousLastActiveAt));
        assertNull(lastActiveUpdate.getTitle());
        assertNull(lastActiveUpdate.getUserId());
        assertNull(lastActiveUpdate.getWorkspaceId());
        assertNotSame(existing, lastActiveUpdate);

        assertEquals("新会话", existing.getTitle());
        assertEquals(previousLastActiveAt, existing.getLastActiveAt());
    }

    @Test
    void appendUserMessage_existingTitleOnlyUpdatesLastActiveAt() {
        LocalDateTime previousLastActiveAt = LocalDateTime.now().minusDays(1);
        AiConversation existing = conversation("已有标题", previousLastActiveAt);
        when(conversationMapper.selectById(conversationId)).thenReturn(existing);

        service.appendUserMessage(conversationId, "后续消息");

        ArgumentCaptor<AiConversation> updates = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationMapper).updateById(updates.capture());
        AiConversation update = updates.getValue();
        assertEquals(conversationId, update.getId());
        assertNotNull(update.getLastActiveAt());
        assertTrue(update.getLastActiveAt().isAfter(previousLastActiveAt));
        assertNull(update.getTitle());
        assertNull(update.getUserId());
        assertNull(update.getWorkspaceId());
        assertNotSame(existing, update);
        assertEquals("已有标题", existing.getTitle());
        assertEquals(previousLastActiveAt, existing.getLastActiveAt());
    }

    @Test
    void appendAssistantMessage_onlyUpdatesLastActiveAt() {
        LocalDateTime previousLastActiveAt = LocalDateTime.now().minusDays(1);
        AiConversation existing = conversation("已有标题", previousLastActiveAt);
        when(conversationMapper.selectById(conversationId)).thenReturn(existing);

        service.appendAssistantMessage(conversationId, "助手回复", List.of());

        assertLastActiveOnlyUpdate(previousLastActiveAt, existing);
    }

    @Test
    void appendToolMessage_onlyUpdatesLastActiveAt() {
        LocalDateTime previousLastActiveAt = LocalDateTime.now().minusDays(1);
        AiConversation existing = conversation("已有标题", previousLastActiveAt);
        when(conversationMapper.selectById(conversationId)).thenReturn(existing);

        service.appendToolMessage(conversationId, "call-1", "工具结果");

        assertLastActiveOnlyUpdate(previousLastActiveAt, existing);
    }

    private void assertLastActiveOnlyUpdate(LocalDateTime previousLastActiveAt, AiConversation existing) {
        ArgumentCaptor<AiConversation> updates = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationMapper).updateById(updates.capture());
        AiConversation update = updates.getValue();
        assertEquals(conversationId, update.getId());
        assertNotNull(update.getLastActiveAt());
        assertTrue(update.getLastActiveAt().isAfter(previousLastActiveAt));
        assertNull(update.getTitle());
        assertNull(update.getUserId());
        assertNull(update.getWorkspaceId());
        assertNotSame(existing, update);
        assertEquals("已有标题", existing.getTitle());
        assertEquals(previousLastActiveAt, existing.getLastActiveAt());
    }

    private AiConversation conversation(String title, LocalDateTime lastActiveAt) {
        AiConversation conversation = new AiConversation();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setWorkspaceId(workspaceId);
        conversation.setTitle(title);
        conversation.setLastActiveAt(lastActiveAt);
        return conversation;
    }
}
