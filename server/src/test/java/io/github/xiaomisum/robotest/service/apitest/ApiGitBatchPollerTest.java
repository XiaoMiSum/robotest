package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 流水线状态轮询作业（WP-6.2，GitLab 集成详设 4.2 第 5/6 步）。
 * <p>核心不变量：扫描驱动无内存态（重启接管）、一批次一次查询、
 * 单批失败不中断整轮、终态只触发钩子不写批次状态（写入归 WP-7.1 经终态闸门）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiGitBatchPollerTest {

    private static final String BASE64_KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiGitRepoMapper gitRepoMapper;
    @Mock
    private GitLabApiClient gitLabApiClient;
    @Mock
    private ApiGitResultCollector resultCollector;

    private final List<String> terminalHits = new ArrayList<>();

    private ApiGitRepo repo;
    private ApiGitBatchPoller poller;

    @BeforeEach
    void setUp() {
        repo = new ApiGitRepo();
        repo.setId(UUID.randomUUID());
        repo.setGitlabBaseUrl("https://gitlab.example.com");
        repo.setRepoPath("qa/order-api-tests");
        repo.setTokenCipher(SecretCryptoUtil.encrypt(
                SecretCryptoUtil.parseKey(BASE64_KEY), "glpat-xxxx"));
        repo.setEnabled(true);

        // 终态钩子以子类捕获：断言「只触发钩子」这一唯一对外行为
        poller = new ApiGitBatchPoller(executionRecordMapper, gitRepoMapper, gitLabApiClient,
                resultCollector, BASE64_KEY) {
            @Override
            void onPipelineTerminal(String batchId, Long pipelineIid, String status) {
                terminalHits.add(batchId + "|" + pipelineIid + "|" + status);
            }
        };
    }

    private ApiExecutionRecord runningRecord(String batchId, long pipelineIid) {
        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setBatchId(batchId);
        record.setRepoId(repo.getId());
        record.setPipelineIid(pipelineIid);
        record.setStatus("running");
        return record;
    }

    private void scanWith(ApiExecutionRecord... records) {
        when(executionRecordMapper.selectPollableRecords()).thenReturn(List.of(records));
        lenient().when(gitRepoMapper.selectById(any())).thenReturn(repo);
    }

    @Test
    void emptyScanIsNoOp() {
        when(executionRecordMapper.selectPollableRecords()).thenReturn(List.of());

        poller.pollAll();

        verifyNoInteractions(gitLabApiClient, gitRepoMapper);
        verify(executionRecordMapper, never()).markBatchTerminal(anyString(), anyString(), any());
    }

    @Test
    void pollsEachBatchOnceAndNonTerminalContinuesNextRound() {
        scanWith(runningRecord("batch-a", 1L), runningRecord("batch-a", 2L), runningRecord("batch-b", 3L));
        when(gitLabApiClient.getPipeline(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new GitLabApiClient.PipelineStatus(1L, "url", "running"));

        poller.pollAll();

        // 同批多条记录只查询一次：batch-a(1) + batch-b(1) = 2 次
        verify(gitLabApiClient, times(2)).getPipeline(anyString(), anyString(), anyString(), anyLong());
        assertTrue(terminalHits.isEmpty());
        verify(executionRecordMapper, never()).markBatchTerminal(anyString(), anyString(), any());
    }

    @Test
    void terminalStatusInvokesHookButWritesNothing() {
        scanWith(runningRecord("batch-a", 7L));
        when(gitLabApiClient.getPipeline(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new GitLabApiClient.PipelineStatus(7L, "url", "success"));

        poller.pollAll();

        assertEquals(List.of("batch-a|7|success"), terminalHits);
        // 结果回收与终态落库归 WP-7.1：轮询兜底通道在 6.2 不写批次状态
        verify(executionRecordMapper, never()).markBatchTerminal(anyString(), anyString(), any());
    }

    @Test
    void queryFailureDoesNotAbortRound() {
        scanWith(runningRecord("batch-a", 1L), runningRecord("batch-b", 2L));
        when(gitLabApiClient.getPipeline(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenThrow(ServiceExceptionUtil.get(1000017705, "HTTP 500"));
        when(gitLabApiClient.getPipeline(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq(2L)))
                .thenReturn(new GitLabApiClient.PipelineStatus(2L, "url", "running"));

        poller.pollAll();

        // 单批失败（1000017705）留痕重试，后续批次照常轮询
        verify(gitLabApiClient, times(1))
                .getPipeline(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq(2L));
        verify(executionRecordMapper, never()).markBatchTerminal(anyString(), anyString(), any());
    }

    @Test
    void missingRepoSkipsBatchAndContinues() {
        ApiExecutionRecord orphan = runningRecord("batch-a", 1L);
        orphan.setRepoId(UUID.randomUUID());
        scanWith(orphan, runningRecord("batch-b", 2L));
        when(gitRepoMapper.selectById(orphan.getRepoId())).thenReturn(null);
        when(gitLabApiClient.getPipeline(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new GitLabApiClient.PipelineStatus(2L, "url", "running"));

        poller.pollAll();

        verify(gitLabApiClient, times(1))
                .getPipeline(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq(2L));
    }

    @Test
    void undecryptableTokenSkipsBatch() {
        ApiGitBatchPoller broken = new ApiGitBatchPoller(
                executionRecordMapper, gitRepoMapper, gitLabApiClient, resultCollector, "") {
            @Override
            void onPipelineTerminal(String batchId, Long pipelineIid, String status) {
                terminalHits.add(batchId);
            }
        };
        scanWith(runningRecord("batch-a", 1L));

        broken.pollAll();

        // 空密钥解密失败按不可达留痕重试，不抛出、不写库
        verify(gitLabApiClient, never()).getPipeline(anyString(), anyString(), anyString(), anyLong());
        verify(executionRecordMapper, never()).markBatchTerminal(anyString(), anyString(), any());
    }

    @Test
    void terminalStatusHandsOffToCollector() {
        ApiGitBatchPoller real = new ApiGitBatchPoller(executionRecordMapper, gitRepoMapper,
                gitLabApiClient, resultCollector, BASE64_KEY);
        scanWith(runningRecord("batch-a", 7L));
        when(gitLabApiClient.getPipeline(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new GitLabApiClient.PipelineStatus(7L, "url", "failed"));

        real.pollAll();

        // WP-7.1 接线：终态只触发回收入口，状态写入由回收经终态闸门落库
        verify(resultCollector).collectAsync("batch-a", ApiGitResultCollector.Channel.POLL, "failed");
        verify(executionRecordMapper, never()).markBatchTerminal(anyString(), anyString(), any());
    }

    @Test
    void scanFailureSkipsRoundGracefully() {
        when(executionRecordMapper.selectPollableRecords()).thenThrow(new RuntimeException("db down"));

        poller.pollAll();

        verifyNoInteractions(gitLabApiClient, gitRepoMapper);
    }
}
