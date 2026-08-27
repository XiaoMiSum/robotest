package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 报告保留期清理（定时任务详细设计 4.4）：每日 03:00 一步物理删除超期数据 */
@ExtendWith(MockitoExtension.class)
class ApiTestRetentionCleanerTest {

    @Mock
    private ApiReportMapper reportMapper;
    @Mock
    private ApiScheduledTaskExecutionMapper executionMapper;

    @InjectMocks
    private ApiTestRetentionCleaner cleaner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cleaner, "retentionDays", 90);
    }

    @Test
    void cleanDeletesReportsAndExecutionsOlderThanRetentionWindow() {
        when(reportMapper.deletePhysicallyOlderThan(any())).thenReturn(5);
        when(executionMapper.deletePhysicallyOlderThan(any())).thenReturn(3);

        cleaner.clean();

        LocalDateTime before = LocalDateTime.now().minusDays(91);
        LocalDateTime after = LocalDateTime.now().minusDays(89);

        ArgumentCaptor<LocalDateTime> reportCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reportMapper).deletePhysicallyOlderThan(reportCutoff.capture());
        ArgumentCaptor<LocalDateTime> executionCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(executionMapper).deletePhysicallyOlderThan(executionCutoff.capture());

        assertTrue(reportCutoff.getValue().isBefore(after));
        assertTrue(reportCutoff.getValue().isAfter(before));
        assertEquals(reportCutoff.getValue(), executionCutoff.getValue());
    }
}
