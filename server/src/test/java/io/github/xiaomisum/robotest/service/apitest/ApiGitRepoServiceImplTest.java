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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiGitRepoServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REPO_ID = UUID.randomUUID();
    private static final byte[] SECRET_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Mock
    private ApiGitRepoMapper gitRepoMapper;
    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiScheduledTaskMapper scheduledTaskMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private GitLabApiClient gitLabApiClient;

    private ApiGitRepoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApiGitRepoServiceImpl(SECRET_KEY);
        ReflectionTestUtils.setField(service, "gitRepoMapper", gitRepoMapper);
        ReflectionTestUtils.setField(service, "sceneMapper", sceneMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        ReflectionTestUtils.setField(service, "projectAccessGuard", projectAccessGuard);
        ReflectionTestUtils.setField(service, "gitLabApiClient", gitLabApiClient);
        lenient().when(gitLabApiClient.probe(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GitLabApiClient.Probe("17.5.1", "master"));
    }

    private static ApiGitRepoCreateReqDTO createReq() {
        ApiGitRepoCreateReqDTO req = new ApiGitRepoCreateReqDTO();
        req.setName("order-api-tests");
        req.setGitlabBaseUrl("https://gitlab.example.com");
        req.setRepoPath("qa/order-api-tests");
        req.setImportMode("platform");
        req.setToken("glpat-xxxx");
        return req;
    }

    private ApiGitRepo storedRepo() {
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
        row.setEnvFilePath("common/env/env.yaml");
        row.setEnabled(true);
        row.setCreatedBy(USER_ID);
        return row;
    }

    // ========== 查询 ==========

    @Test
    void getRepoReturnsNullWhenNotConfigured() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(null);
        assertNull(service.getRepo(PROJECT_ID, WORKSPACE_ID, USER_ID));
    }

    @Test
    void getRepoDerivesHasUpdates() {
        ApiGitRepo row = storedRepo();
        row.setHeadCommit("b7c8");
        row.setLastImportCommit("a1b2");
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(row);

        ApiGitRepoRespDTO dto = service.getRepo(PROJECT_ID, WORKSPACE_ID, USER_ID);

        assertTrue(dto.getHasUpdates());
        assertEquals("platform", dto.getImportMode());
        assertEquals("src/test/java", dto.getTestSourceDir());
    }

    @Test
    void getRepoHasUpdatesFalseWhenHeadUnprobed() {
        ApiGitRepo row = storedRepo();
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(row);

        assertFalse(service.getRepo(PROJECT_ID, WORKSPACE_ID, USER_ID).getHasUpdates());
    }

    // ========== 创建 ==========

    @Test
    void createPersistsEncryptedTokenAfterProbe() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(null);

        service.createRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, createReq());

        ArgumentCaptor<ApiGitRepo> captor = ArgumentCaptor.forClass(ApiGitRepo.class);
        verify(gitRepoMapper).insert(captor.capture());
        ApiGitRepo inserted = captor.getValue();
        assertEquals("master", inserted.getDefaultBranch());
        assertEquals("src/test/java", inserted.getTestSourceDir());
        assertEquals("src/test/resources", inserted.getResourceDir());
        assertEquals("common/env/env.yaml", inserted.getEnvFilePath());
        assertFalse(inserted.getReimportOverwrite());
        assertTrue(inserted.getEnabled());
        assertEquals(USER_ID, inserted.getCreatedBy());
        assertEquals("glpat-xxxx", SecretCryptoUtil.decrypt(SECRET_KEY, inserted.getTokenCipher()));
        verify(gitLabApiClient).probe("https://gitlab.example.com", "glpat-xxxx", "qa/order-api-tests", "master");
    }

    @Test
    void createRejectsSecondConfigInProject() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(storedRepo());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, createReq()));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_PATH_CONFLICT.code(), ex.getCode());
        verify(gitRepoMapper, never()).insert(any(ApiGitRepo.class));
    }

    @Test
    void createRejectsGitSuffixRepoPath() {
        ApiGitRepoCreateReqDTO req = createReq();
        req.setRepoPath("qa/order-api-tests.git");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, req));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void createRejectsHttpPrefixedRepoPath() {
        ApiGitRepoCreateReqDTO req = createReq();
        req.setRepoPath("https://gitlab.example.com/qa/order-api-tests");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, req));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void createNormalizesLeadingAndTrailingSlashes() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(null);
        ApiGitRepoCreateReqDTO req = createReq();
        req.setRepoPath(" /qa/order-api-tests/ ");

        service.createRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, req);

        verify(gitLabApiClient).probe("https://gitlab.example.com", "glpat-xxxx", "qa/order-api-tests", "master");
        ArgumentCaptor<ApiGitRepo> captor = ArgumentCaptor.forClass(ApiGitRepo.class);
        verify(gitRepoMapper).insert(captor.capture());
        assertEquals("qa/order-api-tests", captor.getValue().getRepoPath());
    }

    @Test
    void createRejectsRepoPathMadeOfSlashesOnly() {
        ApiGitRepoCreateReqDTO req = createReq();
        req.setRepoPath("///");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, req));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.code(), ex.getCode());
        verify(gitLabApiClient, never()).probe(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createRejectsUnknownImportMode() {
        ApiGitRepoCreateReqDTO req = createReq();
        req.setImportMode("webhook");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, req));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_IMMUTABLE_FIELD.code(), ex.getCode());
        verify(gitLabApiClient, never()).probe(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createRejectsBlankToken() {
        ApiGitRepoCreateReqDTO req = createReq();
        req.setToken("  ");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, req));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(), ex.getCode());
    }

    @Test
    void createDoesNotPersistWhenProbeFails() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(null);
        when(gitLabApiClient.probe(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(), "实例不可达：测试桩"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, createReq()));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(), ex.getCode());
        verify(gitRepoMapper, never()).insert(any(ApiGitRepo.class));
    }

    // ========== 更新 ==========

    @Test
    void updateRejectsLockedFieldAppearance() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo());
        ApiGitRepoUpdateReqDTO req = new ApiGitRepoUpdateReqDTO();
        req.setName("renamed");
        req.setRejectedFields(new LinkedHashMap<>(Map.of("defaultBranch", "main")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.updateRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, req));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_IMMUTABLE_FIELD.code(), ex.getCode());
        verify(gitRepoMapper, never()).updateById(any(ApiGitRepo.class));
    }

    @Test
    void updateKeepsStoredTokenWhenTokenBlank() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo());
        ApiGitRepoUpdateReqDTO req = new ApiGitRepoUpdateReqDTO();
        req.setName("renamed");

        service.updateRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, req);

        ArgumentCaptor<ApiGitRepo> captor = ArgumentCaptor.forClass(ApiGitRepo.class);
        verify(gitRepoMapper).updateById(captor.capture());
        assertNull(captor.getValue().getTokenCipher());
        assertEquals("renamed", captor.getValue().getName());
        verify(gitLabApiClient).probe("https://gitlab.example.com", "glpat-xxxx", "qa/order-api-tests", "master");
    }

    @Test
    void updateEncryptsNewToken() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo());
        ApiGitRepoUpdateReqDTO req = new ApiGitRepoUpdateReqDTO();
        req.setToken("glpat-yyyy");

        service.updateRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, req);

        ArgumentCaptor<ApiGitRepo> captor = ArgumentCaptor.forClass(ApiGitRepo.class);
        verify(gitRepoMapper).updateById(captor.capture());
        assertEquals("glpat-yyyy", SecretCryptoUtil.decrypt(SECRET_KEY, captor.getValue().getTokenCipher()));
    }

    @Test
    void createHonorsReimportOverwriteWhenTrue() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(null);
        ApiGitRepoCreateReqDTO req = createReq();
        req.setReimportOverwrite(true);

        service.createRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, req);

        ArgumentCaptor<ApiGitRepo> captor = ArgumentCaptor.forClass(ApiGitRepo.class);
        verify(gitRepoMapper).insert(captor.capture());
        assertTrue(captor.getValue().getReimportOverwrite());
    }

    @Test
    void updatePersistsReimportOverwriteWhenPassed() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo());
        ApiGitRepoUpdateReqDTO req = new ApiGitRepoUpdateReqDTO();
        req.setReimportOverwrite(true);

        service.updateRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, req);

        ArgumentCaptor<ApiGitRepo> captor = ArgumentCaptor.forClass(ApiGitRepo.class);
        verify(gitRepoMapper).updateById(captor.capture());
        assertTrue(captor.getValue().getReimportOverwrite());
    }

    @Test
    void updateSkipsPersistWhenReimportOverwriteAbsent() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo());
        ApiGitRepoUpdateReqDTO req = new ApiGitRepoUpdateReqDTO();
        req.setName("renamed");

        service.updateRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, req);

        ArgumentCaptor<ApiGitRepo> captor = ArgumentCaptor.forClass(ApiGitRepo.class);
        verify(gitRepoMapper).updateById(captor.capture());
        assertNull(captor.getValue().getReimportOverwrite());
    }

    @Test
    void updateRejectsRepoOfAnotherProject() {
        ApiGitRepo foreign = storedRepo();
        foreign.setProjectId(UUID.randomUUID());
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(foreign);
        ApiGitRepoUpdateReqDTO req = new ApiGitRepoUpdateReqDTO();
        req.setName("renamed");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.updateRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, req));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void updateDoesNotPersistWhenProbeFails() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo());
        when(gitLabApiClient.probe(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(), "实例不可达：测试桩"));
        ApiGitRepoUpdateReqDTO req = new ApiGitRepoUpdateReqDTO();
        req.setName("renamed");

        assertThrows(ServiceException.class,
                () -> service.updateRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, req));

        verify(gitRepoMapper, never()).updateById(any(ApiGitRepo.class));
    }

    // ========== 删除 ==========

    @Test
    void deleteMarksScenesRepoRemoved() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo());
        when(scheduledTaskMapper.selectCountEnabledGitlabSync(PROJECT_ID)).thenReturn(0L);

        service.deleteRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        verify(gitRepoMapper).deleteById(REPO_ID);
        verify(sceneMapper).markRepoScenesRepoRemoved(REPO_ID, "repo_removed");
    }

    @Test
    void deleteRejectedWhenSyncTaskEnabled() {
        when(gitRepoMapper.selectById(REPO_ID)).thenReturn(storedRepo());
        when(scheduledTaskMapper.selectCountEnabledGitlabSync(PROJECT_ID)).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.deleteRepo(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_TASK_BOUND.code(), ex.getCode());
        verify(gitRepoMapper, never()).deleteById(any());
        verify(sceneMapper, never()).markRepoScenesRepoRemoved(any(), anyString());
    }

    // ========== 连通性测试 ==========

    @Test
    void testConnectionFallsBackToStoredConfig() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(storedRepo());

        ApiGitRepoConnectionTestRespDTO dto = service.testConnection(PROJECT_ID, WORKSPACE_ID, USER_ID,
                new ApiGitRepoConnectionTestReqDTO());

        assertTrue(dto.getReachable());
        assertTrue(dto.getTokenValid());
        assertTrue(dto.getBranchExists());
        assertEquals("17.5.1", dto.getVersion());
        assertEquals("master", dto.getResolvedBranch());
        verify(gitLabApiClient).probe("https://gitlab.example.com", "glpat-xxxx", "qa/order-api-tests", "master");
        verify(gitRepoMapper, never()).insert(any(ApiGitRepo.class));
    }

    @Test
    void testConnectionPrefersBodyOverStoredConfig() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(storedRepo());
        ApiGitRepoConnectionTestReqDTO req = new ApiGitRepoConnectionTestReqDTO();
        req.setRepoPath("qa/another-repo");
        req.setDefaultBranch("develop");

        service.testConnection(PROJECT_ID, WORKSPACE_ID, USER_ID, req);

        verify(gitLabApiClient).probe("https://gitlab.example.com", "glpat-xxxx", "qa/another-repo", "develop");
    }

    @Test
    void testConnectionNormalizesRepoPathFromBody() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(storedRepo());
        ApiGitRepoConnectionTestReqDTO req = new ApiGitRepoConnectionTestReqDTO();
        req.setRepoPath("/qa/another-repo/");

        service.testConnection(PROJECT_ID, WORKSPACE_ID, USER_ID, req);

        verify(gitLabApiClient).probe("https://gitlab.example.com", "glpat-xxxx", "qa/another-repo", "master");
    }

    @Test
    void testConnectionRejectsMissingBaseUrlInEmptyState() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(null);
        ApiGitRepoConnectionTestReqDTO req = new ApiGitRepoConnectionTestReqDTO();
        req.setToken("glpat-xxxx");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.testConnection(PROJECT_ID, WORKSPACE_ID, USER_ID, req));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(), ex.getCode());
    }

    @Test
    void testConnectionRejectsMissingTokenInEmptyState() {
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(null);
        ApiGitRepoConnectionTestReqDTO req = new ApiGitRepoConnectionTestReqDTO();
        req.setGitlabBaseUrl("https://gitlab.example.com");
        req.setRepoPath("qa/order-api-tests");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.testConnection(PROJECT_ID, WORKSPACE_ID, USER_ID, req));

        assertEquals(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(), ex.getCode());
    }

    /** 源探查回写列示例：验证 head 与上次导入一致时派生标记为 false */
    @Test
    void hasUpdatesFalseWhenCommitsEqual() {
        ApiGitRepo row = storedRepo();
        row.setHeadCommit("a1b2");
        row.setLastImportCommit("a1b2");
        row.setHeadCheckedAt(LocalDateTime.now());
        row.setHeadProbeStatus("ok");
        when(gitRepoMapper.selectByProject(PROJECT_ID)).thenReturn(row);

        assertFalse(service.getRepo(PROJECT_ID, WORKSPACE_ID, USER_ID).getHasUpdates());
    }
}
