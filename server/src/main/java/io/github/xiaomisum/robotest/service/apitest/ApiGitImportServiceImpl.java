package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.apitest.scan.ScanIssue;
import io.github.xiaomisum.robotest.service.apitest.scan.ScanOutcome;
import io.github.xiaomisum.robotest.service.apitest.scan.ScannedClass;
import io.github.xiaomisum.robotest.service.apitest.scan.ScannedMethod;
import io.github.xiaomisum.robotest.service.apitest.scan.TestSourceScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 导入与解析实现（GitLab 集成详细设计 3.2 / 3.5，批次一 WP-3.2）。
 * <p>
 * 稳定键 upsert：模块按 source_id（包容器=完整包名、类模块=全限定名）、场景按 (repo_id, source_id)=类全限定名#方法名；
 * 重复导入结果一致（BR-1.8 / BR-1.11）。源侧已不存在的场景仅标记 repo_removed，不删除、不计 summary（BR-1.42）。
 * GitLab 拉取全部在事务外完成，导入写库为单事务（BR-1.15）；环境合并为 WP-4.1 占位（恒 skip）。
 */
@Slf4j
@Service
public class ApiGitImportServiceImpl implements ApiGitImportService {

    private static final String IMPORT_MODE_PLATFORM = "platform";
    private static final String SOURCE_TYPE_GITLAB = "gitlab";
    private static final String SOURCE_STATUS_IN_SYNC = "in_sync";
    private static final String SOURCE_STATUS_PARSE_FAILED = "parse_failed";
    private static final String SOURCE_STATUS_REPO_REMOVED = "repo_removed";
    private static final String PARSE_STATUS_OK = "ok";
    private static final String PARSE_STATUS_FAILED = "failed";
    private static final String HEAD_PROBE_OK = "ok";
    private static final String CHANGE_TYPE_IMPORT = "import";
    private static final String TARGET_TYPE_GIT_REPO = "git_repo";
    private static final String DETAIL_TYPE_PARSE_FAILED = "parseFailed";
    private static final String DETAIL_TYPE_ERROR_FAILED = "errorFailed";

    private static final String STAGE_IDLE = "idle";
    private static final String STAGE_FETCH = "fetch";
    private static final String STAGE_SCAN = "scan";
    private static final String STAGE_PARSE = "parse";
    private static final String STAGE_MERGE = "merge";
    private static final String STAGE_PERSIST = "persist";
    private static final String STAGE_DONE = "done";
    private static final String STAGE_FAILED = "failed";
    private static final Set<String> RUNNING_STAGES =
            Set.of(STAGE_FETCH, STAGE_SCAN, STAGE_PARSE, STAGE_MERGE, STAGE_PERSIST);

    private final ApiSceneMapper sceneMapper;
    private final ProjectModuleMapper moduleMapper;
    private final ApiGitRepoMapper gitRepoMapper;
    private final ApiChangeHistoryMapper changeHistoryMapper;
    private final TestSourceScanner scanner;
    private final GitLabApiClient gitLabApiClient;
    private final ProjectAccessGuard projectAccessGuard;
    private final TransactionTemplate transactionTemplate;
    private final Executor importExecutor;
    private final byte[] secretKey;
    /** 仓库 → 进度：进行中闸门（1000017708）与状态轮询的数据源，不落库 */
    private final ConcurrentMap<UUID, ImportProgress> progressMap = new ConcurrentHashMap<>();

    public ApiGitImportServiceImpl(ApiSceneMapper sceneMapper, ProjectModuleMapper moduleMapper,
            ApiGitRepoMapper gitRepoMapper, ApiChangeHistoryMapper changeHistoryMapper,
            TestSourceScanner scanner, GitLabApiClient gitLabApiClient, ProjectAccessGuard projectAccessGuard,
            PlatformTransactionManager transactionManager,
            @Qualifier("apiGitImportExecutor") Executor importExecutor,
            @Value("${robotest.env.secret-key:}") String base64SecretKey) {
        this.sceneMapper = sceneMapper;
        this.moduleMapper = moduleMapper;
        this.gitRepoMapper = gitRepoMapper;
        this.changeHistoryMapper = changeHistoryMapper;
        this.scanner = scanner;
        this.gitLabApiClient = gitLabApiClient;
        this.projectAccessGuard = projectAccessGuard;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.importExecutor = importExecutor;
        this.secretKey = SecretCryptoUtil.parseKey(base64SecretKey);
    }

