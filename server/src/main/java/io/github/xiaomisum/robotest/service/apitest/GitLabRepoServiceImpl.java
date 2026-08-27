package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabRepoSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabRepoListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabRepoTestConnectionRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.GitLabRepository;
import io.github.xiaomisum.robotest.repository.apitest.GitLabRepositoryMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitLabRepoServiceImpl implements GitLabRepoService {

    private static final Logger log = LoggerFactory.getLogger(GitLabRepoServiceImpl.class);

    private static final Pattern GITLAB_URL_PATTERN = Pattern.compile(
            "^(https?://[^/]+)/(.+?)\\.git$");

    @Resource
    private GitLabRepositoryMapper gitLabRepositoryMapper;

    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Value("${robotest.env.secret-key:}")
    private String secretKeyBase64;

    @Override
    public PageResult<GitLabRepoListItemRespDTO> fetchPage(UUID projectId, UUID workspaceId, UUID userId,
                                                            String keyword, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        PageResult<GitLabRepository> page = gitLabRepositoryMapper.selectPageByProject(projectId, keyword, pageParam);
        return new PageResult<>(page.getList().stream().map(this::toListRespDTO).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID create(UUID projectId, UUID workspaceId, UUID userId, GitLabRepoSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);

        if (!StringUtils.hasText(reqDTO.getAccessToken())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "创建仓库配置时访问令牌不能为空");
        }

        if (gitLabRepositoryMapper.existsByProjectAndName(projectId, reqDTO.getName(), null)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NAME_EXISTS, reqDTO.getName());
        }

        // 加密令牌
        byte[] key = requireCipherKey();
        String cipher = SecretCryptoUtil.encrypt(key, reqDTO.getAccessToken());
        String suffix = SecretCryptoUtil.keySuffix(reqDTO.getAccessToken());

        GitLabRepository entity = new GitLabRepository();
        entity.setProjectId(projectId);
        entity.setName(reqDTO.getName());
        entity.setRepoUrl(reqDTO.getRepoUrl());
        entity.setBranch(reqDTO.getBranch());
        entity.setAccessTokenCipher(cipher);
        entity.setTokenSuffix(suffix);
        entity.setTestSourcePath(reqDTO.getTestSourcePath());
        gitLabRepositoryMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID projectId, UUID workspaceId, UUID userId, UUID id, GitLabRepoSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);

        GitLabRepository existing = gitLabRepositoryMapper.selectByProjectAndId(projectId, id);
        if (existing == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND);
        }

        if (gitLabRepositoryMapper.existsByProjectAndName(projectId, reqDTO.getName(), id)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NAME_EXISTS, reqDTO.getName());
        }

        // 只更新传入字段
        GitLabRepository update = new GitLabRepository();
        update.setId(id);
        update.setName(reqDTO.getName());
        update.setRepoUrl(reqDTO.getRepoUrl());
        update.setBranch(reqDTO.getBranch());
        update.setTestSourcePath(reqDTO.getTestSourcePath());

        // 令牌非空时重新加密
        if (StringUtils.hasText(reqDTO.getAccessToken())) {
            byte[] key = requireCipherKey();
            String cipher = SecretCryptoUtil.encrypt(key, reqDTO.getAccessToken());
            String suffix = SecretCryptoUtil.keySuffix(reqDTO.getAccessToken());
            update.setAccessTokenCipher(cipher);
            update.setTokenSuffix(suffix);
        }

        gitLabRepositoryMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);

        GitLabRepository existing = gitLabRepositoryMapper.selectByProjectAndId(projectId, id);
        if (existing == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND);
        }

        log.info("删除 GitLab 仓库配置：repositoryId={}, 关联元数据和同步历史将保留", id);
        gitLabRepositoryMapper.deleteById(id);
    }

    @Override
    public GitLabRepoTestConnectionRespDTO testConnection(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);

        GitLabRepository repo = gitLabRepositoryMapper.selectByProjectAndId(projectId, id);
        if (repo == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND);
        }

        byte[] key = requireCipherKey();
        String token = SecretCryptoUtil.decrypt(key, repo.getAccessTokenCipher());
        if (token == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
        }

        return doTestConnection(repo.getRepoUrl(), token);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> listBranches(UUID projectId, UUID workspaceId, UUID userId, UUID repositoryId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        GitLabRepository repo = gitLabRepositoryMapper.selectByProjectAndId(projectId, repositoryId);
        if (repo == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND);
        }
        byte[] key = requireCipherKey();
        String token = SecretCryptoUtil.decrypt(key, repo.getAccessTokenCipher());
        if (token == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
        }
        return doListBranches(repo.getRepoUrl(), token);
    }

    @SuppressWarnings("unchecked")
    private List<String> doListBranches(String repoUrl, String token) {
        Matcher matcher = GITLAB_URL_PATTERN.matcher(repoUrl);
        if (!matcher.matches()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE, "仓库地址格式不合法");
        }
        String apiBase = matcher.group(1) + "/api/v4";
        String projectPath = URLDecoder.decode(matcher.group(2), StandardCharsets.UTF_8);
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8).replace("+", "%2F");
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/projects/" + encodedPath + "/repository/branches?per_page=100"))
                    .header("Private-Token", token)
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
            }
            if (response.statusCode() == 404) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                        "仓库地址不可达或令牌无访问权限");
            }
            if (response.statusCode() != 200) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                        "获取分支列表失败，HTTP " + response.statusCode());
            }
            Object parsed = JsonUtils.parseObject(response.body(), Object.class);
            if (parsed instanceof List<?> list) {
                return list.stream()
                        .filter(item -> item instanceof Map)
                        .map(item -> (String) ((Map<String, Object>) item).get("name"))
                        .filter(Objects::nonNull)
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.error("获取分支列表失败", e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private GitLabRepoTestConnectionRespDTO doTestConnection(String repoUrl, String token) {
        Matcher matcher = GITLAB_URL_PATTERN.matcher(repoUrl);
        if (!matcher.matches()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "仓库地址格式不合法，需为 https://host/path.git 格式");
        }

        String apiBase = matcher.group(1) + "/api/v4";
        String projectPath = URLDecoder.decode(matcher.group(2), StandardCharsets.UTF_8);
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8)
                .replace("+", "%2F");

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/projects/" + encodedPath + "?statistics=true"))
                    .header("Private-Token", token)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
            }
            if (response.statusCode() == 404) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                        "仓库地址不可达或令牌无访问权限");
            }
            if (response.statusCode() != 200) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                        "GitLab API 返回异常状态：" + response.statusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = JsonUtils.parseObject(response.body(), Map.class);

            GitLabRepoTestConnectionRespDTO result = new GitLabRepoTestConnectionRespDTO();
            result.setSuccess(true);
            result.setMessage("连接成功");
            result.setRepoName((String) body.get("name"));
            result.setDefaultBranch((String) body.get("default_branch"));
            // 统计信息（?statistics=true）
            Object stats = body.get("statistics");
            if (stats instanceof Map<?, ?> statisticsMap) {
                Object commits = statisticsMap.get("commit_count");
                result.setCommitCount(commits instanceof Number n ? n.longValue() : null);
            } else {
                result.setCommitCount(null);
            }
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "连接失败：" + e.getMessage());
        }
    }

    private GitLabRepoListItemRespDTO toListRespDTO(GitLabRepository entity) {
        GitLabRepoListItemRespDTO dto = new GitLabRepoListItemRespDTO();
        dto.setId(entity.getId().toString());
        dto.setName(entity.getName());
        dto.setRepoUrl(entity.getRepoUrl());
        dto.setBranch(entity.getBranch());
        dto.setTokenSuffix(entity.getTokenSuffix());
        dto.setTestSourcePath(entity.getTestSourcePath());
        dto.setLastImportStatus(entity.getLastImportStatus());
        dto.setLastImportAt(entity.getLastImportAt() != null ? entity.getLastImportAt().toString() : null);
        dto.setLastMetadataSyncAt(entity.getLastMetadataSyncAt() != null
                ? entity.getLastMetadataSyncAt().toString() : null);
        dto.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return dto;
    }

    private byte[] requireCipherKey() {
        byte[] key = SecretCryptoUtil.parseKey(secretKeyBase64);
        if (key == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_SECRET_KEY_MISSING);
        }
        return key;
    }
}
