package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/**
 * tool 消息回填写入单测（详设 4.1 查询类能力）：role=tool 载体落盘形态（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantToolMessageWriterTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @Mock
    private AiAssistantMessageMapper messageMapper;

    @InjectMocks
    private AssistantToolMessageWriter writer;

    @Test
    void appendToolMessage_persistsToolCarrierForBackfill() {
        writer.appendToolMessage(CONVERSATION_ID, "tc-9", "{\"count\":1}");

        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).insert(captor.capture());
        AiAssistantMessage inserted = captor.getValue();
        assertEquals(CONVERSATION_ID, inserted.getConversationId());
        assertEquals(Constants.AiAssistantMessageRole.TOOL, inserted.getRole());
        assertEquals("tc-9", inserted.getToolCallId());
        assertEquals("{\"count\":1}", inserted.getContent());
        assertEquals(Constants.AiAssistantMessageStatus.DONE, inserted.getStatus());
    }
}
