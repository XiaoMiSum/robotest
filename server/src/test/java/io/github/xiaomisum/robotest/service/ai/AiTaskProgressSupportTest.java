package io.github.xiaomisum.robotest.service.ai;


import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.service.ai.support.AiTaskProgressSupport;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiTaskProgressSupport 单测：percent 边界（总数为 0 不除零、四舍五入），
 * heartbeat 委托 mapper.updateProgressIfRunning 并原样透传影响行数。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskProgressSupportTest {

    @Mock
    private AiAnalysisTaskMapper aiTaskMapper;

    @Test
    void percent_totalZero_returnsZero() {
        assertEquals(0, AiTaskProgressSupport.percent(5, 0));
        assertEquals(0, AiTaskProgressSupport.percent(0, 0));
    }

    @Test
    void percent_roundsHalfUp() {
        assertEquals(50, AiTaskProgressSupport.percent(1, 2));
        assertEquals(33, AiTaskProgressSupport.percent(1, 3));
        assertEquals(67, AiTaskProgressSupport.percent(2, 3));
        assertEquals(100, AiTaskProgressSupport.percent(3, 3));
    }

    @Test
    void heartbeat_delegatesAndReturnsAffectedRows() {
        UUID taskId = UUID.randomUUID();
        when(aiTaskMapper.updateProgressIfRunning(taskId, 42, "{\"done\":1}")).thenReturn(1);
        assertEquals(1, AiTaskProgressSupport.heartbeat(aiTaskMapper, taskId, 42, "{\"done\":1}"));
        verify(aiTaskMapper).updateProgressIfRunning(taskId, 42, "{\"done\":1}");
    }

    @Test
    void heartbeat_zeroMeansCancelled() {
        UUID taskId = UUID.randomUUID();
        when(aiTaskMapper.updateProgressIfRunning(taskId, 0, null)).thenReturn(0);
        assertEquals(0, AiTaskProgressSupport.heartbeat(aiTaskMapper, taskId, 0, null));
    }
}
