package io.github.xiaomisum.robotest.service.apitest;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Mock
    private TestSourceScanner scanner;
    @Mock
    private GitLabApiClient gitLabApiClient;
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
        return new ApiGitImportServiceImpl(sceneMapper, moduleMapper, gitRepoMapper, changeHistoryMapper,
                scanner, gitLabApiClient, projectAccessGuard, transactionManager, executor, BASE64_KEY);
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
        lenient().when(sceneMapper.listByRepoAndSourceIds(any(), any())).thenReturn(List.of());
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
        when(sceneMapper.listByRepoAndSourceIds(any(), any())).thenReturn(List.of(existing));
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
        when(sceneMapper.listByRepoAndSourceIds(any(), any())).thenReturn(List.of(existing));
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
        when(sceneMapper.listByRepoAndSourceIds(any(), any())).thenReturn(List.of(current));
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
        when(sceneMapper.listByRepoAndSourceIds(any(), any())).thenReturn(List.of(existing));
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
}
