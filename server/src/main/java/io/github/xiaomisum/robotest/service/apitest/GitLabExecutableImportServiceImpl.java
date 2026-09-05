package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabExecutableImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabExecutableImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabFileTreeNodeRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportMapping;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.GitLabRepository;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportMappingMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.GitLabRepositoryMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

@Service
public class GitLabExecutableImportServiceImpl implements GitLabExecutableImportService {

    private static final Logger log = LoggerFactory.getLogger(GitLabExecutableImportServiceImpl.class);

    private static final Pattern GITLAB_URL_PATTERN = Pattern.compile("^(https?://[^/]+)/(.+?)\\.git$");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^(\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*)" +
            "(?:public\\s+)?(?:abstract\\s+)?class\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@(\\w+)(?:\\(([^)]*)\\))?");
    private static final Pattern RESOURCE_PATH_PATTERN = Pattern.compile(
            "resourcePath\\s*=\\s*\"([^\"]+)\"");

    @Resource
    private GitLabRepositoryMapper gitLabRepositoryMapper;

    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Resource
    private ApiSceneMapper apiSceneMapper;

    @Resource
    private ApiImportRecordMapper importRecordMapper;

    @Resource
    private ApiImportMappingMapper importMappingMapper;

    @Value("${robotest.env.secret-key:}")
    private String secretKeyBase64;

    @Override
    public GitLabFileTreeNodeRespDTO browseFiles(UUID projectId, UUID workspaceId, UUID userId,
                                                   UUID repositoryId, String path) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        GitLabRepository repo = requireRepository(projectId, repositoryId);

        byte[] key = requireCipherKey();
        String token = SecretCryptoUtil.decrypt(key, repo.getAccessTokenCipher());
        if (token == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
        }

        Matcher urlMatcher = GITLAB_URL_PATTERN.matcher(repo.getRepoUrl());
        if (!urlMatcher.matches()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "仓库地址格式不合法");
        }
        String apiBase = urlMatcher.group(1) + "/api/v4";
        String projectPath = URLDecoder.decode(urlMatcher.group(2), StandardCharsets.UTF_8);
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8).replace("+", "%2F");

        try {
            return doBrowseFiles(apiBase, encodedPath, token, repo.getBranch(),
                    path != null ? path : "");
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("浏览仓库文件失败：repositoryId={}", repositoryId, e);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "浏览仓库文件失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GitLabExecutableImportRespDTO importExecutable(UUID projectId, UUID workspaceId, UUID userId,
                                                           UUID repositoryId, GitLabExecutableImportReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        GitLabRepository repo = requireRepository(projectId, repositoryId);

        byte[] key = requireCipherKey();
        String token = SecretCryptoUtil.decrypt(key, repo.getAccessTokenCipher());
        if (token == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
        }

        String scope = reqDTO.getScope() != null ? reqDTO.getScope() : "all";
        List<String> selectedClasses = "selected".equals(scope) && reqDTO.getClassNames() != null
                ? reqDTO.getClassNames() : List.of();
        String conflictStrategy = reqDTO.getConflictStrategy() != null
                ? reqDTO.getConflictStrategy() : "skip";

        Matcher urlMatcher = GITLAB_URL_PATTERN.matcher(repo.getRepoUrl());
        if (!urlMatcher.matches()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "仓库地址格式不合法");
        }
        String apiBase = urlMatcher.group(1) + "/api/v4";
        String projectPath = URLDecoder.decode(urlMatcher.group(2), StandardCharsets.UTF_8);
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8).replace("+", "%2F");

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("gitlab-exe-import-");
            Path extractDir = tempDir.resolve("src");
            Files.createDirectories(extractDir);
            downloadZip(apiBase, encodedPath, token, repo.getBranch(), extractDir);

            List<ExecutableClassInfo> classes = scanExecutableClasses(extractDir, repo.getTestSourcePath());
            if (!selectedClasses.isEmpty()) {
                classes = classes.stream()
                        .filter(c -> selectedClasses.contains(c.fullClassName))
                        .toList();
            }

            int created = 0, updated = 0, skipped = 0, failed = 0;
            List<Map<String, Object>> errorDetails = new ArrayList<>();
            List<GitLabExecutableImportRespDTO.SceneItem> sceneItems = new ArrayList<>();
            UUID importRecordId = UUID.randomUUID();

            for (ExecutableClassInfo cls : classes) {
                try {
                    String yamlContent = findAndReadResource(extractDir, cls.resourcePath);
                    if (yamlContent == null) {
                        failed++;
                        errorDetails.add(Map.of(
                                "path", cls.resourcePath,
                                "message", "YAML 资源文件未找到：" + cls.resourcePath));
                        continue;
                    }

                    Map<String, Object> sceneData = RyzeYamlToSceneConverter.convert(yamlContent);
                    String sceneName = (String) sceneData.getOrDefault("name", cls.displayName);

                    ApiScene existing = apiSceneMapper.selectByName(projectId, sceneName);
                    String action;

                    if (existing != null) {
                        if ("skip".equals(conflictStrategy)) {
                            skipped++;
                            action = "skipped";
                            writeImportMapping(projectId, importRecordId, cls.fullClassName,
                                    cls.displayName, "scene", existing.getId(), action);
                            continue;
                        }
                        updateSceneFromData(existing, sceneData);
                        existing.setSteps(buildSteps(sceneData));
                        apiSceneMapper.updateById(existing);
                        updated++;
                        action = "updated";
                        writeImportMapping(projectId, importRecordId, cls.fullClassName,
                                cls.displayName, "scene", existing.getId(), action);
                        sceneItems.add(toSceneItem(existing, sceneData));
                    } else {
                        ApiScene scene = createSceneFromData(projectId, sceneData);
                        scene.setSteps(buildSteps(sceneData));
                        apiSceneMapper.insert(scene);
                        created++;
                        action = "created";
                        writeImportMapping(projectId, importRecordId, cls.fullClassName,
                                cls.displayName, "scene", scene.getId(), action);
                        sceneItems.add(toSceneItem(scene, sceneData));
                    }
                } catch (Exception e) {
                    log.warn("导入类失败：{}", cls.fullClassName, e);
                    failed++;
                    errorDetails.add(Map.of(
                            "path", cls.resourcePath,
                            "message", "导入失败：" + e.getMessage()));
                }
            }

            GitLabRepository update = new GitLabRepository();
            update.setId(repositoryId);
            update.setLastImportAt(LocalDateTime.now());
            update.setLastImportStatus(failed == 0 ? "success" : created + updated > 0 ? "partial" : "failed");
            gitLabRepositoryMapper.updateById(update);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("created", created);
            summary.put("updated", updated);
            summary.put("failed", failed);
            summary.put("skipped", skipped);

            ApiImportRecord record = new ApiImportRecord();
            record.setId(importRecordId);
            record.setProjectId(projectId);
            record.setImportType("gitlab_executable");
            record.setSourceName(repo.getName());
            record.setStatus(failed == 0 ? "success" : created + updated > 0 ? "partial" : "failed");
            record.setSummary(summary);
            record.setErrorDetails(errorDetails);
            record.setRepositoryId(repositoryId);
            record.setCreatedBy(userId);
            importRecordMapper.insert(record);

            GitLabExecutableImportRespDTO result = new GitLabExecutableImportRespDTO();
            result.setImportHistoryId(importRecordId.toString());
            result.setSummary(summary);
            result.setScenes(sceneItems);
            result.setErrorDetails(errorDetails);
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("可执行导入失败：repositoryId={}", repositoryId, e);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "可执行导入失败：" + e.getMessage());
        } finally {
            if (tempDir != null) {
                deleteTempDir(tempDir);
            }
        }
    }

    @Override
    public GitLabExecutableImportRespDTO fetchLatestImport(UUID projectId, UUID workspaceId, UUID userId,
                                                             UUID repositoryId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireRepository(projectId, repositoryId);

        LambdaQueryWrapperX<ApiImportRecord> wrapper =
                new LambdaQueryWrapperX<ApiImportRecord>()
                        .eq(ApiImportRecord::getProjectId, projectId)
                        .eq(ApiImportRecord::getRepositoryId, repositoryId)
                        .eq(ApiImportRecord::getImportType, "gitlab_executable")
                        .orderByDesc(ApiImportRecord::getCreatedAt)
                        .last("LIMIT 1");
        ApiImportRecord record = importRecordMapper.selectOne(wrapper);
        if (record == null) {
            return new GitLabExecutableImportRespDTO();
        }

        List<ApiImportMapping> mappings = importMappingMapper.selectList(
                new LambdaQueryWrapperX<ApiImportMapping>()
                        .eq(ApiImportMapping::getImportRecordId, record.getId()));

        List<GitLabExecutableImportRespDTO.SceneItem> sceneItems = new ArrayList<>();
        for (ApiImportMapping mapping : mappings) {
            if ("scene".equals(mapping.getTargetType()) && mapping.getTargetId() != null) {
                ApiScene scene = apiSceneMapper.selectById(mapping.getTargetId());
                if (scene != null) {
                    GitLabExecutableImportRespDTO.SceneItem item = new GitLabExecutableImportRespDTO.SceneItem();
                    item.setId(scene.getId().toString());
                    item.setName(scene.getName());
                    List<Map<String, Object>> steps = scene.getSteps();
                    item.setStepCount(steps == null ? 0 : steps.size());
                    sceneItems.add(item);
                }
            }
        }

        GitLabExecutableImportRespDTO result = new GitLabExecutableImportRespDTO();
        result.setImportHistoryId(record.getId().toString());
        result.setSummary(record.getSummary());
        result.setScenes(sceneItems);
        result.setErrorDetails(record.getErrorDetails());
        return result;
    }

    @SuppressWarnings("unchecked")
    private GitLabFileTreeNodeRespDTO doBrowseFiles(String apiBase, String encodedPath, String token,
                                                     String branch, String path) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String url = apiBase + "/projects/" + encodedPath
                + "/repository/tree?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8)
                        .replace("+", "%2F")
                + "&per_page=100&ref=" + URLEncoder.encode(branch, StandardCharsets.UTF_8)
                        .replace("+", "%2F");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Private-Token", token)
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "GitLab API 返回异常状态：" + response.statusCode());
        }

        List<Map<String, Object>> items = JsonUtils.parseObject(response.body(), List.class);
        if (items == null) {
            items = List.of();
        }

        List<GitLabFileTreeNodeRespDTO> nodes = new ArrayList<>();
        for (Map<String, Object> item : items) {
            GitLabFileTreeNodeRespDTO node = new GitLabFileTreeNodeRespDTO();
            node.setName((String) item.get("name"));
            node.setPath((String) item.get("path"));
            node.setType((String) item.get("type"));
            if ("tree".equals(node.getType())) {
                node.setChildren(List.of());
            }
            nodes.add(node);
        }

        GitLabFileTreeNodeRespDTO root = new GitLabFileTreeNodeRespDTO();
        root.setName(path.isEmpty() ? "/" : path);
        root.setPath(path);
        root.setType("tree");
        root.setChildren(nodes);
        return root;
    }

    private void downloadZip(String apiBase, String encodedPath, String token, String branch, Path extractDir)
            throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String archiveUrl = apiBase + "/projects/" + encodedPath
                + "/repository/archive.zip?sha=" + URLEncoder.encode(branch, StandardCharsets.UTF_8)
                        .replace("+", "%2F");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(archiveUrl))
                .header("Private-Token", token)
                .timeout(Duration.ofSeconds(60))
                .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "下载源码失败，HTTP " + response.statusCode());
        }

        try (ZipInputStream zis = new ZipInputStream(response.body())) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path filePath = extractDir.resolve(entry.getName());
                if (!filePath.startsWith(extractDir)) {
                    continue;
                }
                Files.createDirectories(filePath.getParent());
                Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private List<ExecutableClassInfo> scanExecutableClasses(Path extractDir, String testSourcePath) {
        List<ExecutableClassInfo> result = new ArrayList<>();
        Path scanRoot = testSourcePath != null && !testSourcePath.isBlank()
                ? extractDir.resolve(testSourcePath) : extractDir;
        if (!Files.exists(scanRoot)) {
            return result;
        }
        try (Stream<Path> javaFiles = Files.walk(scanRoot)) {
            javaFiles.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            List<ExecutableClassInfo> found = parseExecutableClasses(content);
                            result.addAll(found);
                        } catch (Exception e) {
                            log.warn("解析 Java 文件失败：{}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("扫描目录失败：{}", scanRoot, e);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<ExecutableClassInfo> parseExecutableClasses(String content) {
        List<ExecutableClassInfo> result = new ArrayList<>();

        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(content);
        String packageName = pkgMatcher.find() ? pkgMatcher.group(1) : null;

        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        if (!classMatcher.find()) {
            return result;
        }
        String className = classMatcher.group(2);
        String fullClassName = packageName != null ? packageName + "." + className : className;

        Matcher rpMatcher = RESOURCE_PATH_PATTERN.matcher(content);
        String resourcePath = rpMatcher.find() ? rpMatcher.group(1) : null;

        String displayName = null;
        int classStart = classMatcher.start();
        String beforeClass = content.substring(0, classStart);
        Matcher commentMatcher = Pattern.compile("/\\*\\*\\s*(.+?)\\s*\\*/", Pattern.DOTALL).matcher(beforeClass);
        if (commentMatcher.find()) {
            displayName = commentMatcher.group(1).trim().split("\\n")[0].trim();
        }

        boolean hasRyzeTestMethod = false;
        Matcher methodMatcher = Pattern.compile(
                "^(\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*)" +
                "(?:public\\s+)?void\\s+(\\w+)\\s*\\(",
                Pattern.MULTILINE).matcher(content);
        while (methodMatcher.find()) {
            String methodAnnotations = methodMatcher.group(1);
            List<String> names = new ArrayList<>();
            Matcher annMatcher = ANNOTATION_PATTERN.matcher(methodAnnotations);
            while (annMatcher.find()) {
                names.add(annMatcher.group(1));
            }
            if (names.contains("RyzeTest")) {
                hasRyzeTestMethod = true;
                break;
            }
        }

        if (hasRyzeTestMethod && resourcePath != null && !resourcePath.isBlank()) {
            ExecutableClassInfo info = new ExecutableClassInfo();
            info.fullClassName = fullClassName;
            info.displayName = displayName != null ? displayName : className;
            info.resourcePath = resourcePath;
            result.add(info);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private String findAndReadResource(Path extractDir, String resourcePath) throws IOException {
        Path filePath = extractDir.resolve(resourcePath);
        if (Files.exists(filePath)) {
            return Files.readString(filePath);
        }
        try (Stream<Path> walk = Files.walk(extractDir)) {
            Optional<Path> match = walk.filter(p -> p.toString().endsWith(resourcePath)).findFirst();
            if (match.isPresent()) {
                return Files.readString(match.get());
            }
        }
        return null;
    }

    private ApiScene createSceneFromData(UUID projectId, Map<String, Object> sceneData) {
        ApiScene scene = new ApiScene();
        scene.setId(UUID.randomUUID());
        scene.setProjectId(projectId);
        scene.setName((String) sceneData.getOrDefault("name", "Untitled"));
        scene.setDescription((String) sceneData.get("description"));
        scene.setVariables((List<Map<String, Object>>) sceneData.get("variables"));
        scene.setChangeVersion(0);
        return scene;
    }

    private void updateSceneFromData(ApiScene scene, Map<String, Object> sceneData) {
        ApiScene update = new ApiScene();
        update.setId(scene.getId());
        update.setName((String) sceneData.getOrDefault("name", scene.getName()));
        update.setVariables((List<Map<String, Object>>) sceneData.get("variables"));
        apiSceneMapper.updateById(update);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildSteps(Map<String, Object> sceneData) {
        List<Map<String, Object>> steps = (List<Map<String, Object>>) sceneData.get("steps");
        if (steps == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> stepData = steps.get(i);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("id", UUID.randomUUID());
            step.put("name", stepData.getOrDefault("name", "Step " + (i + 1)));
            step.put("stepType", stepData.getOrDefault("type", "http"));
            step.put("sortOrder", i);
            step.put("enabled", true);
            step.put("sourceType", "custom");
            step.put("requestConfig", stepData.get("requestConfig"));
            step.put("validators", stepData.get("validators"));
            step.put("extractors", stepData.get("extractors"));
            step.put("variables", List.of());
            result.add(step);
        }
        return result;
    }

    private void writeImportMapping(UUID projectId, UUID importRecordId, String fullClassName,
                                     String displayName, String targetType, UUID targetId, String action) {
        ApiImportMapping mapping = new ApiImportMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setProjectId(projectId);
        mapping.setImportRecordId(importRecordId);
        mapping.setSourceType("ryze_executable");
        mapping.setSourceId(fullClassName);
        mapping.setSourceName(displayName);
        mapping.setTargetType(targetType);
        mapping.setTargetId(targetId);
        mapping.setAction(action);
        importMappingMapper.insert(mapping);
    }

    private GitLabExecutableImportRespDTO.SceneItem toSceneItem(ApiScene scene, Map<String, Object> sceneData) {
        GitLabExecutableImportRespDTO.SceneItem item = new GitLabExecutableImportRespDTO.SceneItem();
        item.setId(scene.getId().toString());
        item.setName(scene.getName());
        List<Map<String, Object>> steps = (List<Map<String, Object>>) sceneData.get("steps");
        item.setStepCount(steps != null ? steps.size() : 0);
        return item;
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

    private void deleteTempDir(Path dir) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    static class ExecutableClassInfo {
        String fullClassName;
        String displayName;
        String resourcePath;
    }
}
