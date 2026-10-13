package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 流水线状态轮询作业（GitLab 集成详细设计 4.2 第 5/6 步，WP-6.2）：
 * CI 主动回报为主路径，本作业是兜底通道——CI 崩溃、网络中断、流水线被手动取消都会导致回报丢失。
 *
 * <p>无内存态：每轮从库中扫 {@code status='running' AND pipeline_iid IS NOT NULL} 的批次记录，
 * 进程重启后首轮扫描即自然接管未完成批次（BR-1.29），按库中状态恢复、不依赖任何进程内注册。
 *
 * <p>一批次一条流水线（详设 4.2 第 1 步），同批多条记录只查询一次；同仓库令牌解密一次复用。
 * 查询失败（1000017705）留痕后下周期自然重试，单批失败不中断整轮（仿源探查口径）。
 *
 * <p>轮询到流水线终态时只触发 {@link #onPipelineTerminal} 钩子、不写批次状态：结果回收
 * （制品下载、BR-1.34 先判有无用例报告）归 WP-7.1，由其经终态闸门
 * {@code ApiGitBatchService.finishBatch} 写入；本作业抢写终态会让真实结果被去重挡掉。
 */
@Slf4j
@Component
public class ApiGitBatchPoller {

    /** GitLab 流水线终态（详设 4.2 第 5 步）：其余状态均视为未达终态，下周期继续轮询 */
    private static final Set<String> PIPELINE_TERMINAL_STATUSES = Set.of("success", "failed", "canceled");

    private final ApiExecutionRecordMapper executionRecordMapper;
    private final ApiGitRepoMapper gitRepoMapper;
    private final GitLabApiClient gitLabApiClient;
    private final byte[] secretKey;

    public ApiGitBatchPoller(ApiExecutionRecordMapper executionRecordMapper, ApiGitRepoMapper gitRepoMapper,
            GitLabApiClient gitLabApiClient,
            @Value("${robotest.env.secret-key:}") String base64SecretKey) {
        this.executionRecordMapper = executionRecordMapper;
        this.gitRepoMapper = gitRepoMapper;
        this.gitLabApiClient = gitLabApiClient;
        this.secretKey = SecretCryptoUtil.parseKey(base64SecretKey);
    }

    @Scheduled(fixedDelayString = "${robotest.gitlab.pipeline-poll-interval-ms:30000}",
            initialDelayString = "${robotest.gitlab.pipeline-poll-interval-ms:30000}")
    public void pollAll() {
        List<ApiExecutionRecord> records;
        try {
            records = executionRecordMapper.selectPollableRecords();
        } catch (Exception e) {
            log.warn("[批次轮询] 扫描执行记录失败，本轮跳过：{}", e.getMessage());
            return;
        }
        if (records.isEmpty()) {
            return;
        }
        // 按批次分组：一批次一条流水线，每批只查询一次
        Map<String, List<ApiExecutionRecord>> byBatch = records.stream()
                .filter(r -> r.getBatchId() != null && r.getPipelineIid() != null)
                .collect(Collectors.groupingBy(ApiExecutionRecord::getBatchId,
                        LinkedHashMap::new, Collectors.toList()));
        // 仓库查询与令牌解密按仓库缓存：同仓库多批次只做一次
        Map<UUID, ApiGitRepo> repoCache = new HashMap<>();
        for (Map.Entry<String, List<ApiExecutionRecord>> entry : byBatch.entrySet()) {
            try {
                pollOne(entry.getKey(), entry.getValue().get(0), repoCache);
            } catch (Exception e) {
                log.warn("[批次轮询] 批次查询异常，下周期重试 batchId={}：{}",
                        entry.getKey(), e.getMessage());
            }
        }
    }

    private void pollOne(String batchId, ApiExecutionRecord head, Map<UUID, ApiGitRepo> repoCache) {
        ApiGitRepo repo = head.getRepoId() == null ? null
                : repoCache.computeIfAbsent(head.getRepoId(), gitRepoMapper::selectById);
        if (repo == null) {
            log.warn("[批次轮询] 仓库配置已删除，无从查询流水线，下周期重试 batchId={}", batchId);
            return;
        }
        String token;
        try {
            token = decryptToken(repo);
        } catch (ServiceException e) {
            // 令牌不可解密按不可达留痕重试：用户重新配置令牌后下周期自愈，不提前终态化批次
            log.warn("[批次轮询] 令牌不可解密，下周期重试 batchId={}：{}", batchId, e.getMessage());
            return;
        }
        GitLabApiClient.PipelineStatus status = gitLabApiClient.getPipeline(
                repo.getGitlabBaseUrl(), token, repo.getRepoPath(), head.getPipelineIid());
        if (PIPELINE_TERMINAL_STATUSES.contains(status.status())) {
            onPipelineTerminal(batchId, head.getPipelineIid(), status.status());
        }
    }

    /**
     * 流水线终态钩子：WP-6.2 只留痕，不写批次状态。
     * <p>WP-7.1 在此接入结果回收（下载 artifact → 解析信封 → 组装报告），
     * 收尾时经 {@code ApiGitBatchService.finishBatch} 终态闸门写入，两通道去重共用同一入口。
     */
    void onPipelineTerminal(String batchId, Long pipelineIid, String status) {
        log.info("[批次轮询] 流水线达终态，待结果回收接管 batchId={} pipelineIid={} status={}",
                batchId, pipelineIid, status);
    }

    /** 沿用已存令牌：解密失败（密钥轮换 / 密文损坏）按不可达处理，禁止明文入日志 */
    private String decryptToken(ApiGitRepo repo) {
        String plain = secretKey == null ? null : SecretCryptoUtil.decrypt(secretKey, repo.getTokenCipher());
        if (plain == null || plain.isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(),
                    "已存访问令牌无法解密");
        }
        return plain;
    }
}
