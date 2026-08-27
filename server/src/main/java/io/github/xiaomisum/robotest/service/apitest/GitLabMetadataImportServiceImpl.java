package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabSyncConfigReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabMetadataImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabMetadataListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabSyncConfigRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabSyncHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.GitLabRepository;
import io.github.xiaomisum.robotest.model.entity.apitest.GitLabSyncHistory;
import io.github.xiaomisum.robotest.model.entity.apitest.GitLabTestClassMetadata;
import io.github.xiaomisum.robotest.repository.apitest.GitLabRepositoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.GitLabSyncHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.GitLabTestClassMetadataMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;

import java.io.*;
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
public class GitLabMetadataImportServiceImpl implements GitLabMetadataImportService {

    private static final Logger log = LoggerFactory.getLogger(GitLabMetadataImportServiceImpl.class);

    private static final Pattern GITLAB_URL_PATTERN = Pattern.compile("^(https?://[^/]+)/(.+?)\\.git$");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^(\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*)" +
            "(?:public\\s+)?(?:abstract\\s+)?class\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@(\\w+)(?:\\(([^)]*)\\))?");
    private static final Pattern RESOURCE_PATH_PATTERN = Pattern.compile(
            "resourcePath\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^(\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*)" +
            "(?:public\\s+)?void\\s+(\\w+)\\s*\\(",
            Pattern.MULTILINE);
    private static final Pattern DISPLAY_NAME_PATTERN = Pattern.compile(
            "/\\*\\*\\s*(.+?)\\s*\\*/", Pattern.DOTALL);

    @Resource
    private GitLabRepositoryMapper gitLabRepositoryMapper;

    @Resource
    private GitLabTestClassMetadataMapper metadataMapper;

    @Resource
    private GitLabSyncHistoryMapper syncHistoryMapper;

    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Value("${robotest.env.secret-key:}")
    private String secretKeyBase64;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GitLabMetadataImportRespDTO importMetadata(UUID projectId, UUID workspaceId, UUID userId,
                                                       UUID repositoryId) {
        return doImport(projectId, workspaceId, userId, repositoryId);
    }

    @Override
    public PageResult<GitLabMetadataListItemRespDTO> fetchMetadataPage(UUID projectId, UUID workspaceId, UUID userId,
                                                                       UUID repositoryId, Boolean isExecutable,
                                                                       String keyword, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireRepository(projectId, workspaceId, repositoryId);

        PageResult<GitLabTestClassMetadata> page = metadataMapper.selectPageByRepository(
                repositoryId, isExecutable, keyword, pageParam);
        return new PageResult<>(page.getList().stream().map(this::toRespDTO).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GitLabMetadataImportRespDTO syncMetadata(UUID projectId, UUID workspaceId, UUID userId,
                                                     UUID repositoryId) {
        return doImport(projectId, workspaceId, userId, repositoryId);
    }

    @Override
    public List<GitLabSyncHistoryItemRespDTO> fetchSyncHistory(UUID projectId, UUID workspaceId, UUID userId,
                                                                UUID repositoryId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireRepository(projectId, workspaceId, repositoryId);

        List<GitLabSyncHistory> historyList = syncHistoryMapper.selectListByRepositoryId(repositoryId);
        return historyList.stream().map(this::toSyncHistoryRespDTO).toList();
    }

    @Override
    public GitLabSyncConfigRespDTO fetchSyncConfig(UUID projectId, UUID workspaceId, UUID userId,
                                                    UUID repositoryId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        GitLabRepository repo = requireRepository(projectId, workspaceId, repositoryId);

        GitLabSyncConfigRespDTO config = new GitLabSyncConfigRespDTO();
        config.setAutoSyncEnabled(Boolean.TRUE.equals(repo.getAutoSyncEnabled()));
        config.setTestSourcePath(repo.getTestSourcePath());
        config.setAnnotationFilter(repo.getAnnotationFilter());
        config.setOnlyWithResourcePath(Boolean.TRUE.equals(repo.getOnlyWithResourcePath()));
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSyncConfig(UUID projectId, UUID workspaceId, UUID userId, UUID repositoryId,
                                     GitLabSyncConfigReqDTO config) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireRepository(projectId, workspaceId, repositoryId);

        GitLabRepository update = new GitLabRepository();
        update.setId(repositoryId);
        update.setAutoSyncEnabled(config.getAutoSyncEnabled());
        update.setTestSourcePath(config.getTestSourcePath());
        update.setAnnotationFilter(config.getAnnotationFilter());
        update.setOnlyWithResourcePath(config.getOnlyWithResourcePath());
        gitLabRepositoryMapper.updateById(update);
        return true;
    }

    @SuppressWarnings("unchecked")
    private GitLabMetadataImportRespDTO doImport(UUID projectId, UUID workspaceId, UUID userId,
                                                  UUID repositoryId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        GitLabRepository repo = requireRepository(projectId, workspaceId, repositoryId);

        byte[] key = requireCipherKey();
        String token = SecretCryptoUtil.decrypt(key, repo.getAccessTokenCipher());
        if (token == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_TOKEN_INVALID);
        }

        // 1. 解析 GitLab API 基础路径
        Matcher urlMatcher = GITLAB_URL_PATTERN.matcher(repo.getRepoUrl());
        if (!urlMatcher.matches()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "仓库地址格式不合法");
        }
        String apiBase = urlMatcher.group(1) + "/api/v4";
        String projectPath = URLDecoder.decode(urlMatcher.group(2), StandardCharsets.UTF_8);
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8).replace("+", "%2F");

        try {
            // 2. 获取最新 commit SHA
            String commitSha = fetchLatestCommitSha(apiBase, encodedPath, token, repo.getBranch());

            // 3. 下载源码 ZIP 并扫描
            Path tempDir = Files.createTempDirectory("gitlab-meta-");
            try {
                List<JavaFileMetadata> scanned = downloadAndScan(apiBase, encodedPath, token,
                        repo.getBranch(), repo.getTestSourcePath(), repo.getAnnotationFilter(), tempDir);

                // 4. 全量覆盖写入元数据
                // Count existing for diff
                List<GitLabTestClassMetadata> existingList = metadataMapper.selectListByRepositoryId(repositoryId);
                Set<String> existingClassNames = existingList.stream()
                        .map(GitLabTestClassMetadata::getFullClassName).collect(java.util.stream.Collectors.toSet());
                Set<String> newClassNames = scanned.stream()
                        .map(m -> m.fullClassName).collect(java.util.stream.Collectors.toSet());

                metadataMapper.deleteByRepository(repositoryId);
                int methodCount = 0;
                int executableCount = 0;
                for (JavaFileMetadata meta : scanned) {
                    GitLabTestClassMetadata entity = toEntity(repositoryId, meta);
                    metadataMapper.insert(entity);
                    methodCount += meta.methods.size();
                    if (Boolean.TRUE.equals(meta.isExecutable)) {
                        executableCount++;
                    }
                }

                // 5. 更新仓库同步信息
                GitLabRepository update = new GitLabRepository();
                update.setId(repositoryId);
                update.setLastMetadataSyncAt(LocalDateTime.now());
                update.setLastCommitSha(commitSha);
                gitLabRepositoryMapper.updateById(update);

                // 6. 记录同步历史
                GitLabSyncHistory history = new GitLabSyncHistory();
                history.setId(UUID.randomUUID());
                history.setRepositoryId(repositoryId);
                history.setSyncAt(LocalDateTime.now());
                history.setClassCount(scanned.size());
                history.setMethodCount(methodCount);
                history.setCommitSha(commitSha);
                history.setStatus("success");
                syncHistoryMapper.insert(history);

                // 7. 返回结果
                int addCount = (int) newClassNames.stream().filter(c -> !existingClassNames.contains(c)).count();
                int removeCount = (int) existingClassNames.stream().filter(c -> !newClassNames.contains(c)).count();
                int modifyCount = scanned.size() - addCount;

                GitLabMetadataImportRespDTO result = new GitLabMetadataImportRespDTO();
                result.setClassCount(scanned.size());
                result.setMethodCount(methodCount);
                result.setExecutableCount(executableCount);
                result.setCommitSha(commitSha);
                result.setAddCount(addCount);
                result.setModifyCount(modifyCount);
                result.setRemoveCount(removeCount);
                return result;
            } finally {
                deleteTempDir(tempDir);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("元数据导入失败：repositoryId={}", repositoryId, e);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GITLAB_REPO_UNREACHABLE,
                    "元数据导入失败：" + e.getMessage());
        }
    }

    private String fetchLatestCommitSha(String apiBase, String encodedPath, String token, String branch)
            throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/projects/" + encodedPath
                        + "/repository/commits/" + URLEncoder.encode(branch, StandardCharsets.UTF_8)
                                .replace("+", "%2F")))
                .header("Private-Token", token)
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }
        Map<String, Object> body = JsonUtils.parseObject(response.body(), Map.class);
        Object sha = body.get("id");
        return sha != null ? sha.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private List<JavaFileMetadata> downloadAndScan(String apiBase, String encodedPath, String token,
                                                    String branch, String testSourcePath,
                                                    String annotationFilter, Path tempDir)
            throws Exception {
        // 下载 ZIP archive
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
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

        // 解压 ZIP
        Path extractDir = tempDir.resolve("src");
        Files.createDirectories(extractDir);
        try (ZipInputStream zis = new ZipInputStream(response.body())) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path filePath = extractDir.resolve(entry.getName());
                if (!filePath.startsWith(extractDir)) {
                    continue; // 路径穿越保护
                }
                Files.createDirectories(filePath.getParent());
                Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // 扫描 Java 文件
        List<JavaFileMetadata> result = new ArrayList<>();
        Set<String> annotationFilterSet = parseAnnotationFilter(annotationFilter);
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
                            JavaFileMetadata meta = parseJavaFile(content, scanRoot.relativize(p).toString());
                            if (meta != null && matchesAnnotationFilter(meta.classAnnotations, annotationFilterSet)) {
                                result.add(meta);
                            }
                        } catch (Exception e) {
                            log.warn("解析 Java 文件失败：{}", p, e);
                        }
                    });
        }
        return result;
    }

    private Set<String> parseAnnotationFilter(String annotationFilter) {
        if (annotationFilter == null || annotationFilter.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(annotationFilter.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith("@") ? s.substring(1) : s)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean matchesAnnotationFilter(List<Map<String, Object>> classAnnotations, Set<String> filterSet) {
        if (filterSet.isEmpty()) {
            return true;
        }
        return classAnnotations.stream()
                .anyMatch(ann -> {
                    String name = (String) ann.get("name");
                    return name != null && filterSet.contains(name);
                });
    }

    JavaFileMetadata parseJavaFile(String content, String relativePath) {
        // 提取包名
        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(content);
        String packageName = pkgMatcher.find() ? pkgMatcher.group(1) : null;

        // 提取类声明 + 类注解
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        if (!classMatcher.find()) {
            return null;
        }
        String classAnnotationsBlock = classMatcher.group(1);
        String className = classMatcher.group(2);
        String fullClassName = packageName != null ? packageName + "." + className : className;

        // 解析类注解
        List<Map<String, Object>> classAnnotations = parseAnnotations(classAnnotationsBlock);

        // 提取 display name（类注释）
        String displayName = null;
        Matcher commentMatcher = DISPLAY_NAME_PATTERN.matcher(content);
        // 查找类声明前最近的注释
        int classStart = classMatcher.start();
        String beforeClass = content.substring(0, classStart);
        Matcher commentBeforeClass = DISPLAY_NAME_PATTERN.matcher(beforeClass);
        if (commentBeforeClass.find()) {
            displayName = commentBeforeClass.group(1).trim().split("\\n")[0].trim();
        }

        // 提取 resourcePath
        String resourcePath = null;
        Matcher rpMatcher = RESOURCE_PATH_PATTERN.matcher(content);
        if (rpMatcher.find()) {
            resourcePath = rpMatcher.group(1);
        }

        // 提取方法
        List<Map<String, Object>> methods = new ArrayList<>();
        Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        while (methodMatcher.find()) {
            String methodAnnotationsBlock = methodMatcher.group(1);
            String methodName = methodMatcher.group(2);
            List<String> methodAnnotationNames = extractAnnotationNames(methodAnnotationsBlock);
            // 只保留测试方法（含 @Test 或 @RyzeTest）
            if (methodAnnotationNames.stream().anyMatch(a -> a.equals("Test") || a.equals("RyzeTest"))) {
                Map<String, Object> method = new LinkedHashMap<>();
                method.put("name", methodName);
                method.put("annotations", methodAnnotationNames);
                methods.add(method);
            }
        }

        JavaFileMetadata meta = new JavaFileMetadata();
        meta.fullClassName = fullClassName;
        meta.classAnnotations = classAnnotations;
        meta.displayName = displayName;
        meta.description = displayName;
        meta.resourcePath = resourcePath;
        meta.isExecutable = resourcePath != null && !resourcePath.isBlank();
        meta.methods = methods;
        meta.relativePath = relativePath;
        return meta;
    }

    private List<Map<String, Object>> parseAnnotations(String block) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (block == null || block.isBlank()) {
            return result;
        }
        Matcher m = ANNOTATION_PATTERN.matcher(block);
        while (m.find()) {
            Map<String, Object> ann = new LinkedHashMap<>();
            ann.put("name", m.group(1));
            if (m.group(2) != null) {
                ann.put("params", m.group(2));
            }
            result.add(ann);
        }
        return result;
    }

    private List<String> extractAnnotationNames(String block) {
        List<String> names = new ArrayList<>();
        if (block == null || block.isBlank()) {
            return names;
        }
        Matcher m = ANNOTATION_PATTERN.matcher(block);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private GitLabTestClassMetadata toEntity(UUID repositoryId, JavaFileMetadata meta) {
        GitLabTestClassMetadata entity = new GitLabTestClassMetadata();
        entity.setRepositoryId(repositoryId);
        entity.setFullClassName(meta.fullClassName);
        entity.setClassAnnotations(JsonUtils.toJsonString(meta.classAnnotations));
        entity.setDisplayName(meta.displayName);
        entity.setDescription(meta.description);
        entity.setResourcePath(meta.resourcePath);
        entity.setIsExecutable(meta.isExecutable);
        entity.setMethods(JsonUtils.toJsonString(meta.methods));
        return entity;
    }

    private GitLabMetadataListItemRespDTO toRespDTO(GitLabTestClassMetadata entity) {
        GitLabMetadataListItemRespDTO dto = new GitLabMetadataListItemRespDTO();
        dto.setId(entity.getId().toString());
        dto.setFullClassName(entity.getFullClassName());
        dto.setDisplayName(entity.getDisplayName());
        dto.setDescription(entity.getDescription());
        dto.setResourcePath(entity.getResourcePath());
        dto.setIsExecutable(entity.getIsExecutable());
        // 解析 methods JSON
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawMethods = JsonUtils.parseObject(entity.getMethods(), List.class);
            List<GitLabMetadataListItemRespDTO.MethodItem> methodItems = new ArrayList<>();
            if (rawMethods != null) {
                for (Map<String, Object> raw : rawMethods) {
                    GitLabMetadataListItemRespDTO.MethodItem item = new GitLabMetadataListItemRespDTO.MethodItem();
                    item.setName((String) raw.get("name"));
                    item.setDisplayName((String) raw.get("displayName"));
                    Object anns = raw.get("annotations");
                    if (anns instanceof List<?>) {
                        item.setAnnotations(((List<?>) anns).stream()
                                .map(Object::toString).toList());
                    }
                    methodItems.add(item);
                }
            }
            dto.setMethods(methodItems);
        } catch (Exception e) {
            dto.setMethods(List.of());
        }
        return dto;
    }

    private GitLabRepository requireRepository(UUID projectId, UUID workspaceId, UUID repositoryId) {
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

    // 内部数据结构
    static class JavaFileMetadata {
        String fullClassName;
        List<Map<String, Object>> classAnnotations;
        String displayName;
        String description;
        String resourcePath;
        boolean isExecutable;
        List<Map<String, Object>> methods;
        String relativePath;
    }

    private GitLabSyncHistoryItemRespDTO toSyncHistoryRespDTO(GitLabSyncHistory entity) {
        GitLabSyncHistoryItemRespDTO dto = new GitLabSyncHistoryItemRespDTO();
        dto.setId(entity.getId().toString());
        dto.setSyncAt(entity.getSyncAt() != null ? entity.getSyncAt().toString() : null);
        dto.setClassCount(entity.getClassCount());
        dto.setMethodCount(entity.getMethodCount());
        dto.setCommitSha(entity.getCommitSha());
        dto.setStatus(entity.getStatus());
        return dto;
    }
}
