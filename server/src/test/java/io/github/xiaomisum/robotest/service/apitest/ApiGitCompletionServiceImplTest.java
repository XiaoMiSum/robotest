package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitBatchCompletionReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CI 回报批次完成测试（GitLab 集成详细设计 4.6 completion 端点）。
 */
@ExtendWith(MockitoExtension.class)
class ApiGitCompletionServiceImplTest {

    private static final String BATCH = "batch-1";

    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;

    @Mock
    private ApiGitResultCollector resultCollector;

    @InjectMocks
    private ApiGitCompletionServiceImpl service;

    @Test
    void unknownBatchThrowsNotFound() {
        when(executionRecordMapper.selectByBatchId(BATCH)).thenReturn(List.of());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.reportCompletion(BATCH, completion("success", 10, 9)));

        assertEquals(1000017713, ex.getCode());
        verifyNoInteractions(resultCollector);
    }

    @Test
    void invalidStatusRejected() {
        when(executionRecordMapper.selectByBatchId(BATCH)).thenReturn(List.of(record("running", null)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.reportCompletion(BATCH, completion("canceled", 10, 9)));

        assertEquals(1000001001, ex.getCode());
        verify(executionRecordMapper, never()).updateSummaryIfRunning(any(), anyMap());
        verifyNoInteractions(resultCollector);
    }

    @Test
    void reportMergesCiCountsAndTriggersCompletionChannel() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("expectedCases", 10);
        ApiExecutionRecord running = record("running", existing);
        ApiExecutionRecord pending = record("pending", null);
        when(executionRecordMapper.selectByBatchId(BATCH)).thenReturn(List.of(running, pending));

        service.reportCompletion(BATCH, completion("success", 10, 9));

        // CI 自报计数以 ci 前缀合入，平台既有键保留、互不覆盖
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> merged = ArgumentCaptor.forClass(Map.class);
        verify(executionRecordMapper).updateSummaryIfRunning(eq(running.getId()), merged.capture());
        assertEquals(10, merged.getValue().get("expectedCases"));
        assertEquals("success", merged.getValue().get("ciStatus"));
        assertEquals(10, merged.getValue().get("ciExpectedCases"));
        assertEquals(9, merged.getValue().get("ciActualCases"));
        assertEquals("http://gitlab.example/p/1", merged.getValue().get("ciPipelineUrl"));
        verify(executionRecordMapper).updateSummaryIfRunning(eq(pending.getId()), anyMap());
        verify(resultCollector).collectAsync(BATCH, ApiGitResultCollector.Channel.COMPLETION, "success");
    }

    @Test
    void terminalBatchIgnoresWriteButStillCollects() {
        when(executionRecordMapper.selectByBatchId(BATCH))
                .thenReturn(List.of(record("failed", null), record("success", null)));
        ApiGitBatchCompletionReqDTO requestDTO = completion("failed", 10, 8);
        requestDTO.setPipelineUrl(null);

        service.reportCompletion(BATCH, requestDTO);

        // 终态去重（BR-1.24）：写入被忽略；回收仍触发，由单飞与逐条条件终态兜底
        verify(executionRecordMapper, never()).updateSummaryIfRunning(any(), anyMap());
        verify(resultCollector).collectAsync(BATCH, ApiGitResultCollector.Channel.COMPLETION, "failed");
    }

    private static ApiGitBatchCompletionReqDTO completion(String status, Integer expected, Integer actual) {
        ApiGitBatchCompletionReqDTO dto = new ApiGitBatchCompletionReqDTO();
        dto.setStatus(status);
        dto.setExpectedCases(expected);
        dto.setActualCases(actual);
        dto.setPipelineUrl("http://gitlab.example/p/1");
        return dto;
    }

    private static ApiExecutionRecord record(String status, Map<String, Object> summary) {
        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setStatus(status);
        record.setSummary(summary);
        return record;
    }
}
