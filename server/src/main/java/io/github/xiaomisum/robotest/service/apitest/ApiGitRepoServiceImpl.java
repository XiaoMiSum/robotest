package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitRepoConnectionTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitRepoCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitRepoUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitRepoConnectionTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitRepoRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.security.core.annotation.AuditLog;

import java.util.Set;
import java.util.UUID;

/**
 * 代码仓库配置实现（GitLab 集成详细设计 3.1）。
 * <p>
 * 一项目一仓库；创建 / 更新一律强制实测连通性，失败不落库；
 * 令牌 AES-256-GCM 加密存储、只进不出（BR-1.3）。
 */
@Service
public class ApiGitRepoServiceImpl implements ApiGitRepoService {

    private static final Set<String> IMPORT_MODES = Set.of("platform", "gitlab_ci");
    private static final Set<String> LOCKED_FIELDS = Set.of("repoPath", "defaultBranch", "importMode");
    private static final String DEFAULT_BRANCH = "master";
    private static final String DEFAULT_TEST_SOURCE_DIR = "src/test/java";
    private static final String DEFAULT_RESOURCE_DIR = "src/test/resources";
    private static final String DEFAULT_ENV_FILE_PATH = "common/env/env.yaml";
    private static final String SOURCE_STATUS_REPO_REMOVED = "repo_removed";

    @Resource
    private ApiGitRepoMapper gitRepoMapper;
    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ApiScheduledTaskMapper scheduledTaskMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private GitLabApiClient gitLabApiClient;

    private final byte[] secretKey;

    @Autowired
    public ApiGitRepoServiceImpl(@Value("${robotest.env.secret-key:}") String base64SecretKey) {
        this(SecretCryptoUtil.parseKey(base64SecretKey));
    }

