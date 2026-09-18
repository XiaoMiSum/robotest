package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.domain.bug.BugChangeOp;
import io.github.xiaomisum.robotest.service.domain.bug.BugChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingEventConsumerTest {

    @Mock
    private BugMapper bugMapper;
    @Mock
    private AiEmbeddingWriteService aiEmbeddingWriteService;

    @InjectMocks
    private EmbeddingEventConsumer consumer;

    private final UUID bugId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Bug bug(String status) {
        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setStatus(status);
        return bug;
    }

    @Test
    void onBugChanged_created_passesRequeriedBug() {
        Bug bug = bug("active");
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        consumer.onBugChanged(new BugChangedEvent(bugId, BugChangeOp.CREATED));

        verify(bugMapper).selectById(bugId);
        verify(aiEmbeddingWriteService).handleBugChanged(bug);
    }

    @Test
    void onBugChanged_updated_passesRequeriedBug() {
        Bug bug = bug("active");
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        consumer.onBugChanged(new BugChangedEvent(bugId, BugChangeOp.UPDATED));

        verify(bugMapper).selectById(bugId);
        verify(aiEmbeddingWriteService).handleBugChanged(bug);
    }

    @Test
    void onBugChanged_closed_passesClosedRowForVectorDelete() {
        // 重查到的 CLOSED 行完整传给 handleBugChanged，由其按状态分支删向量（05 §3.1.2 语义一字不差）
        when(bugMapper.selectById(bugId)).thenReturn(bug("closed"));

        consumer.onBugChanged(new BugChangedEvent(bugId, BugChangeOp.CLOSED));

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(aiEmbeddingWriteService).handleBugChanged(captor.capture());
        assertEquals("closed", captor.getValue().getStatus());
    }

    @Test
    void onBugChanged_bugGone_skipsWrite() {
        when(bugMapper.selectById(bugId)).thenReturn(null);

        consumer.onBugChanged(new BugChangedEvent(bugId, BugChangeOp.UPDATED));

        verify(bugMapper).selectById(bugId);
        verify(aiEmbeddingWriteService, never()).handleBugChanged(any());
    }
}