package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.apitest.scan.ScanOutcome;
import io.github.xiaomisum.robotest.service.apitest.scan.ScannedClass;
import io.github.xiaomisum.robotest.service.apitest.scan.ScannedMethod;
import io.github.xiaomisum.robotest.service.apitest.scan.TestSourceScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import xyz.migoo.framework.common.exception.ServiceException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导入服务测试：稳定键 upsert、reimportOverwrite 覆盖策略、repo_removed 标记、解析失败降级与并发闸门（详设 3.5）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiGitImportServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REPO_ID = UUID.randomUUID();
    private static final byte[] SECRET_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String BASE64_KEY = Base64.getEncoder().encodeToString(SECRET_KEY);

    private static final String JAVA_PATH = "src/test/java/com/example/OrderQueryTest.java";
    private static final String YAML_PATH = "src/test/resources/order/query_status.yaml";
    private static final String SCENE_KEY = "com.example.OrderQueryTest#queryStatus";
    private static final String SUITE_YAML = """
            title: 查询订单状态
            variables:
              tenant: '2'
            children:
              - title: 查询订单
                testclass: http
                config:
                  base_url: http://example.com
                  method: GET
                  path: /order/query
            """;

    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ProjectModuleMapper moduleMapper;
    @Mock
    private ApiGitRepoMapper gitRepoMapper;
    @Mock
    private ApiEnvironmentMapper environmentMapper;
    @Mock
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Mock
    private TestSourceScanner scanner;
    @Mock
    private GitLabApiClient gitLabApiClient;
    @Mock
    private ApiGitRenameDetector renameDetector;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final Map<String, String> remoteFiles = new HashMap<>();
    private ApiGitImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = newService(Runnable::run);
        stubCommon();
    }

    private ApiGitImportServiceImpl newService(java.util.concurrent.Executor executor) {
        return new ApiGitImportServiceImpl(sceneMapper, moduleMapper, gitRepoMapper, environmentMapper,
                changeHistoryMapper, scanner, gitLabApiClient, renameDetector, projectAccessGuard,
                transactionManager, executor, BASE64_KEY);
    }

    private void stubCommon() {
        remoteFiles.clear();
        remoteFiles.put(JAVA_PATH, "public class OrderQueryTest {}");
        remoteFiles.put(YAML_PATH, SUITE_YAML);
        lenient().when(gitLabApiClient.branchHead(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("head123");
        lenient().when(gitLabApiClient.listFiles(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(JAVA_PATH));
        lenient().when(gitLabApiClient.findFileContent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> remoteFiles.get(invocation.getArgument(4, String.class)));
        lenient().when(scanner.scan(any())).thenReturn(scanOutcome());
        lenient().when(moduleMapper.listRootBySourceIds(any(), any())).thenReturn(List.of());
        lenient().when(moduleMapper.listByProjectAndSourceIds(any(), any())).thenReturn(List.of());
        lenient().when(moduleMapper.findByNameAndParent(any(), any(), anyString())).thenReturn(null);
        lenient().when(sceneMapper.listByRepo(any())).thenReturn(List.of());
        lenient().when(changeHistoryMapper.selectMaxVersion(anyString(), any())).thenReturn(0);
    }

    private static ScanOutcome scanOutcome() {
        ScannedClass cls = new ScannedClass(JAVA_PATH, "com.example", "OrderQueryTest",
                List.of(new ScannedMethod("queryStatus", "查询订单状态", "order/query_status.yaml")));
        return new ScanOutcome(List.of(cls), List.of(), List.of());
    }

    private ApiGitRepo storedRepo(boolean reimportOverwrite) {
        ApiGitRepo row = new ApiGitRepo();
        row.setId(REPO_ID);
        row.setProjectId(PROJECT_ID);
        row.setName("order-api-tests");
        row.setGitlabBaseUrl("https://gitlab.example.com");
        row.setRepoPath("qa/order-api-tests");
        row.setDefaultBranch("master");
        row.setImportMode("platform");
        row.setTokenCipher(SecretCryptoUtil.encrypt(SECRET_KEY, "glpat-xxxx"));
        row.setTestSourceDir("src/test/java");
        row.setResourceDir("src/test/resources");
        row.setReimportOverwrite(reimportOverwrite);
        row.setEnabled(true);
        return row;
    }

    private static ApiScene existingScene(UUID id, String sourceId) {
        ApiScene scene = new ApiScene();
        scene.setId(id);
        scene.setProjectId(PROJECT_ID);
        scene.setRepoId(REPO_ID);
        scene.setSourceId(sourceId);
        return scene;
    }

    // ========== 预览 ==========

    @Test
    void previewCountsFirstImportAndWritesNothing() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals(REPO_ID, dto.getRepoId());
        assertEquals("master", dto.getBranch());
        assertEquals("head123", dto.getHeadCommit());
        assertEquals(false, dto.getGate().getBlocked());
        assertEquals(1, dto.getClasses());
        assertEquals(1, dto.getEffectiveCases());
        assertEquals(1, dto.getModules().getContainers().getCreate());
        assertEquals(0, dto.getModules().getContainers().getUpdate());
        assertEquals(1, dto.getModules().getClasses().getCreate());
        assertEquals(0, dto.getModules().getClasses().getBackfill());
        assertEquals(1, dto.getScenes().getCreate());
        assertEquals(0, dto.getScenes().getUpdate());
        assertEquals("skip", dto.getEnvironment().getAction());
        assertTrue(dto.getDetails().isEmpty());
        // 预览不落库
        verify(moduleMapper, never()).insert(any(ProjectModule.class));
        verify(sceneMapper, never()).insert(any(ApiScene.class));
        verify(sceneMapper, never()).updateById(any(ApiScene.class));
        verify(sceneMapper, never()).markScenesRepoRemoved(any(), anyString());
        verify(gitRepoMapper, never()).updateById(any(ApiGitRepo.class));
        verify(changeHistoryMapper, never()).insert(any(ApiChangeHistory.class));
    }

    @Test
    void previewReportsParseFailureDetailWhenTemplateMissing() {
        remoteFiles.remove(YAML_PATH);
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals(0, dto.getScenes().getCreate());
        assertEquals(1, dto.getScenes().getParseFailed());
        assertEquals(1, dto.getDetails().size());
        assertEquals("parseFailed", dto.getDetails().get(0).getType());
        assertEquals(SCENE_KEY, dto.getDetails().get(0).getSceneKey());
        assertTrue(dto.getDetails().get(0).getReason().contains("未找到"));
    }

    @Test
    void previewCountsBackfillWhenSameNameModuleLacksSourceId() {
        ProjectModule container = new ProjectModule();
        container.setId(UUID.randomUUID());
        container.setSourceId("com.example");
        when(moduleMapper.listRootBySourceIds(any(), any())).thenReturn(List.of(container));
        ProjectModule claimed = new ProjectModule();
        claimed.setId(UUID.randomUUID());
        claimed.setParentId(container.getId());
        claimed.setName("OrderQueryTest");
        when(moduleMapper.findByNameAndParent(any(), eq(container.getId()), anyString())).thenReturn(claimed);
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals(0, dto.getModules().getContainers().getCreate());
        assertEquals(1, dto.getModules().getContainers().getUpdate());
        assertEquals(0, dto.getModules().getClasses().getCreate());
        assertEquals(1, dto.getModules().getClasses().getBackfill());
        verify(moduleMapper, never()).backfillSourceId(any(), any());
    }

    @Test
    void previewReportsParsedScenesWithCreateAndUpdateAction() {
        ScannedClass cls = new ScannedClass(JAVA_PATH, "com.example", "OrderQueryTest",
                List.of(new ScannedMethod("queryStatus", "查询订单状态", "order/query_status.yaml"),
                        new ScannedMethod("createOrder", "创建订单", "order/create_order.yaml")));
        lenient().when(scanner.scan(any())).thenReturn(new ScanOutcome(List.of(cls), List.of(), List.of()));
        remoteFiles.put("src/test/resources/order/create_order.yaml", SUITE_YAML);
        // 命中既有稳定键的那条标 update，其余标 create
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(existingScene(UUID.randomUUID(), SCENE_KEY)));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertNotNull(dto.getParsedScenes());
        assertEquals(2, dto.getParsedScenes().getTotal());
        assertEquals(false, dto.getParsedScenes().getTruncated());
        List<ApiGitImportPreviewRespDTO.ParsedSceneDTO> items = dto.getParsedScenes().getItems();
        assertEquals(2, items.size());
        assertEquals(SCENE_KEY, items.get(0).getSceneKey());
        assertEquals("查询订单状态", items.get(0).getName());
        assertEquals("update", items.get(0).getAction());
        assertEquals("com.example.OrderQueryTest#createOrder", items.get(1).getSceneKey());
        assertEquals("create", items.get(1).getAction());
        // 与 scenes 计数同口径
        assertEquals(1, dto.getScenes().getCreate());
        assertEquals(1, dto.getScenes().getUpdate());
    }

    @Test
    void previewTruncatesParsedScenesBeyondLimitButKeepsCounts() {
        List<ScannedMethod> methods = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            String path = "order/case_" + i + ".yaml";
            remoteFiles.put("src/test/resources/" + path, SUITE_YAML);
            methods.add(new ScannedMethod("case" + i, "用例" + i, path));
        }
        ScannedClass cls = new ScannedClass(JAVA_PATH, "com.example", "OrderQueryTest", methods);
        lenient().when(scanner.scan(any())).thenReturn(new ScanOutcome(List.of(cls), List.of(), List.of()));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals(201, dto.getParsedScenes().getTotal());
        assertEquals(true, dto.getParsedScenes().getTruncated());
        assertEquals(200, dto.getParsedScenes().getItems().size());
        // 截断只影响清单，不影响任何计数
        assertEquals(201, dto.getEffectiveCases());
        assertEquals(201, dto.getScenes().getCreate());
    }

    @Test
    void previewParsedScenesExcludesFailures() {
        remoteFiles.remove(YAML_PATH);
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals(0, dto.getParsedScenes().getTotal());
        assertTrue(dto.getParsedScenes().getItems().isEmpty());
        assertEquals(false, dto.getParsedScenes().getTruncated());
    }

    // ========== 导入 ==========

    @Test
    void importPersistsModulesScenesAuditAndWriteback() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("done", status.getStage());
        assertEquals(1, status.getSummary().get("created"));
        assertEquals(0, status.getSummary().get("updated"));
        assertEquals(0, status.getSummary().get("parseFailed"));

        ArgumentCaptor<ProjectModule> moduleCaptor = ArgumentCaptor.forClass(ProjectModule.class);
        verify(moduleMapper, org.mockito.Mockito.times(2)).insert(moduleCaptor.capture());
        ProjectModule container = moduleCaptor.getAllValues().get(0);
        assertEquals("com.example", container.getName());
        assertEquals("com.example", container.getSourceId());
        assertNull(container.getParentId());
        assertEquals("OrderQueryTest", moduleCaptor.getAllValues().get(1).getName());
        assertEquals("com.example.OrderQueryTest", moduleCaptor.getAllValues().get(1).getSourceId());

        ArgumentCaptor<ApiScene> sceneCaptor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).insert(sceneCaptor.capture());
        ApiScene scene = sceneCaptor.getValue();
        assertEquals("查询订单状态", scene.getName());
        assertEquals("draft", scene.getStatus());
        assertEquals(1, scene.getChangeVersion());
        assertEquals("gitlab", scene.getSourceType());
        assertEquals(REPO_ID, scene.getRepoId());
        assertEquals(SCENE_KEY, scene.getSourceId());
        assertEquals("order/query_status.yaml", scene.getSourceRef());
        assertEquals("in_sync", scene.getSourceStatus());
        assertEquals("ok", scene.getParseStatus());
        assertNull(scene.getDescription());
        assertNull(scene.getEnvironmentId());
        assertTrue(scene.getVariables().size() == 1);
        assertTrue(scene.getSteps().size() == 1);

        ArgumentCaptor<ApiChangeHistory> historyCaptor = ArgumentCaptor.forClass(ApiChangeHistory.class);
        verify(changeHistoryMapper).insert(historyCaptor.capture());
        ApiChangeHistory history = historyCaptor.getValue();
        assertEquals("git_repo", history.getTargetType());
        assertEquals(REPO_ID, history.getTargetId());
        assertEquals("import", history.getChangeType());
        assertEquals(1, history.getVersion());
        assertEquals(1, history.getContentDiff().get("created"));

        ArgumentCaptor<ApiGitRepo> writebackCaptor = ArgumentCaptor.forClass(ApiGitRepo.class);
        verify(gitRepoMapper).updateById(writebackCaptor.capture());
        ApiGitRepo writeback = writebackCaptor.getValue();
        assertEquals("head123", writeback.getLastImportCommit());
        assertEquals("head123", writeback.getHeadCommit());
        assertEquals("ok", writeback.getHeadProbeStatus());
        assertNotNull(writeback.getLastImportAt());
        assertEquals(1, writeback.getLastImportSummary().get("created"));
    }

    @Test
    void reimportWithoutOverwriteRefreshesMetadataOnly() {
        ApiScene existing = existingScene(UUID.randomUUID(), SCENE_KEY);
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(existing));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("done", status.getStage());
        assertEquals(1, status.getSummary().get("updated"));
        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).updateById(captor.capture());
        ApiScene carrier = captor.getValue();
        assertEquals(existing.getId(), carrier.getId());
        // 来源元数据恒刷新，内容字段不触碰（C11 + 详设 3.5 合并策略）
        assertEquals("order/query_status.yaml", carrier.getSourceRef());
        assertEquals("in_sync", carrier.getSourceStatus());
        assertEquals("ok", carrier.getParseStatus());
        assertEquals(List.of(), carrier.getParseIssues());
        assertNull(carrier.getName());
        assertNull(carrier.getVariables());
        assertNull(carrier.getSteps());
        assertNull(carrier.getChangeVersion());
        verify(sceneMapper, never()).insert(any(ApiScene.class));
    }

    @Test
    void reimportWithOverwriteRefreshesContent() {
        ApiScene existing = existingScene(UUID.randomUUID(), SCENE_KEY);
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(existing));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(true));

        service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).updateById(captor.capture());
        ApiScene carrier = captor.getValue();
        assertEquals("查询订单状态", carrier.getName());
        assertEquals(1, carrier.getVariables().size());
        assertEquals(1, carrier.getSteps().size());
        // import 不递增 changeVersion
        assertNull(carrier.getChangeVersion());
    }

    @Test
    void importMarksStaleScenesRepoRemoved() {
        UUID staleId = UUID.randomUUID();
        ApiScene stale = existingScene(staleId, "com.example.GoneTest#gone");
        ApiScene current = existingScene(UUID.randomUUID(), SCENE_KEY);
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(stale, current));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("done", status.getStage());
        // 源侧缺失仅标记，不删除、不计 summary（BR-1.42）
        verify(sceneMapper).markScenesRepoRemoved(
                argThat(ids -> ids.size() == 1 && ids.contains(staleId)), eq("repo_removed"));
        verify(sceneMapper, never()).deleteById(any(UUID.class));
        assertEquals(0, status.getSummary().get("created"));
        assertEquals(1, status.getSummary().get("updated"));
        assertNull(status.getSummary().get("stale"));
    }

    @Test
    void importDegradesExistingSceneWhenTemplateMissing() {
        ApiScene existing = existingScene(UUID.randomUUID(), SCENE_KEY);
        remoteFiles.remove(YAML_PATH);
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(existing));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("done", status.getStage());
        assertEquals(1, status.getSummary().get("parseFailed"));
        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).updateById(captor.capture());
        ApiScene carrier = captor.getValue();
        assertEquals("parse_failed", carrier.getSourceStatus());
        assertEquals("failed", carrier.getParseStatus());
        assertEquals(1, carrier.getParseIssues().size());
        assertTrue(carrier.getParseIssues().get(0).get("message").toString().contains("未找到"));
        // 解析失败保留原状：内容字段不触碰（BR-1.12）
        assertNull(carrier.getName());
        assertNull(carrier.getSteps());
        verify(sceneMapper, never()).insert(any(ApiScene.class));
    }

    @Test
    void importsRejectsWhileAnotherImportRunning() {
        List<Runnable> queued = new ArrayList<>();
        service = newService(queued::add);
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID));
        assertEquals(1000017708, ex.getCode());

        // 执行排队任务后闸门放行，状态查询为已完成
        queued.forEach(Runnable::run);
        assertEquals("done", service.status(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID).getStage());
    }

    @Test
    void gitlabCiImportUsesDescriptionAndEmptySteps() {
        ScannedClass cls = new ScannedClass(JAVA_PATH, "com.example", "OrderQueryTest",
                List.of(new ScannedMethod("placeOrder", "下单成功", null)));
        when(scanner.scan(any())).thenReturn(new ScanOutcome(List.of(cls), List.of(), List.of()));
        ApiGitRepo repo = storedRepo(false);
        repo.setImportMode("gitlab_ci");
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repo);

        service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).insert(captor.capture());
        ApiScene scene = captor.getValue();
        assertEquals("下单成功", scene.getName());
        assertEquals("下单成功", scene.getDescription());
        assertEquals(List.of(), scene.getSteps());
        assertEquals(JAVA_PATH + "#placeOrder", scene.getSourceRef());
        assertEquals("com.example.OrderQueryTest#placeOrder", scene.getSourceId());
    }

    @Test
    void statusIsIdleBeforeAnyImport() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        ApiGitImportStatusRespDTO status = service.status(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("idle", status.getStage());
        assertEquals(0, status.getProcessed());
        assertEquals(0, status.getTotal());
    }

    @Test
    void importFailsProgressWhenRepoDeletedMidflight() {
        List<Runnable> queued = new ArrayList<>();
        service = newService(queued::add);
        // 闸门同步校验时仓库在，异步执行时已被删除
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false), (ApiGitRepo) null,
                storedRepo(false));

        service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);
        queued.forEach(Runnable::run);

        ApiGitImportStatusRespDTO status = service.status(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);
        assertEquals("failed", status.getStage());
        assertNotNull(status.getError());
    }

    @Test
    void previewRejectsWhenTokenUndecryptable() {
        ApiGitRepo repo = storedRepo(false);
        repo.setTokenCipher("broken-cipher");
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repo);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID));

        assertEquals(1000017702, ex.getCode());
    }

    // ========== commit 幂等闸门（详设 3.4） ==========

    @Test
    void previewBlockedWhenHeadUpToDate() {
        ApiGitRepo repo = storedRepo(false);
        repo.setLastImportCommit("head123");
        repo.setLastImportSummary(Map.of("created", 5, "updated", 1,
                "parseFailed", 2, "errorFailed", 0, "skipped", 0));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repo);

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals(true, dto.getGate().getBlocked());
        assertTrue(dto.getGate().getReason().contains("已跳过"));
        assertEquals(REPO_ID, dto.getRepoId());
        assertEquals("master", dto.getBranch());
        assertEquals("head123", dto.getHeadCommit());
        assertNull(dto.getClasses());
        assertNull(dto.getModules());
        assertNull(dto.getScenes());
        assertNull(dto.getParsedScenes());
        // 拦截时不拉取源码树、不扫描、不产生任何写操作
        verify(gitLabApiClient, never()).listFiles(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(scanner, never()).scan(any());
        verify(sceneMapper, never()).insert(any(ApiScene.class));
        verify(gitRepoMapper, never()).updateById(any(ApiGitRepo.class));
    }

    @Test
    void previewPassesWhenResidualErrorsRemain() {
        ApiGitRepo repo = storedRepo(false);
        repo.setLastImportCommit("head123");
        repo.setLastImportSummary(Map.of("created", 5, "updated", 1,
                "parseFailed", 0, "errorFailed", 2, "skipped", 0));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repo);

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        // 提交一致但上次仍有真实错误 → 放行（parseFailed 是预期结果，不参与闸门）
        assertEquals(false, dto.getGate().getBlocked());
        assertEquals(1, dto.getScenes().getCreate());
    }

    @Test
    void importsSkippedWhenUpToDate() {
        ApiGitRepo repo = storedRepo(false);
        repo.setLastImportCommit("head123");
        repo.setLastImportSummary(Map.of("errorFailed", 0));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repo);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID));

        assertEquals(1000017709, ex.getCode());
        verify(gitLabApiClient, never()).listFiles(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(sceneMapper, never()).insert(any(ApiScene.class));
        // 未占用进行中闸门：状态仍为 idle，可继续查询与后续发起
        assertEquals("idle", service.status(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID).getStage());
    }

    @Test
    void gitlabCiUsesSameGate() {
        ApiGitRepo repo = storedRepo(false);
        repo.setImportMode("gitlab_ci");
        repo.setLastImportCommit("head123");
        repo.setLastImportSummary(Map.of("errorFailed", 0));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repo);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID));

        assertEquals(1000017709, ex.getCode());
    }

    @Test
    void importsRunWhenHeadDiffersFromLastImport() {
        ApiGitRepo repo = storedRepo(false);
        repo.setLastImportCommit("old456");
        repo.setLastImportSummary(Map.of("errorFailed", 0));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repo);

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("done", status.getStage());
        assertEquals(1, status.getSummary().get("created"));
    }

    // ========== 同步任务（WP-8.2，定时任务详设 27 §4） ==========

    @Test
    void syncImportRejectsWhenRepoMissing() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.syncImport(PROJECT_ID, USER_ID));

        assertEquals(1000017701, ex.getCode());
    }

    @Test
    void syncImportRejectsWhenRepoDisabled() {
        ApiGitRepo repo = storedRepo(false);
        repo.setEnabled(false);
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(repo);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.syncImport(PROJECT_ID, USER_ID));

        assertEquals(1000017701, ex.getCode());
    }

    @Test
    void syncImportSkipsWithoutParsingWhenUpToDate() {
        ApiGitRepo repo = storedRepo(false);
        repo.setLastImportCommit("head123");
        repo.setLastImportSummary(Map.of("errorFailed", 0));
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(repo);

        ApiGitImportService.SyncOutcome outcome = service.syncImport(PROJECT_ID, USER_ID);

        assertTrue(outcome.skipped());
        assertEquals("head123", outcome.headCommit());
        assertEquals(1, outcome.summary().get("skipped"));
        assertEquals(0, outcome.summary().get("created"));
        // 无变更不进解析：不拉源码树、不扫描、不落库
        verify(gitLabApiClient, never()).listFiles(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(sceneMapper, never()).insert(any(ApiScene.class));
    }

    @Test
    void syncImportPersistsPlanWhenHeadChanged() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(storedRepo(false));

        ApiGitImportService.SyncOutcome outcome = service.syncImport(PROJECT_ID, USER_ID);

        assertFalse(outcome.skipped());
        assertEquals("head123", outcome.headCommit());
        assertEquals(1, outcome.summary().get("created"));
        verify(sceneMapper).insert(any(ApiScene.class));
    }

    // ========== 改名迁移（WP-3.4，详设 3.5 规则 1–6） ==========

    private static final String OLD_FQN = "com.example.OrderQueryTest";
    private static final String NEW_FQN = "com.example.b.RenamedQueryTest";
    private static final String NEW_KEY = NEW_FQN + "#queryStatus";
    private static final String NEW_JAVA_PATH = "src/test/java/com/example/b/RenamedQueryTest.java";
    private static final String RENAME_FROM = "oldsha456";

    private ApiGitRepo repoWithRenameBasis() {
        ApiGitRepo row = storedRepo(false);
        row.setLastImportCommit(RENAME_FROM);
        return row;
    }

    private static ScanOutcome renamedScanOutcome() {
        ScannedClass cls = new ScannedClass(NEW_JAVA_PATH, "com.example.b", "RenamedQueryTest",
                List.of(new ScannedMethod("queryStatus", "查询订单状态", "order/query_status.yaml")));
        return new ScanOutcome(List.of(cls), List.of(), List.of());
    }

    private void stubAcceptedRename() {
        when(renameDetector.comparePairs(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new ApiGitRenameDetector.RenamePair(JAVA_PATH, NEW_JAVA_PATH)));
        when(renameDetector.validatePairs(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ApiGitRenameDetector.RenameResult(Map.of(OLD_FQN, NEW_FQN), List.of()));
    }

    @Test
    void previewCountsMigrationAsUpdateWithoutWrites() {
        stubAcceptedRename();
        when(scanner.scan(any())).thenReturn(renamedScanOutcome());
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(existingScene(UUID.randomUUID(), SCENE_KEY)));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repoWithRenameBasis());

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        // 规则 1：比对区间 = 上次导入提交 → 本次 HEAD（fetch 阶段）
        verify(renameDetector).comparePairs(any(), any(), any(), eq(RENAME_FROM), eq("head123"), any());
        // 规则 3：改名后旧行按有效键命中 → 计 update 不计 create；校验通过无明细
        assertEquals(0, dto.getScenes().getCreate());
        assertEquals(1, dto.getScenes().getUpdate());
        assertTrue(dto.getDetails().isEmpty());
        // 预览干跑不落任何迁移
        verify(sceneMapper, never()).updateById(any(ApiScene.class));
        verify(sceneMapper, never()).insert(any(ApiScene.class));
        verify(sceneMapper, never()).markScenesRepoRemoved(any(), anyString());
    }

    @Test
    void importAppliesSceneAndModuleMigrationBeforeUpsert() {
        stubAcceptedRename();
        when(scanner.scan(any())).thenReturn(renamedScanOutcome());
        UUID oldSceneId = UUID.randomUUID();
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(existingScene(oldSceneId, SCENE_KEY)));
        UUID oldModuleId = UUID.randomUUID();
        ProjectModule oldModule = new ProjectModule();
        oldModule.setId(oldModuleId);
        oldModule.setSourceId(OLD_FQN);
        oldModule.setParentId(UUID.randomUUID());
        when(moduleMapper.listByProjectAndSourceIds(any(), any())).thenAnswer(invocation -> {
            Set<String> ids = invocation.getArgument(1);
            return ids.contains(OLD_FQN) ? List.of(oldModule) : List.of();
        });
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repoWithRenameBasis());

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("done", status.getStage());
        assertEquals(0, status.getSummary().get("created"));
        assertEquals(1, status.getSummary().get("updated"));
        // 场景先迁 source_id（规则 3）再按新键 upsert；旧行未被标陈旧
        ArgumentCaptor<ApiScene> sceneCaptor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper, times(2)).updateById(sceneCaptor.capture());
        ApiScene migration = sceneCaptor.getAllValues().get(0);
        assertEquals(oldSceneId, migration.getId());
        assertEquals(NEW_KEY, migration.getSourceId());
        assertNull(migration.getName());
        verify(sceneMapper).markScenesRepoRemoved(argThat(ids -> ids.isEmpty()), eq("repo_removed"));
        // 类模块稳定键旧 → 新迁移，载体仅 id + source_id（C11）
        ArgumentCaptor<ProjectModule> moduleCaptor = ArgumentCaptor.forClass(ProjectModule.class);
        verify(moduleMapper, atLeastOnce()).updateById(moduleCaptor.capture());
        assertTrue(moduleCaptor.getAllValues().stream().anyMatch(
                module -> oldModuleId.equals(module.getId()) && NEW_FQN.equals(module.getSourceId())
                        && module.getName() == null));
    }

    @Test
    void importDegradesToDeleteRecreateWhenCompareFails() {
        when(renameDetector.comparePairs(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("变更比对失败：HTTP 404"));
        when(scanner.scan(any())).thenReturn(renamedScanOutcome());
        UUID oldSceneId = UUID.randomUUID();
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(existingScene(oldSceneId, SCENE_KEY)));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repoWithRenameBasis());

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        // 规则 6：compare 失败计 1 条 errorFailed 并降级删旧建新，不中止导入
        assertEquals("done", status.getStage());
        assertEquals(1, status.getSummary().get("errorFailed"));
        assertEquals(1, status.getSummary().get("created"));
        assertEquals(0, status.getSummary().get("updated"));
        verify(sceneMapper).markScenesRepoRemoved(
                argThat(ids -> ids.size() == 1 && ids.contains(oldSceneId)), eq("repo_removed"));
        verify(renameDetector, never()).validatePairs(any(), any(), any(), any(), any(), any());
    }

    @Test
    void importDegradesRenameWhenValidationReportsError() {
        when(renameDetector.comparePairs(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new ApiGitRenameDetector.RenamePair(JAVA_PATH, NEW_JAVA_PATH)));
        when(renameDetector.validatePairs(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ApiGitRenameDetector.RenameResult(Map.of(),
                        List.of("旧版本文件不存在：" + JAVA_PATH)));
        when(scanner.scan(any())).thenReturn(renamedScanOutcome());
        UUID oldSceneId = UUID.randomUUID();
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(existingScene(oldSceneId, SCENE_KEY)));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repoWithRenameBasis());

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        // 规则 2/6：真实错误计 errorFailed 且不采信改名 → 降级删旧建新，导入继续
        assertEquals("done", status.getStage());
        assertEquals(1, status.getSummary().get("errorFailed"));
        assertEquals(1, status.getSummary().get("created"));
        verify(sceneMapper).markScenesRepoRemoved(
                argThat(ids -> ids.size() == 1 && ids.contains(oldSceneId)), eq("repo_removed"));
    }

    @Test
    void importDropsRenameWhenTargetKeyExists() {
        stubAcceptedRename();
        when(scanner.scan(any())).thenReturn(renamedScanOutcome());
        UUID oldSceneId = UUID.randomUUID();
        UUID targetSceneId = UUID.randomUUID();
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(
                existingScene(oldSceneId, SCENE_KEY), existingScene(targetSceneId, NEW_KEY)));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repoWithRenameBasis());

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        // 规则 5 撞键保护：目标键已有真实行 → 放弃迁移 → 旧行标陈旧、目标行普通更新
        assertEquals("done", status.getStage());
        assertEquals(0, status.getSummary().get("created"));
        assertEquals(1, status.getSummary().get("updated"));
        verify(sceneMapper).markScenesRepoRemoved(
                argThat(ids -> ids.size() == 1 && ids.contains(oldSceneId)), eq("repo_removed"));
        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper, times(1)).updateById(captor.capture());
        assertEquals(targetSceneId, captor.getValue().getId());
        assertNull(captor.getValue().getSourceId());
    }

    @Test
    void importReusesContainerWhenPackageFullyVacated() {
        stubAcceptedRename();
        when(scanner.scan(any())).thenReturn(renamedScanOutcome());
        when(sceneMapper.listByRepo(any())).thenReturn(List.of(existingScene(UUID.randomUUID(), SCENE_KEY)));
        UUID containerId = UUID.randomUUID();
        ProjectModule container = new ProjectModule();
        container.setId(containerId);
        container.setSourceId("com.example");
        UUID oldModuleId = UUID.randomUUID();
        ProjectModule oldModule = new ProjectModule();
        oldModule.setId(oldModuleId);
        oldModule.setSourceId(OLD_FQN);
        oldModule.setParentId(containerId);
        when(moduleMapper.listRootBySourceIds(any(), any())).thenAnswer(invocation -> {
            Set<String> ids = invocation.getArgument(1);
            return ids.contains("com.example") ? List.of(container) : List.of();
        });
        when(moduleMapper.listByProjectAndSourceIds(any(), any())).thenAnswer(invocation -> {
            Set<String> ids = invocation.getArgument(1);
            return ids.contains(OLD_FQN) ? List.of(oldModule) : List.of();
        });
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repoWithRenameBasis());

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("done", status.getStage());
        ArgumentCaptor<ProjectModule> captor = ArgumentCaptor.forClass(ProjectModule.class);
        verify(moduleMapper, atLeastOnce()).updateById(captor.capture());
        List<ProjectModule> carriers = captor.getAllValues();
        // 规则 4：旧包名已无留存类 → 旧容器整体复用，source_id 与显示名同改为新包名
        assertTrue(carriers.stream().anyMatch(
                module -> containerId.equals(module.getId())
                        && "com.example.b".equals(module.getSourceId())
                        && "com.example.b".equals(module.getName())));
        // 规则 3：类模块稳定键迁移
        assertTrue(carriers.stream().anyMatch(
                module -> oldModuleId.equals(module.getId()) && NEW_FQN.equals(module.getSourceId())));
        // 不新建任何模块；类模块与（桥接后的）容器同父 → 不产生 reparent 载体
        verify(moduleMapper, never()).insert(any(ProjectModule.class));
        assertEquals(0, status.getSummary().get("created"));
        assertEquals(1, status.getSummary().get("updated"));
    }

    @Test
    void firstImportSkipsRenameCompare() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo(false));

        service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        verify(renameDetector, never()).comparePairs(any(), any(), any(), any(), any(), any());
    }

    // ========== 环境合并与封印（WP-4.1） ==========

    private static final String ENV_YAML = """
            - testclass: http
              config:
                protocol: http
                host: 127.0.0.1
                port: 7612
            - testclass: jdbc
              ref_name: test_env
              config:
                url: jdbc:mysql://127.0.0.1:3306/testdb
                username: root
                password: 123456
            """;

    private ApiGitRepo envRepo() {
        ApiGitRepo repo = storedRepo(false);
        repo.setEnvFilePath("common/env/env.yaml");
        return repo;
    }

    @Test
    void importMergesEnvCreatesDefaultAndSeals() {
        remoteFiles.put("src/test/resources/common/env/env.yaml", ENV_YAML);
        UUID envId = UUID.randomUUID();
        // 真实插入由框架生成 UUID，测试环境回填以模拟
        org.mockito.Mockito.doAnswer(invocation -> {
            ((ApiEnvironment) invocation.getArgument(0)).setId(envId);
            return 1;
        }).when(environmentMapper).insert(any(ApiEnvironment.class));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(envRepo());
        when(environmentMapper.findDefaultByProjectId(PROJECT_ID)).thenReturn(null);

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("done", status.getStage());
        ArgumentCaptor<ApiEnvironment> envCaptor = ArgumentCaptor.forClass(ApiEnvironment.class);
        verify(environmentMapper).insert(envCaptor.capture());
        ApiEnvironment created = envCaptor.getValue();
        assertEquals("order-api-tests", created.getName());
        assertTrue(created.getIsDefault());
        assertEquals("qa/order-api-tests", created.getSourceId());
        assertEquals(1, created.getHttpConfigs().size());
        assertEquals("http://127.0.0.1:7612", created.getHttpConfigs().get(0).get("baseUrl"));
        assertEquals(1, created.getDataSources().size());

        ArgumentCaptor<ApiScene> sceneCaptor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).insert(sceneCaptor.capture());
        assertNotNull(sceneCaptor.getValue().getEnvironmentId());
    }

    @Test
    void importSkipsWriteWhenEnvSealed() {
        remoteFiles.put("src/test/resources/common/env/env.yaml", ENV_YAML);
        ApiEnvironment sealed = new ApiEnvironment();
        sealed.setId(UUID.randomUUID());
        sealed.setSourceId("qa/another-repo");
        sealed.setHttpConfigs(List.of(Map.of("refName", "custom", "name", "custom",
                "baseUrl", "http://custom", "headers", List.of(), "isDefault", true)));
        sealed.setDataSources(List.of());
        when(environmentMapper.findDefaultByProjectId(PROJECT_ID)).thenReturn(sealed);
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(envRepo());

        service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        // 封印环境跳过写入（BR-1.18），但场景仍回填到该默认环境（BR-1.19）
        verify(environmentMapper, never()).updateById(any(ApiEnvironment.class));
        verify(environmentMapper, never()).insert(any(ApiEnvironment.class));
        verify(sceneMapper).backfillEnvironmentIdIfNull(REPO_ID, sealed.getId());
    }

    @Test
    void importMergesIntoUnsealedDefaultEnv() {
        remoteFiles.put("src/test/resources/common/env/env.yaml", ENV_YAML);
        ApiEnvironment unsealed = new ApiEnvironment();
        unsealed.setId(UUID.randomUUID());
        unsealed.setHttpConfigs(new ArrayList<>(List.of(Map.of("refName", "test_env", "name", "旧名",
                "baseUrl", "http://old", "headers", List.of(), "isDefault", false))));
        unsealed.setDataSources(new ArrayList<>(List.of()));
        when(environmentMapper.findDefaultByProjectId(PROJECT_ID)).thenReturn(unsealed);
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(envRepo());

        service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        ArgumentCaptor<ApiEnvironment> carrierCaptor = ArgumentCaptor.forClass(ApiEnvironment.class);
        verify(environmentMapper).updateById(carrierCaptor.capture());
        ApiEnvironment carrier = carrierCaptor.getValue();
        assertEquals(unsealed.getId(), carrier.getId());
        assertEquals("qa/order-api-tests", carrier.getSourceId());
        // 按 refName 覆盖：test_env 为数据源 refName，http 配置 default 追加
        assertEquals(2, carrier.getHttpConfigs().size());
        assertEquals(1, carrier.getDataSources().size());
    }

    @Test
    void importSkipsEnvMergeWhenFileMissing() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(envRepo());

        ApiGitImportStatusRespDTO status = service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("done", status.getStage());
        verify(environmentMapper, never()).insert(any(ApiEnvironment.class));
        verify(environmentMapper, never()).updateById(any(ApiEnvironment.class));
        verify(sceneMapper, never()).backfillEnvironmentIdIfNull(any(), any());
    }

    @Test
    void gitlabCiImportNeverTouchesEnvironment() {
        remoteFiles.put("src/test/resources/common/env/env.yaml", ENV_YAML);
        ApiGitRepo repo = envRepo();
        repo.setImportMode("gitlab_ci");
        ScannedClass cls = new ScannedClass(JAVA_PATH, "com.example", "OrderQueryTest",
                List.of(new ScannedMethod("placeOrder", "下单成功", null)));
        when(scanner.scan(any())).thenReturn(new ScanOutcome(List.of(cls), List.of(), List.of()));
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(repo);

        service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        // BR-1.20：gitlab_ci 不读取、不合并任何环境配置
        verify(environmentMapper, never()).findDefaultByProjectId(any());
        verify(environmentMapper, never()).insert(any(ApiEnvironment.class));
        verify(sceneMapper, never()).backfillEnvironmentIdIfNull(any(), any());
    }

    @Test
    void existingNullEnvSceneBackfilledButChosenEnvKept() {
        remoteFiles.put("src/test/resources/common/env/env.yaml", ENV_YAML);
        ApiEnvironment unsealed = new ApiEnvironment();
        unsealed.setId(UUID.randomUUID());
        unsealed.setHttpConfigs(new ArrayList<>(List.of()));
        unsealed.setDataSources(new ArrayList<>(List.of()));
        when(environmentMapper.findDefaultByProjectId(PROJECT_ID)).thenReturn(unsealed);
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(envRepo());

        service.imports(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        // 回填以 SQL is-null 条件保证（BR-1.19）：服务层只发起一次按 repo 的回填
        verify(sceneMapper, org.mockito.Mockito.times(1))
                .backfillEnvironmentIdIfNull(REPO_ID, unsealed.getId());
    }

    @Test
    void previewReportsEnvMergeCounts() {
        remoteFiles.put("src/test/resources/common/env/env.yaml", ENV_YAML);
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(envRepo());
        when(environmentMapper.findDefaultByProjectId(PROJECT_ID)).thenReturn(null);

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("merge", dto.getEnvironment().getAction());
        assertEquals("order-api-tests", dto.getEnvironment().getTarget());
        assertEquals(1, dto.getEnvironment().getHttpConfigs());
        assertEquals(1, dto.getEnvironment().getDataSources());
    }

    @Test
    void previewReportsSkipWhenEnvSealed() {
        remoteFiles.put("src/test/resources/common/env/env.yaml", ENV_YAML);
        ApiEnvironment sealed = new ApiEnvironment();
        sealed.setId(UUID.randomUUID());
        sealed.setSourceId("qa/another-repo");
        when(environmentMapper.findDefaultByProjectId(PROJECT_ID)).thenReturn(sealed);
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(envRepo());

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("skip", dto.getEnvironment().getAction());
    }

    @Test
    void previewReportsSkipWhenEnvFileMissing() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(envRepo());

        ApiGitImportPreviewRespDTO dto = service.preview(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        assertEquals("skip", dto.getEnvironment().getAction());
    }
}
