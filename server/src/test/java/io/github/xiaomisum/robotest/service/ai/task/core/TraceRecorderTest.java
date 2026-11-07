package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTraceEvent;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskTraceEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 轨迹记录单测（总册 2.6.3）：seq 按执行内 max+1 分配、只增不改、非法事件类型拒绝（C8）。
 */
@ExtendWith(MockitoExtension.class)
class TraceRecorderTest {

    private static final UUID EXECUTION_ID = UUID.randomUUID();

    @Mock
    private AiTaskTraceEventMapper traceEventMapper;
    @Mock
    private AiTaskExecutionMapper executionMapper;

    @InjectMocks
    private TraceRecorder recorder;

    @Test
    void record_firstEvent_allocatesSeqOne() {
        when(executionMapper.selectByIdForUpdate(EXECUTION_ID)).thenReturn(new AiTaskExecution());
        when(traceEventMapper.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(null);

        int seq = recorder.record(EXECUTION_ID, "plan", "执行计划", Map.of("steps", 1));

        assertEquals(1, seq);
        ArgumentCaptor<AiTaskTraceEvent> captor = ArgumentCaptor.forClass(AiTaskTraceEvent.class);
        verify(traceEventMapper).insert(captor.capture());
        AiTaskTraceEvent event = captor.getValue();
        assertEquals(EXECUTION_ID, event.getExecutionId());
        assertEquals(1, event.getSeq());
        assertEquals("plan", event.getEventType());
        assertEquals("执行计划", event.getSummary());
        assertEquals(Map.of("steps", 1), event.getDetail());
        InOrder order = inOrder(executionMapper, traceEventMapper);
        order.verify(executionMapper).selectByIdForUpdate(EXECUTION_ID);
        order.verify(traceEventMapper).selectOne(any(LambdaQueryWrapperX.class));
        order.verify(traceEventMapper).insert(any(AiTaskTraceEvent.class));
    }

    @Test
    void record_appendsAfterMaxSeq() {
        when(executionMapper.selectByIdForUpdate(EXECUTION_ID)).thenReturn(new AiTaskExecution());
        AiTaskTraceEvent last = new AiTaskTraceEvent();
        last.setSeq(5);
        when(traceEventMapper.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(last);

        assertEquals(6, recorder.record(EXECUTION_ID, "tool_call", "调用工具", null));
    }

    @Test
    void record_neverMutatesOrDeletes_existingEvents() {
        when(executionMapper.selectByIdForUpdate(EXECUTION_ID)).thenReturn(new AiTaskExecution());
        when(traceEventMapper.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(null);

        recorder.record(EXECUTION_ID, "status_change", "状态流转", Map.of());

        verify(traceEventMapper, never()).update(any(), any());
        verify(traceEventMapper, never()).updateById(any(AiTaskTraceEvent.class));
        verify(traceEventMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void record_summaryTruncatedToColumnLimit() {
        when(executionMapper.selectByIdForUpdate(EXECUTION_ID)).thenReturn(new AiTaskExecution());
        when(traceEventMapper.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(null);

        recorder.record(EXECUTION_ID, "tool_result", "x".repeat(600), null);

        ArgumentCaptor<AiTaskTraceEvent> captor = ArgumentCaptor.forClass(AiTaskTraceEvent.class);
        verify(traceEventMapper).insert(captor.capture());
        assertTrue(captor.getValue().getSummary().length() <= 500);
    }

    @Test
    void record_unknownEventType_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> recorder.record(EXECUTION_ID, "not_a_type", "摘要", null));
        verify(traceEventMapper, never()).insert(any(AiTaskTraceEvent.class));
    }

    @Test
    void recordIfActive_terminalExecution_rejectsLateOrdinaryEvent() {
        AiTaskExecution execution = new AiTaskExecution();
        execution.setStatus("aborted");
        when(executionMapper.selectByIdForUpdate(EXECUTION_ID)).thenReturn(execution);

        assertFalse(recorder.recordIfActive(EXECUTION_ID, "tool_result", "迟到结果", Map.of()));

        verify(traceEventMapper, never()).selectOne(any(LambdaQueryWrapperX.class));
        verify(traceEventMapper, never()).insert(any(AiTaskTraceEvent.class));
    }
}