    /** 测试构造：直接注入已解析密钥 */
    ApiGitRepoServiceImpl(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public ApiGitRepoRespDTO getRepo(UUID projectId, UUID workspaceId, UUID userId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiGitRepo row = gitRepoMapper.selectByProject(projectId);
        return row == null ? null : toResp(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "CREATE:ApiGitRepo")
    public ApiGitRepoRespDTO createRepo(UUID projectId, UUID workspaceId, UUID userId,
            ApiGitRepoCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireRepoPathFormat(reqDTO.getRepoPath());
        if (!IMPORT_MODES.contains(reqDTO.getImportMode())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_IMMUTABLE_FIELD.code(),
                    "接入方式取值仅 platform / gitlab_ci");
        }
        if (gitRepoMapper.selectByProject(projectId) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_PATH_CONFLICT);
        }
        requireToken(reqDTO.getToken());
        // 保存强制实测：先探测后落库，失败不产生半成品配置（详设 3.1）
        gitLabApiClient.probe(reqDTO.getGitlabBaseUrl(), reqDTO.getToken(),
                reqDTO.getRepoPath(), defaultBranch(reqDTO.getDefaultBranch()));

        ApiGitRepo row = new ApiGitRepo();
        row.setProjectId(projectId);
        row.setName(reqDTO.getName());
        row.setGitlabBaseUrl(reqDTO.getGitlabBaseUrl());
        row.setRepoPath(reqDTO.getRepoPath());
        row.setDefaultBranch(defaultBranch(reqDTO.getDefaultBranch()));
        row.setImportMode(reqDTO.getImportMode());
        row.setTokenCipher(encrypt(reqDTO.getToken()));
        row.setTestSourceDir(defaultIfBlank(reqDTO.getTestSourceDir(), DEFAULT_TEST_SOURCE_DIR));
        row.setResourceDir(defaultIfBlank(reqDTO.getResourceDir(), DEFAULT_RESOURCE_DIR));
        row.setEnvFilePath(defaultIfBlank(reqDTO.getEnvFilePath(), DEFAULT_ENV_FILE_PATH));
        row.setEnabled(true);
        row.setCreatedBy(userId);
        gitRepoMapper.insert(row);
        return toResp(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "UPDATE:ApiGitRepo")
    public void updateRepo(UUID projectId, UUID workspaceId, UUID userId, UUID repoId,
            ApiGitRepoUpdateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiGitRepo stored = requireRepo(projectId, repoId);
        for (String field : reqDTO.getRejectedFields().keySet()) {
            if (LOCKED_FIELDS.contains(field)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_IMMUTABLE_FIELD);
            }
        }
        String token = hasText(reqDTO.getToken()) ? reqDTO.getToken() : decrypt(stored);
        String gitlabBaseUrl = hasText(reqDTO.getGitlabBaseUrl()) ? reqDTO.getGitlabBaseUrl() : stored.getGitlabBaseUrl();
        // 更新强制实测：沿用已存令牌时同样以实测通过为准，失败不落库
        gitLabApiClient.probe(gitlabBaseUrl, token, stored.getRepoPath(), stored.getDefaultBranch());

        ApiGitRepo update = new ApiGitRepo();
        update.setId(repoId);
        update.setName(reqDTO.getName());
        update.setGitlabBaseUrl(reqDTO.getGitlabBaseUrl());
        update.setTestSourceDir(reqDTO.getTestSourceDir());
        update.setResourceDir(reqDTO.getResourceDir());
        update.setEnvFilePath(reqDTO.getEnvFilePath());
        // 令牌未变更时不触碰 token_cipher 列（C11 部分更新）
        if (hasText(reqDTO.getToken())) {
            update.setTokenCipher(encrypt(reqDTO.getToken()));
        }
        // 全空请求体无列可更新，跳过空 UPDATE
        if (update.getName() == null && update.getGitlabBaseUrl() == null && update.getTestSourceDir() == null
                && update.getResourceDir() == null && update.getEnvFilePath() == null
                && update.getTokenCipher() == null) {
            return;
        }
        gitRepoMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "DELETE:ApiGitRepo")
    public void deleteRepo(UUID projectId, UUID workspaceId, UUID userId, UUID repoId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireRepo(projectId, repoId);
        Long boundTasks = scheduledTaskMapper.selectCountEnabledGitlabSync(projectId);
        if (boundTasks != null && boundTasks > 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_TASK_BOUND);
        }
        gitRepoMapper.deleteById(repoId);
        // 删除后其下已导入场景保留，仅源状态置 repo_removed（详设 3.1）
        sceneMapper.markRepoScenesRepoRemoved(repoId, SOURCE_STATUS_REPO_REMOVED);
    }

    @Override
    public ApiGitRepoConnectionTestRespDTO testConnection(UUID projectId, UUID workspaceId, UUID userId,
            ApiGitRepoConnectionTestReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiGitRepo stored = gitRepoMapper.selectByProject(projectId);
        String gitlabBaseUrl = firstNonBlank(reqDTO.getGitlabBaseUrl(),
                stored == null ? null : stored.getGitlabBaseUrl());
        String repoPath = firstNonBlank(reqDTO.getRepoPath(), stored == null ? null : stored.getRepoPath());
        String branch = firstNonBlank(reqDTO.getDefaultBranch(),
                stored == null ? null : stored.getDefaultBranch());
        String token = firstNonBlank(reqDTO.getToken(), stored == null ? null : decrypt(stored));
        if (!hasText(gitlabBaseUrl)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(),
                    "GitLab 地址不能为空");
        }
        if (!hasText(repoPath)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.code(),
                    "项目路径不能为空");
        }
        requireToken(token);

        GitLabApiClient.Probe probe = gitLabApiClient.probe(gitlabBaseUrl, token, repoPath, defaultBranch(branch));
        return new ApiGitRepoConnectionTestRespDTO(true, true, true, probe.version(), probe.resolvedBranch());
    }

    // ========== 内部工具 ==========

    private ApiGitRepo requireRepo(UUID projectId, UUID repoId) {
        ApiGitRepo row = gitRepoMapper.selectById(repoId);
        if (row == null || !projectId.equals(row.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND);
        }
        return row;
    }

    private void requireRepoPathFormat(String repoPath) {
        if (!hasText(repoPath)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.code(),
                    "项目路径不能为空");
        }
        String lower = repoPath.toLowerCase();
        if (lower.endsWith(".git") || lower.startsWith("http://") || lower.startsWith("https://")) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.code(),
                    "项目路径形如 group/subgroup/repo，不含 .git 后缀与 http(s):// 前缀");
        }
    }

    private void requireToken(String token) {
        if (!hasText(token)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(),
                    "访问令牌不能为空");
        }
    }

    /** 沿用已存令牌：解密失败（密钥轮换 / 密文损坏）须重新填写，不允许以空令牌通过实测 */
    private String decrypt(ApiGitRepo stored) {
        String plain = secretKey == null ? null : SecretCryptoUtil.decrypt(secretKey, stored.getTokenCipher());
        if (!hasText(plain)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(),
                    "已存访问令牌无法解密，请重新填写令牌");
        }
        return plain;
    }

    private String encrypt(String plainToken) {
        if (secretKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.INTERNAL_SERVER_ERROR.code(),
                    "服务端密钥未配置，无法加密存储令牌");
        }
        return SecretCryptoUtil.encrypt(secretKey, plainToken);
    }

    private ApiGitRepoRespDTO toResp(ApiGitRepo row) {
        ApiGitRepoRespDTO dto = new ApiGitRepoRespDTO();
        dto.setId(String.valueOf(row.getId()));
        dto.setProjectId(String.valueOf(row.getProjectId()));
        dto.setName(row.getName());
        dto.setGitlabBaseUrl(row.getGitlabBaseUrl());
        dto.setRepoPath(row.getRepoPath());
        dto.setDefaultBranch(row.getDefaultBranch());
        dto.setImportMode(row.getImportMode());
        dto.setEnabled(row.getEnabled());
        dto.setTestSourceDir(row.getTestSourceDir());
        dto.setResourceDir(row.getResourceDir());
        dto.setEnvFilePath(row.getEnvFilePath());
        dto.setLastImportAt(row.getLastImportAt());
        dto.setLastImportCommit(row.getLastImportCommit());
        dto.setLastImportSummary(row.getLastImportSummary());
        dto.setHeadCommit(row.getHeadCommit());
        dto.setHeadCheckedAt(row.getHeadCheckedAt());
        dto.setHeadProbeStatus(row.getHeadProbeStatus());
        // 派生「源有更新」：任一为 NULL 时为 false，前端不自行比对提交号（详设 3.1）
        dto.setHasUpdates(row.getHeadCommit() != null && row.getLastImportCommit() != null
                && !row.getHeadCommit().equals(row.getLastImportCommit()));
        return dto;
    }

    private static String defaultBranch(String branch) {
        return hasText(branch) ? branch : DEFAULT_BRANCH;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
