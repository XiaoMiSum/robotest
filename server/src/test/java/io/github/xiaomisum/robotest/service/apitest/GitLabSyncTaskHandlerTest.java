package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GitLab 同步任务执行器测试：无变更跳过、摘要落库、手动触发更新预插入记录与执行期异常留痕（定时任务详设 27 §4）
 */
@ExtendWith(MockitoExtension.class)
class GitLabSyncTaskHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock
    private ApiGitImportService apiGitImportService;
    @Mock
    private ApiScheduledTaskMapper taskMapper;
    @Mock
    private ApiScheduledTaskExecutionMapper executionMapper;

    @InjectMocks
    private GitLabSyncTaskHandler handler;

    private ApiScheduledTask task;
    private UUID userId;

    @BeforeEach
    void setUp() {
        task = new ApiScheduledTask();
        task.setId(UUID.randomUUID());
        task.setProjectId(PROJECT_ID);
        task.setTaskType("gitlab_sync");
        task.setName("每日 GitLab 同步");
        userId = UUID.randomUUID();
    }

    @Test
    void noChangeWritesSuccessRecordWithSkippedCount() {
        when(apiGitImportService.syncImport(PROJECT_ID, userId))
                .thenReturn(new ApiGitImportService.SyncOutcome(true, "head1", noChangeSummary()));

        handler.executeTask(task, userId, "scheduled", LocalDateTime.now(), null);

        ApiScheduledTaskExecution record = captureInsertedRecord();
        assertEquals("success", record.getStatus());
        assertEquals(GitLabSyncTaskHandler.NO_CHANGE_MESSAGE, record.getErrorMessage());
        assertNotNull(record.getSyncSummary());
        assertEquals(1, record.getSyncSummary().get("skipped"));
        assertEquals("head1", record.getSyncSummary().get("commit"));
        assertTaskStatuses("running", "success");
    }

    @Test
    void normalSyncWritesSummaryWithCommitAndNoError() {
        when(apiGitImportService.syncImport(PROJECT_ID, userId))
                .thenReturn(new ApiGitImportService.SyncOutcome(false, "head2", importedSummary()));

        handler.executeTask(task, userId, "scheduled", LocalDateTime.now(), null);

        ApiScheduledTaskExecution record = captureInsertedRecord();
        assertEquals("success", record.getStatus());
        assertNull(record.getErrorMessage());
        assertEquals(3, record.getSyncSummary().get("created"));
        assertEquals(2, record.getSyncSummary().get("updated"));
        assertEquals("head2", record.getSyncSummary().get("commit"));
        assertTaskStatuses("running", "success");
    }

    @Test
    void manualTriggerUpdatesPreInsertedRecordInsteadOfInsert() {
        UUID preInserted = UUID.randomUUID();
        when(apiGitImportService.syncImport(PROJECT_ID, userId))
                .thenReturn(new ApiGitImportService.SyncOutcome(false, "head3", importedSummary()));

        handler.executeTask(task, userId, "manual", LocalDateTime.now(), preInserted);

        ArgumentCaptor<ApiScheduledTaskExecution> captor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).updateById(captor.capture());
        verify(executionMapper, never()).insert(any(ApiScheduledTaskExecution.class));
        ApiScheduledTaskExecution carrier = captor.getValue();
        assertEquals(preInserted, carrier.getId());
        assertEquals("success", carrier.getStatus());
        assertEquals("head3", carrier.getSyncSummary().get("commit"));
        assertNotNull(carrier.getDurationMs());
        // 预插入记录已带 triggerType / triggeredAt，完成载体只写终态字段（C11 部分更新）
        assertNull(carrier.getTriggerType());
        assertNull(carrier.getTriggeredAt());
        assertTaskStatuses("running", "success");
    }

    @Test
    void executionFailureWritesFailedRecordAndDoesNotRethrow() {
        when(apiGitImportService.syncImport(PROJECT_ID, userId))
                .thenThrow(new IllegalStateException("GitLab 不可达"));

        handler.executeTask(task, userId, "scheduled", LocalDateTime.now(), null);

        ApiScheduledTaskExecution record = captureInsertedRecord();
        assertEquals("failed", record.getStatus());
        assertEquals("GitLab 不可达", record.getErrorMessage());
        assertNull(record.getSyncSummary());
        assertTaskStatuses("running", "failed");
    }

    @Test
    void failureTruncatesLongErrorMessageToColumnLimit() {
        when(apiGitImportService.syncImport(PROJECT_ID, userId))
                .thenThrow(new IllegalStateException("x".repeat(2500)));

        handler.executeTask(task, userId, "scheduled", LocalDateTime.now(), null);

        ApiScheduledTaskExecution record = captureInsertedRecord();
        assertEquals("failed", record.getStatus());
        assertEquals(2000, record.getErrorMessage().length());
    }

    private ApiScheduledTaskExecution captureInsertedRecord() {
        ArgumentCaptor<ApiScheduledTaskExecution> captor =
                ArgumentCaptor.forClass(ApiScheduledTaskExecution.class);
        verify(executionMapper).insert(captor.capture());
        return captor.getValue();
    }

    private void assertTaskStatuses(String... expected) {
        ArgumentCaptor<ApiScheduledTask> captor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper, times(expected.length)).updateById(captor.capture());
        List<String> statuses = captor.getAllValues().stream()
                .map(ApiScheduledTask::getLastExecutionStatus)
                .toList();
        assertEquals(List.of(expected), statuses);
    }

    private static Map<String, Object> noChangeSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("created", 0);
        summary.put("updated", 0);
        summary.put("parseFailed", 0);
        summary.put("errorFailed", 0);
        summary.put("skipped", 1);
        return summary;
    }

    private static Map<String, Object> importedSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("created", 3);
        summary.put("updated", 2);
        summary.put("parseFailed", 1);
        summary.put("errorFailed", 0);
        summary.put("skipped", 0);
        return summary;
    }
}
