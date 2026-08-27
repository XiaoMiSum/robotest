package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabPipelineTriggerReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabMetadataImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabPipelineReportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabPipelineRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabPipelineStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.GitLabRepository;
import io.github.xiaomisum.robotest.model.entity.apitest.GitLabTestClassMetadata;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.GitLabRepositoryMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitLabPipelineServiceImpl implements GitLabPipelineService {

    private static final Logger log = LoggerFactory.getLogger(GitLabPipelineServiceImpl.class);

    private static final Pattern GITLAB_URL_PATTERN = Pattern.compile("^(https?://[^/]+)/(.+?)\\.git$");
    private static final long METADATA_EXPIRE_HOURS = 1;

    @Resource
    private GitLabRepositoryMapper gitLabRepositoryMapper;

    @Resource
    private ApiExecutionRecordMapper executionRecordMapper;

    @Resource
    private ApiReportMapper reportMapper;

    @Resource
    private ApiSceneMapper sceneMapper;

    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Resource
    private GitLabMetadataImportService metadataImportService;

    @Resource
    private GitLabTestScopeService testScopeService;

    @Value("${robotest.env.secret-key:}")
    private String secretKeyBase64;

    @Override
    @SuppressWarnings("unchecked")
    public GitLabPipelineRespDTO triggerPipeline(UUID projectId, UUID workspaceId, UUID userId,
                                                  UUID repositoryId, GitLabPipelineTriggerReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);

        // 1. 校验仓库与场景
        GitLabRepository repo = requireRepository(projectId, repositoryId);
        ApiScene scene = sceneMapper.selectById(reqDTO.getSceneId());
        if (scene == null || !scene.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }

        // 2. 元数据过期检测
        boolean metadataExpired = false;
        Integer syncClassCount = null;
        if (isMetadataExpired(repo)) {
            metadataExpired = true;
            GitLabMetadataImportRespDTO syncResult = metadataImportService.importMetadata(
                    projectId, workspaceId, userId, repositoryId);
            syncClassCount = syncResult.getClassCount();
        }

        // 3. 解密令牌
        byte[] key = requireCipherKey();
        String token = SecretCryptoUtil.decrypt(key, repo.getAccessTokenCipher());
        if (token == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
        }

        // 4. 解析 GitLab API 路径
        Matcher urlMatcher = GITLAB_URL_PATTERN.matcher(repo.getRepoUrl());
        if (!urlMatcher.matches()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "仓库地址格式不合法");
        }
        String apiBase = urlMatcher.group(1) + "/api/v4";
        String projectPath = java.net.URLDecoder.decode(urlMatcher.group(2), StandardCharsets.UTF_8);
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8).replace("+", "%2F");

        try {
            // 5. 组装 CI 变量：表定义 → 用户 testScope 覆盖 → 用户 variables 覆盖
            Map<String, String> ciVariables = new LinkedHashMap<>(testScopeService.buildScopeVariables(repositoryId));
            if (reqDTO.getTestScope() != null) {
                reqDTO.getTestScope().forEach(ciVariables::put);
            }
            if (reqDTO.getVariables() != null) {
                reqDTO.getVariables().forEach(ciVariables::put);
            }

            // 6. 调用 GitLab Trigger Pipeline API
            Map<String, Object> triggerResult = callTriggerPipelineApi(apiBase, encodedPath, token,
                    repo.getBranch(), ciVariables);

            String pipelineId = String.valueOf(triggerResult.get("id"));
            String pipelineUrl = (String) triggerResult.get("web_url");

            // 7. 创建执行记录
            ApiExecutionRecord record = new ApiExecutionRecord();
            record.setId(UUID.randomUUID());
            record.setProjectId(projectId);
            record.setSceneId(reqDTO.getSceneId());
            record.setExecutionMode("pipeline");
            record.setStatus("pending");
            record.setTriggerType("manual");
            record.setPipelineId(pipelineId);
            record.setPipelineUrl(pipelineUrl);
            record.setRepositoryId(repositoryId);
            record.setExecutedAt(LocalDateTime.now());
            executionRecordMapper.insert(record);

            // 8. 返回结果
            GitLabPipelineRespDTO result = new GitLabPipelineRespDTO();
            result.setExecutionRecordId(record.getId().toString());
            result.setPipelineId(pipelineId);
            result.setPipelineUrl(pipelineUrl);
            result.setStatus("pending");
            result.setMetadataExpired(metadataExpired);
            result.setMetadataSyncClassCount(syncClassCount);
            return result;

        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("触发流水线失败：repositoryId={}", repositoryId, e);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "触发流水线失败：" + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public GitLabPipelineStatusRespDTO queryPipelineStatus(UUID projectId, UUID workspaceId, UUID userId,
                                                            UUID executionId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);

        ApiExecutionRecord record = executionRecordMapper.selectById(executionId);
        if (record == null || !record.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
        if (!"pipeline".equals(record.getExecutionMode())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "非流水线执行记录");
        }
        if (record.getPipelineId() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "流水线 ID 为空");
        }

        // 查找对应的仓库配置（通过执行记录的 repositoryId）
        UUID repoId = record.getRepositoryId();
        if (repoId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND);
        }
        GitLabRepository repo = gitLabRepositoryMapper.selectByProjectAndId(projectId, repoId);
        if (repo == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND);
        }

        byte[] key = requireCipherKey();
        String token = SecretCryptoUtil.decrypt(key, repo.getAccessTokenCipher());
        if (token == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
        }

        Matcher urlMatcher = GITLAB_URL_PATTERN.matcher(repo.getRepoUrl());
        if (!urlMatcher.matches()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE, "仓库地址格式不合法");
        }
        String apiBase = urlMatcher.group(1) + "/api/v4";
        String projectPath = java.net.URLDecoder.decode(urlMatcher.group(2), StandardCharsets.UTF_8);
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8).replace("+", "%2F");

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/projects/" + encodedPath
                            + "/pipelines/" + record.getPipelineId()))
                    .header("Private-Token", token)
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                        "查询流水线状态失败：" + response.statusCode());
            }

            Map<String, Object> body = JsonUtils.parseObject(response.body(), Map.class);
            String status = (String) body.get("status");
            Long duration = body.get("duration") instanceof Number
                    ? ((Number) body.get("duration")).longValue() : null;

            // 更新执行记录状态
            if (!"running".equals(status) && !"pending".equals(status) && !"created".equals(status)) {
                updateRecordStatus(record.getId(), status);
            }

            // 查询流水线 stages
            List<GitLabPipelineStatusRespDTO.StageItem> stages = queryPipelineStages(
                    apiBase, encodedPath, token, record.getPipelineId());

            GitLabPipelineStatusRespDTO result = new GitLabPipelineStatusRespDTO();
            result.setPipelineId(record.getPipelineId());
            result.setStatus(mapPipelineStatus(status));
            result.setDuration(duration);
            result.setStages(stages);
            return result;

        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询流水线状态失败：executionId={}", executionId, e);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "查询流水线状态失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callTriggerPipelineApi(String apiBase, String encodedPath, String token,
                                                       String branch, Map<String, String> variables)
            throws Exception {
        // 构建 form body
        StringBuilder formBody = new StringBuilder();
        formBody.append("ref=").append(URLEncoder.encode(branch, StandardCharsets.UTF_8));
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                formBody.append("&variables%5B")
                        .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append("%5D=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/projects/" + encodedPath + "/trigger/pipeline"))
                .header("Private-Token", token)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(formBody.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
        }
        if (response.statusCode() == 404) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "仓库地址不可达或令牌无访问权限");
        }
        if (response.statusCode() != 201 && response.statusCode() != 200) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "GitLab 触发流水线失败，HTTP " + response.statusCode());
        }

        Map<String, Object> body = JsonUtils.parseObject(response.body(), Map.class);
        if (body == null || body.get("id") == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "GitLab 返回数据异常");
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private List<GitLabPipelineStatusRespDTO.StageItem> queryPipelineStages(
            String apiBase, String encodedPath, String token, String pipelineId) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/projects/" + encodedPath
                            + "/pipelines/" + pipelineId + "/jobs"))
                    .header("Private-Token", token)
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return List.of();
            }

            Object parsed = JsonUtils.parseObject(response.body(), Object.class);
            if (parsed instanceof List<?> list) {
                return list.stream()
                        .filter(item -> item instanceof Map)
                        .map(item -> {
                            Map<String, Object> map = (Map<String, Object>) item;
                            GitLabPipelineStatusRespDTO.StageItem stage = new GitLabPipelineStatusRespDTO.StageItem();
                            stage.setName((String) map.get("name"));
                            stage.setStatus((String) map.get("status"));
                            return stage;
                        })
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("查询流水线 stages 失败：pipelineId={}", pipelineId, e);
            return List.of();
        }
    }

    private boolean isMetadataExpired(GitLabRepository repo) {
        if (repo.getLastMetadataSyncAt() == null) {
            return true;
        }
        long hoursSinceSync = ChronoUnit.HOURS.between(repo.getLastMetadataSyncAt(), LocalDateTime.now());
        return hoursSinceSync >= METADATA_EXPIRE_HOURS;
    }

    private void updateRecordStatus(UUID recordId, String gitlabStatus) {
        String mappedStatus = mapPipelineStatus(gitlabStatus);
        ApiExecutionRecord update = new ApiExecutionRecord();
        update.setId(recordId);
        update.setStatus(mappedStatus);
        if ("success".equals(mappedStatus) || "failed".equals(mappedStatus) || "error".equals(mappedStatus)) {
            update.setErrorMessage("failed".equals(mappedStatus) || "error".equals(mappedStatus)
                    ? "流水线执行失败" : null);
        }
        executionRecordMapper.updateById(update);
    }

    private String mapPipelineStatus(String gitlabStatus) {
        if (gitlabStatus == null) return "error";
        return switch (gitlabStatus) {
            case "created", "pending", "running" -> gitlabStatus;
            case "success" -> "success";
            case "failed" -> "failed";
            case "canceled" -> "cancelled";
            case "skipped" -> "skipped";
            default -> "error";
        };
    }

    private GitLabRepository requireRepository(UUID projectId, UUID repositoryId) {
        GitLabRepository repo = gitLabRepositoryMapper.selectByProjectAndId(projectId, repositoryId);
        if (repo == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND);
        }
        return repo;
    }

    private byte[] requireCipherKey() {
        byte[] key = SecretCryptoUtil.parseKey(secretKeyBase64);
        if (key == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_SECRET_KEY_MISSING);
        }
        return key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GitLabPipelineReportRespDTO pullReport(UUID projectId, UUID workspaceId, UUID userId, UUID executionId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);

        ApiExecutionRecord record = executionRecordMapper.selectById(executionId);
        if (record == null || !record.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
        if (!"pipeline".equals(record.getExecutionMode())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "非流水线执行记录");
        }
        if (record.getPipelineId() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "流水线 ID 为空");
        }

        UUID repoId = record.getRepositoryId();
        if (repoId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND);
        }
        GitLabRepository repo = gitLabRepositoryMapper.selectByProjectAndId(projectId, repoId);
        if (repo == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND);
        }

        byte[] key = requireCipherKey();
        String token = SecretCryptoUtil.decrypt(key, repo.getAccessTokenCipher());
        if (token == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
        }

        Matcher urlMatcher = GITLAB_URL_PATTERN.matcher(repo.getRepoUrl());
        if (!urlMatcher.matches()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE, "仓库地址格式不合法");
        }
        String apiBase = urlMatcher.group(1) + "/api/v4";
        String projectPath = java.net.URLDecoder.decode(urlMatcher.group(2), StandardCharsets.UTF_8);
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8).replace("+", "%2F");

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/projects/" + encodedPath
                            + "/pipelines/" + record.getPipelineId()
                            + "/artifacts/report.json"))
                    .header("Private-Token", token)
                    .timeout(Duration.ofSeconds(30))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED,
                        "流水线产物不存在或尚未生成，请稍后重试");
            }
            if (response.statusCode() != 200) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                        "拉取流水线报告失败，HTTP " + response.statusCode());
            }

            Map<String, Object> reportData = JsonUtils.parseObject(response.body(), Map.class);
            Map<String, Object> summary = reportData.get("summary") instanceof Map m ? m : Map.of();

            ApiReport report = new ApiReport();
            report.setId(UUID.randomUUID());
            report.setProjectId(projectId);
            report.setExecutionRecordId(record.getId());
            report.setSceneId(record.getSceneId());
            report.setExecutionMode("pipeline");
            report.setStatus(summary.get("failed") instanceof Number num && num.intValue() > 0 ? "failed" : "success");
            report.setSummary(summary);
            report.setStepResults(List.of());
            report.setShareEnabled(false);
            reportMapper.insert(report);

            ApiExecutionRecord update = new ApiExecutionRecord();
            update.setId(record.getId());
            update.setReportId(report.getId());
            update.setStatus("success");
            executionRecordMapper.updateById(update);

            GitLabPipelineReportRespDTO result = new GitLabPipelineReportRespDTO();
            result.setReportId(report.getId().toString());
            result.setSummary(summary);
            return result;

        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("拉取流水线报告失败：executionId={}", executionId, e);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "拉取流水线报告失败：" + e.getMessage());
        }
    }
}