    // ========== 预览 ==========

    @Override
    public ApiGitImportPreviewRespDTO preview(UUID projectId, UUID workspaceId, UUID userId, UUID repoId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiGitRepo repo = requireRepo(projectId, repoId);
        String token = decryptToken(repo);
        String branch = branchOf(repo);
        String headCommit = gitLabApiClient.branchHead(repo.getGitlabBaseUrl(), token, repo.getRepoPath(), branch);
        // commit 幂等闸门（详设 3.4）：跳过时不拉源码树、不扫描，经 gate 字段返回拦截结论
        if (gateBlocked(repo, headCommit)) {
            return blockedPreview(repo, branch, headCommit);
        }
        ImportPlan plan = buildPlan(repo, token, headCommit, null);
        return toPreviewDTO(projectId, repo, plan);
    }

    // ========== 导入 ==========

    @Override
    public ApiGitImportStatusRespDTO imports(UUID projectId, UUID workspaceId, UUID userId, UUID repoId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiGitRepo repo = requireRepo(projectId, repoId);
        String token = decryptToken(repo);
        String branch = branchOf(repo);
        String headCommit = gitLabApiClient.branchHead(repo.getGitlabBaseUrl(), token, repo.getRepoPath(), branch);
        // commit 幂等闸门（详设 3.4）：跳过即同步拒绝，不占用进行中闸门、不发起源码拉取
        if (gateBlocked(repo, headCommit)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_IMPORT_COMMIT_UP_TO_DATE);
        }
        ImportProgress progress = claimProgress(repoId);
        try {
            importExecutor.execute(() -> executeImport(projectId, userId, repoId, headCommit, progress));
        } catch (RejectedExecutionException e) {
            progressMap.remove(repoId, progress);
            // 并发导入超出承载上限，复用「存在进行中的导入」业务异常
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_IMPORT_IN_PROGRESS);
        }
        return toStatusDTO(progress);
    }

    /** 进度占位的原子抢占：同仓库存在进行中的导入即拒绝（1000017708） */
    private ImportProgress claimProgress(UUID repoId) {
        ImportProgress progress = new ImportProgress();
        progress.stage = STAGE_FETCH;
        while (true) {
            ImportProgress current = progressMap.get(repoId);
            if (current == null) {
                if (progressMap.putIfAbsent(repoId, progress) == null) {
                    return progress;
                }
                continue;
            }
            if (RUNNING_STAGES.contains(current.stage)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_IMPORT_IN_PROGRESS);
            }
            if (progressMap.replace(repoId, current, progress)) {
                return progress;
            }
            // 与并发导入竞争失败，重新判定
        }
    }

    private void executeImport(UUID projectId, UUID userId, UUID repoId, String headCommit, ImportProgress progress) {
        try {
            ApiGitRepo repo = gitRepoMapper.selectById(repoId);
            if (repo == null || !projectId.equals(repo.getProjectId())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND);
            }
            ImportPlan plan = buildPlan(repo, decryptToken(repo), headCommit, progress);
            stage(progress, STAGE_MERGE, plan.prepared.size(), plan.prepared.size());
            // 导入写库单事务：模块归属、场景 upsert、repo_removed 标记、变更审计与回写同批提交（BR-1.15）
            Map<String, Object> summary = transactionTemplate.execute(
                    status -> persistPlan(projectId, userId, repo, plan));
            progress.summary = summary;
            stage(progress, STAGE_DONE, plan.prepared.size(), plan.prepared.size());
        } catch (Exception e) {
            log.warn("GitLab 导入失败，repoId={}", repoId, e);
            progress.error = messageOf(e);
            progress.stage = STAGE_FAILED;
        }
    }

    // ========== 状态 ==========

    @Override
    public ApiGitImportStatusRespDTO status(UUID projectId, UUID workspaceId, UUID userId, UUID repoId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireRepo(projectId, repoId);
        ImportProgress progress = progressMap.get(repoId);
        return progress == null ? idleStatus() : toStatusDTO(progress);
    }

    private static ApiGitImportStatusRespDTO idleStatus() {
        ApiGitImportStatusRespDTO dto = new ApiGitImportStatusRespDTO();
        dto.setStage(STAGE_IDLE);
        dto.setProcessed(0);
        dto.setTotal(0);
        return dto;
    }

    private static ApiGitImportStatusRespDTO toStatusDTO(ImportProgress progress) {
        ApiGitImportStatusRespDTO dto = new ApiGitImportStatusRespDTO();
        dto.setStage(progress.stage);
        dto.setProcessed(progress.processed);
        dto.setTotal(progress.total);
        dto.setError(progress.error);
        dto.setSummary(progress.summary);
        return dto;
    }

    private static void stage(ImportProgress progress, String stage, int processed, int total) {
        if (progress != null) {
            progress.stage = stage;
            progress.processed = processed;
            progress.total = total;
        }
    }

    // ========== 计划构建：GitLab 拉取（扫描 + 套件解析）与库内比对 ==========

    private ImportPlan buildPlan(ApiGitRepo repo, String token, String headCommit, ImportProgress progress) {
        ImportPlan plan = new ImportPlan();
        plan.branch = branchOf(repo);
        // HEAD 由闸门评估时取定并透传，保证闸门比对与落库的 last_import_commit 为同一提交号
        plan.headCommit = headCommit;
        String baseUrl = repo.getGitlabBaseUrl();
        String repoPath = repo.getRepoPath();

        stage(progress, STAGE_FETCH, 0, 0);
        List<String> javaFiles = gitLabApiClient
                .listFiles(baseUrl, token, repoPath, plan.branch, repo.getTestSourceDir())
                .stream().filter(path -> path != null && path.toLowerCase().endsWith(".java")).toList();

        stage(progress, STAGE_SCAN, 0, javaFiles.size());
        List<TestSourceScanner.SourceFile> files = new ArrayList<>();
        int scanned = 0;
        for (String path : javaFiles) {
            // 文件缺失（枚举与读取之间被删除）计入真实错误明细，不中止整次扫描（详设 2.4）
            String content = gitLabApiClient.findFileContent(baseUrl, token, repoPath, plan.branch, path);
            if (content == null) {
                plan.failures.add(new Failure(null, DETAIL_TYPE_ERROR_FAILED, "文件读取失败：未找到 " + path, path));
            } else {
                files.add(new TestSourceScanner.SourceFile(path, content));
            }
            stage(progress, STAGE_SCAN, ++scanned, javaFiles.size());
        }
        ScanOutcome scan = scanner.scan(files);
        plan.classes = scan.classes();
        for (ScanIssue issue : scan.parseFailed()) {
            plan.failures.add(new Failure(issue.methodKey(), DETAIL_TYPE_PARSE_FAILED, issue.reason(), issue.filePath()));
        }
        for (ScanIssue issue : scan.errorFailed()) {
            plan.failures.add(new Failure(issue.methodKey(), DETAIL_TYPE_ERROR_FAILED, issue.reason(), issue.filePath()));
        }

        stage(progress, STAGE_PARSE, 0, plan.effectiveCases());
        if (IMPORT_MODE_PLATFORM.equals(repo.getImportMode())) {
            parsePlatformScenes(repo, token, plan, progress);
        } else {
            parseGitlabCiScenes(plan, progress);
        }

        loadExistingByKey(repo, plan);
        loadModuleLookups(repo, plan);
        return plan;
    }

    /** platform：逐用例拉取步骤模板并转换为平台场景内容（!include 由加载器内联，缺失 / 非法计解析失败，BR-1.9） */
    private void parsePlatformScenes(ApiGitRepo repo, String token, ImportPlan plan, ImportProgress progress) {
        RyzeSuiteYamlLoader yamlLoader = new RyzeSuiteYamlLoader(path ->
                gitLabApiClient.findFileContent(repo.getGitlabBaseUrl(), token, repo.getRepoPath(), plan.branch,
                        joinResource(repo.getResourceDir(), path)));
        int parsed = 0;
        int total = plan.effectiveCases();
        for (ScannedClass cls : plan.classes) {
            String fqn = cls.fullyQualifiedName();
            for (ScannedMethod method : cls.methods()) {
                String key = fqn + "#" + method.methodName();
                try {
                    Map<String, Object> scene = RyzeYamlToSceneConverter.convertSuite(
                            yamlLoader.loadSuite(method.yamlPath()));
                    String name = firstNonBlank(str(scene.get("name")), method.description(), method.methodName());
                    plan.prepared.add(new PreparedScene(key, fqn, method.yamlPath(), name,
                            asListOfMaps(scene.get("variables")), asListOfMaps(scene.get("steps"))));
                } catch (RyzeSuiteYamlLoader.LoadException e) {
                    plan.failures.add(new Failure(key, DETAIL_TYPE_PARSE_FAILED, e.getMessage(), method.yamlPath()));
                } catch (RuntimeException e) {
                    log.warn("套件转换失败，key={}", key, e);
                    plan.failures.add(new Failure(key, DETAIL_TYPE_ERROR_FAILED, "套件转换失败：" + messageOf(e),
                            method.yamlPath()));
                }
                stage(progress, STAGE_PARSE, ++parsed, total);
            }
        }
    }

    /** gitlab_ci：平台侧只读（BR-1.43），步骤恒空，名称与描述取 @Test description（BR-1.10） */
    private void parseGitlabCiScenes(ImportPlan plan, ImportProgress progress) {
        int parsed = 0;
        int total = plan.effectiveCases();
        for (ScannedClass cls : plan.classes) {
            String fqn = cls.fullyQualifiedName();
            for (ScannedMethod method : cls.methods()) {
                String name = firstNonBlank(method.description(), method.methodName());
                plan.prepared.add(new PreparedScene(fqn + "#" + method.methodName(), fqn,
                        cls.filePath() + "#" + method.methodName(), name, List.of(), List.of()));
                stage(progress, STAGE_PARSE, ++parsed, total);
            }
        }
    }

    /** 既有场景按稳定键取回；源侧已不存在（不在本次解析键集内）的以差集标记 */
    private void loadExistingByKey(ApiGitRepo repo, ImportPlan plan) {
        Set<String> keys = new LinkedHashSet<>();
        plan.prepared.forEach(prepared -> keys.add(prepared.key()));
        for (Failure failure : plan.failures) {
            if (failure.key() != null) {
                keys.add(failure.key());
            }
        }
        plan.existingByKey = new HashMap<>();
        for (ApiScene scene : sceneMapper.listByRepoAndSourceIds(repo.getId(), keys)) {
            plan.existingByKey.putIfAbsent(scene.getSourceId(), scene);
        }
        plan.staleIds = new ArrayList<>();
        for (ApiScene scene : sceneMapper.listByRepo(repo.getId())) {
            if (scene.getSourceId() != null && !keys.contains(scene.getSourceId())) {
                plan.staleIds.add(scene.getId());
            }
        }
        for (PreparedScene prepared : plan.prepared) {
            if (plan.existingByKey.containsKey(prepared.key())) {
                plan.sceneUpdate++;
            } else {
                plan.sceneCreate++;
            }
        }
        for (Failure failure : plan.failures) {
            if (DETAIL_TYPE_PARSE_FAILED.equals(failure.type())) {
                plan.parseFailed++;
            } else {
                plan.errorFailed++;
            }
        }
    }

    /** 模块既有映射预载：包容器按根级 source_id=包名，类模块按 source_id=全限定名（BR-1.8） */
    private void loadModuleLookups(ApiGitRepo repo, ImportPlan plan) {
        Set<String> packages = new LinkedHashSet<>();
        Set<String> fqns = new LinkedHashSet<>();
        for (ScannedClass cls : plan.classes) {
            if (hasText(cls.packageName())) {
                packages.add(cls.packageName());
            }
            fqns.add(cls.fullyQualifiedName());
        }
        plan.containerByPkg = new HashMap<>();
        for (ProjectModule module : moduleMapper.listRootBySourceIds(repo.getProjectId(), packages)) {
            plan.containerByPkg.putIfAbsent(module.getSourceId(), module);
        }
        plan.classModuleByFqn = new HashMap<>();
        for (ProjectModule module : moduleMapper.listByProjectAndSourceIds(repo.getProjectId(), fqns)) {
            plan.classModuleByFqn.putIfAbsent(module.getSourceId(), module);
        }
    }

    /**
     * 逐类解析包容器与类模块归属：先按 source_id 命中，类模块退化「同包容器 + 同简单名」认领并回填（BR-1.8）。
     *
     * @param persist true=导入：建缺节点、回填与归属纠偏落库；false=预览：仅计数，不产生任何写操作
     * @return 类全限定名 → 模块 ID（预览模式下新建节点 ID 为空，仅导入使用）
     */
    private Map<String, UUID> resolveModules(UUID projectId, List<ScannedClass> classes, ImportPlan plan, boolean persist) {
        Map<String, UUID> moduleIdsByFqn = new HashMap<>();
        Set<String> handledPackages = new HashSet<>();
        for (ScannedClass cls : classes) {
            String pkg = cls.packageName() == null ? "" : cls.packageName().trim();
            String fqn = cls.fullyQualifiedName();
            UUID containerId = null;
            if (!pkg.isBlank()) {
                ProjectModule container = plan.containerByPkg.get(pkg);
                if (container != null) {
                    if (handledPackages.add(pkg)) {
                        plan.counters.containersUpdate++;
                    }
                } else if (handledPackages.add(pkg)) {
                    plan.counters.containersCreate++;
                    container = newModule(projectId, null, pkg, pkg);
                    if (persist) {
                        moduleMapper.insert(container);
                    } else {
                        // 预览占位 ID：不落库，仅保持认领查询的父链语义
                        container.setId(UUID.randomUUID());
                    }
                    plan.containerByPkg.put(pkg, container);
                } else {
                    container = plan.containerByPkg.get(pkg);
                }
                containerId = container.getId();
            }
            ProjectModule classModule = plan.classModuleByFqn.get(fqn);
            if (classModule != null) {
                plan.counters.classesUpdate++;
                if (persist && !Objects.equals(classModule.getParentId(), containerId)) {
                    moduleMapper.reparent(classModule.getId(), containerId);
                }
                moduleIdsByFqn.put(fqn, classModule.getId());
                continue;
            }
            ProjectModule claimed = moduleMapper.findByNameAndParent(projectId, containerId, cls.className());
            if (claimed != null) {
                if (!hasText(claimed.getSourceId())) {
                    plan.counters.classesBackfill++;
                    if (persist) {
                        moduleMapper.backfillSourceId(claimed.getId(), fqn);
                    }
                } else {
                    plan.counters.classesUpdate++;
                }
                moduleIdsByFqn.put(fqn, claimed.getId());
                continue;
            }
            plan.counters.classesCreate++;
            ProjectModule created = newModule(projectId, containerId, cls.className(), fqn);
            if (persist) {
                moduleMapper.insert(created);
            }
            moduleIdsByFqn.put(fqn, created.getId());
        }
        return moduleIdsByFqn;
    }

    private static ProjectModule newModule(UUID projectId, UUID parentId, String name, String sourceId) {
        ProjectModule module = new ProjectModule();
        module.setProjectId(projectId);
        module.setParentId(parentId);
        module.setName(name);
        module.setSourceId(sourceId);
        module.setSortOrder(0);
        return module;
    }

    // ========== 预览组装 ==========

    private ApiGitImportPreviewRespDTO toPreviewDTO(UUID projectId, ApiGitRepo repo, ImportPlan plan) {
        resolveModules(projectId, plan.classes, plan, false);
        ApiGitImportPreviewRespDTO dto = new ApiGitImportPreviewRespDTO();
        dto.setRepoId(repo.getId());
        dto.setBranch(plan.branch);
        dto.setHeadCommit(plan.headCommit);
        ApiGitImportPreviewRespDTO.GateDTO gate = new ApiGitImportPreviewRespDTO.GateDTO();
        gate.setBlocked(false);
        gate.setReason(null);
        dto.setGate(gate);
        dto.setClasses(plan.classes.size());
        dto.setEffectiveCases(plan.effectiveCases());

        ApiGitImportPreviewRespDTO.CounterDTO containers = new ApiGitImportPreviewRespDTO.CounterDTO();
        containers.setCreate(plan.counters.containersCreate);
        containers.setUpdate(plan.counters.containersUpdate);
        ApiGitImportPreviewRespDTO.ClassesCounterDTO classes = new ApiGitImportPreviewRespDTO.ClassesCounterDTO();
        classes.setCreate(plan.counters.classesCreate);
        classes.setUpdate(plan.counters.classesUpdate);
        classes.setBackfill(plan.counters.classesBackfill);
        ApiGitImportPreviewRespDTO.ModulesDTO modules = new ApiGitImportPreviewRespDTO.ModulesDTO();
        modules.setContainers(containers);
        modules.setClasses(classes);
        dto.setModules(modules);

        ApiGitImportPreviewRespDTO.ScenesDTO scenes = new ApiGitImportPreviewRespDTO.ScenesDTO();
        scenes.setCreate(plan.sceneCreate);
        scenes.setUpdate(plan.sceneUpdate);
        scenes.setParseFailed(plan.parseFailed);
        scenes.setErrorFailed(plan.errorFailed);
        dto.setScenes(scenes);

        ApiGitImportPreviewRespDTO.EnvironmentDTO environment = new ApiGitImportPreviewRespDTO.EnvironmentDTO();
        environment.setAction("skip");
        dto.setEnvironment(environment);

        List<ApiGitImportPreviewRespDTO.DetailDTO> details = new ArrayList<>();
        for (Failure failure : plan.failures) {
            ApiGitImportPreviewRespDTO.DetailDTO detail = new ApiGitImportPreviewRespDTO.DetailDTO();
            detail.setType(failure.type());
            detail.setSceneKey(failure.key());
            detail.setReason(failure.reason());
            details.add(detail);
        }
        dto.setDetails(details);
        return dto;
    }

    // ========== 导入落库（事务内） ==========

    private Map<String, Object> persistPlan(UUID projectId, UUID userId, ApiGitRepo repo, ImportPlan plan) {
        Map<String, UUID> moduleIds = resolveModules(projectId, plan.classes, plan, true);
        boolean platform = IMPORT_MODE_PLATFORM.equals(repo.getImportMode());
        boolean overwrite = Boolean.TRUE.equals(repo.getReimportOverwrite());
        for (PreparedScene prepared : plan.prepared) {
            UUID moduleId = moduleIds.get(prepared.fqn());
            ApiScene existing = plan.existingByKey.get(prepared.key());
            if (existing == null) {
                sceneMapper.insert(newScene(projectId, repo.getId(), moduleId, prepared, platform));
            } else {
                sceneMapper.updateById(buildUpdateCarrier(existing, moduleId, prepared, platform, overwrite));
            }
        }
        for (Failure failure : plan.failures) {
            if (failure.key() == null) {
                continue;
            }
            ApiScene existing = plan.existingByKey.get(failure.key());
            if (existing == null) {
                continue;
            }
            sceneMapper.updateById(buildDegradedCarrier(existing, moduleIds.get(fqnOf(failure.key())), failure));
        }
        // 源侧已不存在：仅标记，不删除、不计 summary（BR-1.42）
        sceneMapper.markScenesRepoRemoved(plan.staleIds, SOURCE_STATUS_REPO_REMOVED);

        Map<String, Object> summary = summaryOf(plan);
        ApiChangeHistory history = new ApiChangeHistory();
        history.setProjectId(projectId);
        history.setTargetType(TARGET_TYPE_GIT_REPO);
        history.setTargetId(repo.getId());
        history.setVersion(changeHistoryMapper.selectMaxVersion(TARGET_TYPE_GIT_REPO, repo.getId()) + 1);
        history.setChangeType(CHANGE_TYPE_IMPORT);
        history.setContentDiff(summary);
        history.setCreatedBy(userId);
        changeHistoryMapper.insert(history);

        LocalDateTime now = LocalDateTime.now();
        ApiGitRepo writeback = new ApiGitRepo();
        writeback.setId(repo.getId());
        writeback.setHeadCommit(plan.headCommit);
        writeback.setHeadCheckedAt(now);
        writeback.setHeadProbeStatus(HEAD_PROBE_OK);
        writeback.setLastImportAt(now);
        writeback.setLastImportCommit(plan.headCommit);
        writeback.setLastImportSummary(summary);
        gitRepoMapper.updateById(writeback);
        return summary;
    }

    private ApiScene newScene(UUID projectId, UUID repoId, UUID moduleId, PreparedScene prepared, boolean platform) {
        ApiScene scene = new ApiScene();
        scene.setProjectId(projectId);
        scene.setModuleId(moduleId);
        scene.setName(prepared.name());
        if (!platform) {
            scene.setDescription(prepared.name());
        }
        scene.setStatus(SceneSettingsValidator.SCENE_STATUS_DRAFT);
        scene.setProcessors(List.of());
        scene.setChangeVersion(1);
        scene.setVariables(prepared.variables());
        scene.setSteps(prepared.steps());
        scene.setSourceType(SOURCE_TYPE_GITLAB);
        scene.setRepoId(repoId);
        scene.setSourceId(prepared.key());
        scene.setSourceRef(prepared.sourceRef());
        scene.setSourceStatus(SOURCE_STATUS_IN_SYNC);
        scene.setParseStatus(PARSE_STATUS_OK);
        scene.setParseIssues(List.of());
        return scene;
    }

    /**
     * 命中既有场景的更新载体（C11 部分更新）：来源元数据与模块归属恒刷新；
     * reimport_overwrite 开启时以源侧内容覆盖 platform 场景 name / variables / steps，关闭时保持平台侧现状（详设 3.5）；
     * gitlab_ci 场景平台侧只读，name / description 恒按源侧刷新。import 不递增 changeVersion。
     */
    private ApiScene buildUpdateCarrier(ApiScene existing, UUID moduleId, PreparedScene prepared,
            boolean platform, boolean overwrite) {
        ApiScene carrier = new ApiScene();
        carrier.setId(existing.getId());
        carrier.setModuleId(moduleId);
        carrier.setSourceRef(prepared.sourceRef());
        carrier.setSourceStatus(SOURCE_STATUS_IN_SYNC);
        carrier.setParseStatus(PARSE_STATUS_OK);
        carrier.setParseIssues(List.of());
        if (!platform) {
            carrier.setName(prepared.name());
            carrier.setDescription(prepared.name());
        } else if (overwrite) {
            carrier.setName(prepared.name());
            carrier.setVariables(prepared.variables());
            carrier.setSteps(prepared.steps());
        }
        return carrier;
    }

    /** 解析失败降级：既有场景保留原状，仅刷新解析状态与问题清单（BR-1.12） */
    private ApiScene buildDegradedCarrier(ApiScene existing, UUID moduleId, Failure failure) {
        ApiScene carrier = new ApiScene();
        carrier.setId(existing.getId());
        if (moduleId != null) {
            carrier.setModuleId(moduleId);
        }
        carrier.setSourceStatus(SOURCE_STATUS_PARSE_FAILED);
        carrier.setParseStatus(PARSE_STATUS_FAILED);
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("type", failure.type());
        issue.put("message", failure.reason());
        issue.put("ref", failure.ref());
        carrier.setParseIssues(List.of(issue));
        return carrier;
    }

    private static Map<String, Object> summaryOf(ImportPlan plan) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("created", plan.sceneCreate);
        summary.put("updated", plan.sceneUpdate);
        summary.put("parseFailed", plan.parseFailed);
        summary.put("errorFailed", plan.errorFailed);
        // skipped 键位保留（详设 2.1.1）：当前无跳过场景来源；源侧缺失标 repo_removed 不计 summary
        summary.put("skipped", 0);
        return summary;
    }

    // ========== 工具 ==========

    private ApiGitRepo requireRepo(UUID projectId, UUID repoId) {
        ApiGitRepo repo = gitRepoMapper.selectById(repoId);
        if (repo == null || !projectId.equals(repo.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND);
        }
        return repo;
    }

    /** 沿用已存令牌：解密失败（密钥轮换 / 密文损坏）须重新配置令牌后重试 */
    private String decryptToken(ApiGitRepo repo) {
        String plain = secretKey == null ? null : SecretCryptoUtil.decrypt(secretKey, repo.getTokenCipher());
        if (!hasText(plain)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(),
                    "已存访问令牌无法解密，请重新配置令牌");
        }
        return plain;
    }

    private static String branchOf(ApiGitRepo repo) {
        return hasText(repo.getDefaultBranch()) ? repo.getDefaultBranch() : "master";
    }

    /**
     * commit 幂等闸门（详设 3.4，双模式同一判定）：目标分支 HEAD 与上次导入一致
     * 且上次无遗留真实错误时跳过；解析失败（parseFailed）是预期内结果，不参与闸门。
     */
    private static boolean gateBlocked(ApiGitRepo repo, String headCommit) {
        if (!hasText(repo.getLastImportCommit()) || !headCommit.equals(repo.getLastImportCommit())) {
            return false;
        }
        Map<String, Object> summary = repo.getLastImportSummary();
        Object errorFailed = summary == null ? null : summary.get("errorFailed");
        return !(errorFailed instanceof Number number) || number.intValue() == 0;
    }

    /** 闸门拦截的预览响应：不拉取源码树、不扫描，上次导入汇总由前端从仓库配置响应取用 */
    private static ApiGitImportPreviewRespDTO blockedPreview(ApiGitRepo repo, String branch, String headCommit) {
        ApiGitImportPreviewRespDTO dto = new ApiGitImportPreviewRespDTO();
        dto.setRepoId(repo.getId());
        dto.setBranch(branch);
        dto.setHeadCommit(headCommit);
        ApiGitImportPreviewRespDTO.GateDTO gate = new ApiGitImportPreviewRespDTO.GateDTO();
        gate.setBlocked(true);
        gate.setReason(ErrorCodeConstants.API_GIT_IMPORT_COMMIT_UP_TO_DATE.msg());
        dto.setGate(gate);
        dto.setDetails(List.of());
        return dto;
    }

    private static String fqnOf(String key) {
        int idx = key.lastIndexOf('#');
        return idx < 0 ? key : key.substring(0, idx);
    }

    private static String joinResource(String resourceDir, String path) {
        String dir = trimSlashes(resourceDir);
        String file = trimSlashes(path);
        return dir.isEmpty() ? file : dir + "/" + file;
    }

    private static String trimSlashes(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : List.of();
    }

    // ========== 内部结构 ==========

    /** 解析成功、待 upsert 的场景：key=稳定键，fqn 定位类模块，sourceRef 为源侧引用（模板路径 / 方法路径） */
    record PreparedScene(String key, String fqn, String sourceRef, String name,
            List<Map<String, Object>> variables, List<Map<String, Object>> steps) {
    }

    /** 解析失败明细：key 为空表示文件级失败（无关联场景） */
    record Failure(String key, String type, String reason, String ref) {
    }

    static final class ModuleCounters {

        int containersCreate;
        int containersUpdate;
        int classesCreate;
        int classesUpdate;
        int classesBackfill;
    }

    /** 导入计划：GitLab 拉取（扫描 + 套件解析）与库内比对的产物，预览与导入共用 */
    static final class ImportPlan {

        String branch;
        String headCommit;
        List<ScannedClass> classes = List.of();
        List<PreparedScene> prepared = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        List<UUID> staleIds = List.of();
        Map<String, ApiScene> existingByKey = Map.of();
        Map<String, ProjectModule> containerByPkg = Map.of();
        Map<String, ProjectModule> classModuleByFqn = Map.of();
        ModuleCounters counters = new ModuleCounters();
        int sceneCreate;
        int sceneUpdate;
        int parseFailed;
        int errorFailed;

        int effectiveCases() {
            int total = 0;
            for (ScannedClass cls : classes) {
                total += cls.methods().size();
            }
            return total;
        }
    }

    /** 导入进度（内存态，轮询读取，不落库） */
    static final class ImportProgress {

        volatile String stage = STAGE_IDLE;
        volatile int processed;
        volatile int total;
        volatile String error;
        volatile Map<String, Object> summary;
    }
}
