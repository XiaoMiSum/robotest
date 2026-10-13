package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitBatchManifestRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 外部执行批次实现（GitLab 集成详细设计 4.2）。
 * <p>
 * 批次载体即 api_execution_record：同 batchId 的 N 条 pending 记录构成冻结快照，
 * 触发成功后整批回写 pipeline_iid / pipeline_url 并置 running，CI 侧按 batchId 取清单。
 * 批次票据为随机 UUID（36 字符），天然不可猜，读取端不再校验项目归属（CI 无项目上下文）。
 */
@Service
public class ApiGitBatchServiceImpl implements ApiGitBatchService {

    /** 执行记录终态集合（引擎详设 3.1 状态机 + 详设 4.2 第 5 步终态去重的写入白名单） */
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("success", "failed", "error", "cancelled", "timeout");

    private final ApiExecutionRecordMapper executionRecordMapper;
    private final ApiGitRepoMapper gitRepoMapper;
    private final GitLabApiClient gitLabApiClient;
    private final byte[] secretKey;

    public ApiGitBatchServiceImpl(ApiExecutionRecordMapper executionRecordMapper, ApiGitRepoMapper gitRepoMapper,
            GitLabApiClient gitLabApiClient,
            @Value("${robotest.env.secret-key:}") String base64SecretKey) {
        this.executionRecordMapper = executionRecordMapper;
        this.gitRepoMapper = gitRepoMapper;
        this.gitLabApiClient = gitLabApiClient;
        this.secretKey = SecretCryptoUtil.parseKey(base64SecretKey);
    }

    // ========== 触发 ==========
    @Override
    public Result trigger(ApiGitRepo repo, List<ApiScene> scenes, UUID scheduledTaskId,
            String triggerType, String source) {
        if (scenes == null || scenes.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "批次场景清单为空");
        }
        String batchId = UUID.randomUUID().toString();
        // 顺序硬约束（详设 4.2 第 2 步）：快照全部落库后才允许触发
        freeze(batchId, repo, scenes, scheduledTaskId, triggerType, source);

        String branch = repo.getDefaultBranch() == null || repo.getDefaultBranch().isBlank()
                ? "master" : repo.getDefaultBranch();
        try {
            GitLabApiClient.PipelineRef pipeline = gitLabApiClient.triggerPipeline(
                    repo.getGitlabBaseUrl(), decryptToken(repo), repo.getRepoPath(), branch, batchId);
            executionRecordMapper.markBatchRunning(batchId, pipeline.iid(), pipeline.webUrl());
        } catch (ServiceException ex) {
            markBatchError(batchId, ex);
            // 网络不可达按 BR-1.26 映射为仓库不可达（1000017707），GitLab 拒绝触发（1000017704）原样透出
            if (ex.getCode() == ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code()) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_SCENE_NOT_EXECUTABLE);
            }
            throw ex;
        }
        UUID firstRecordId = executionRecordMapper.selectByBatchId(batchId).stream()
                .findFirst().map(ApiExecutionRecord::getId).orElse(null);
        return new Result(batchId, firstRecordId);
    }

    /** 冻结：逐场景落 pending 执行记录（含批次票据与源侧引用），单条失败即整体失败 */
    private void freeze(String batchId, ApiGitRepo repo, List<ApiScene> scenes, UUID scheduledTaskId,
            String triggerType, String source) {
        for (ApiScene scene : scenes) {
            ApiExecutionRecord record = new ApiExecutionRecord();
            record.setId(UUID.randomUUID());
            record.setProjectId(scene.getProjectId());
            record.setSceneId(scene.getId());
            record.setExecutionMode("platform");
            record.setStatus("pending");
            record.setTriggerType(triggerType);
            record.setSource(source);
            record.setExecutedAt(LocalDateTime.now());
            record.setRepoId(repo.getId());
            record.setBatchId(batchId);
            record.setSourceRef(scene.getSourceRef());
            record.setCommitSha(repo.getHeadCommit());
            record.setScheduledTaskId(scheduledTaskId);
            executionRecordMapper.insert(record);
        }
    }

    private void markBatchError(String batchId, ServiceException ex) {
        String message = ex.getMessage();
        if (message != null && message.length() > 2000) {
            message = message.substring(0, 2000);
        }
        executionRecordMapper.markBatchError(batchId, message);
    }

    // ========== 取清单 ==========
    @Override
    public ApiGitBatchManifestRespDTO getManifest(String batchId) {
        List<ApiExecutionRecord> records = executionRecordMapper.selectByBatchId(batchId);
        if (records.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_BATCH_NOT_FOUND);
        }
        ApiGitRepo repo = gitRepoMapper.selectById(records.get(0).getRepoId());
        ApiGitBatchManifestRespDTO resp = new ApiGitBatchManifestRespDTO();
        resp.setBatchId(batchId);
        resp.setRef(repo == null || repo.getDefaultBranch() == null || repo.getDefaultBranch().isBlank()
                ? "master" : repo.getDefaultBranch());
        // gitlab_ci 场景与测试方法一一对应（BR-1.43），期望用例数恒为 1，派生不落库
        resp.setScenes(records.stream()
                .map(r -> new ApiGitBatchManifestRespDTO.SceneItem(
                        r.getSceneId().toString(), r.getSourceRef(), 1))
                .toList());
        resp.setExpectedCases(records.size());
        return resp;
    }

    // ========== 终态去重 ==========
    @Override
    public boolean finishBatch(String batchId, String status, String errorMessage, Map<String, Object> summary) {
        if (!TERMINAL_STATUSES.contains(status)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "非法终态取值：" + status);
        }
        return executionRecordMapper.markBatchTerminal(batchId, status, errorMessage, summary) > 0;
    }

    /** 沿用已存令牌：解密失败（密钥轮换 / 密文损坏）须重新配置令牌后重试 */
    private String decryptToken(ApiGitRepo repo) {
        String plain = secretKey == null ? null : SecretCryptoUtil.decrypt(secretKey, repo.getTokenCipher());
        if (plain == null || plain.isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(),
                    "已存访问令牌无法解密，请重新配置令牌");
        }
        return plain;
    }
}
