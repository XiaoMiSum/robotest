package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTaskSweeperTest {

    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private AiSettingsReader settingsReader;

    @InjectMocks
    private AiTaskSweeper sweeper;

    @Test
    void sweepTimeoutTasks_marksStaleTasksFailedWithTimeoutError() {
        when(settingsReader.settings()).thenReturn(new AiSettingsReader.AiSettings(true, null, 600, 2));
        LocalDateTime beforeSweep = LocalDateTime.now();

        sweeper.sweepTimeoutTasks();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskMapper).failStale(cutoffCaptor.capture(),
                eq(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code()), eq("任务执行超时"));
        LocalDateTime cutoff = cutoffCaptor.getValue();
        // 截止时间 = 当前时间 - task_timeout_seconds（600s）
        assertTrue(cutoff.isBefore(beforeSweep.minusSeconds(599)));
        assertTrue(cutoff.isAfter(beforeSweep.minusSeconds(601)));
    }
}
