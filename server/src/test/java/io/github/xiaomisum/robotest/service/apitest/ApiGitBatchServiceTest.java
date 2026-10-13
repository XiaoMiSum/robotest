package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitBatchManifestRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import xyz.migoo.framework.common.exception.ServiceException;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批次清单冻结与流水线触发（WP-6.1，GitLab 集成详设 4.2）。
 * <p>核心不变量：快照先于触发落库；变量只含批次票据；触发失败整批置 error 留痕。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiGitBatchServiceTest {

    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiGitRepoMapper gitRepoMapper;

    private final List<ApiExecutionRecord> store = new ArrayList<>();
    /** 真实 32 字节密钥（Base64）：验证令牌解密路径与生产一致 */
    private final String base64Key = java.util.Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private ApiGitRepo repo;
    private ApiGitBatchServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = new ApiGitRepo();
        repo.setId(UUID.randomUUID());
        repo.setProjectId(UUID.randomUUID());
        repo.setGitlabBaseUrl("https://gitlab.example.com");
        repo.setRepoPath("qa/order-api-tests");
        repo.setDefaultBranch("master");
        repo.setTokenCipher(io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil.encrypt(
                io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil.parseKey(base64Key), "glpat-xxxx"));
        repo.setHeadCommit("a1b2c3");

        lenient().when(executionRecordMapper.insert(any(ApiExecutionRecord.class)))
                .thenAnswer(inv -> {
                    store.add(inv.getArgument(0));
                    return 1;
                });
        lenient().when(executionRecordMapper.selectByBatchId(anyString()))
                .thenAnswer(inv -> store.stream()
                        .filter(r -> inv.getArgument(0).equals(r.getBatchId())).toList());
        lenient().when(gitRepoMapper.selectById(any())).thenReturn(repo);
    }

    private ApiGitBatchServiceImpl serviceWith(GitLabApiClient client) {
        service = new ApiGitBatchServiceImpl(executionRecordMapper, gitRepoMapper, client, base64Key);
        return service;
    }

    private GitLabApiClient acceptingClient(List<String> seenBodies, List<Integer> storeSizeAtTrigger) {
        return new GitLabApiClient(new GitLabApiClient.HttpOpener() {
            @Override
            public HttpResponse<String> get(String url, String token) {
                return mockResponse(200, "{\"id\":12345}");
            }

            @Override
            public HttpResponse<String> post(String url, String token, String body) {
                seenBodies.add(body);
                if (storeSizeAtTrigger != null) {
                    storeSizeAtTrigger.add(store.size());
                }
                return mockResponse(201,
                        "{\"iid\":88,\"web_url\":\"https://gitlab.example.com/p/88\",\"status\":\"created\"}");
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int status, String body) {
        HttpResponse<String> resp = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        return resp;
    }

    private ApiScene scene(String sourceRef) {
        ApiScene scene = new ApiScene();
        scene.setId(UUID.randomUUID());
        scene.setProjectId(repo.getProjectId());
        scene.setSourceType("gitlab");
        scene.setRepoId(repo.getId());
        scene.setSourceRef(sourceRef);
        return scene;
    }

    @Test
    void freezeLandsBeforeTriggerAndVariableOnlyCarriesBatchId() {
        List<String> seenBodies = new ArrayList<>();
        List<Integer> sizeAtTrigger = new ArrayList<>();
        ApiGitBatchServiceImpl svc = serviceWith(acceptingClient(seenBodies, sizeAtTrigger));

        ApiGitBatchService.Result result = svc.trigger(repo,
                List.of(scene("com.example.A#m1"), scene("com.example.B#m2")), null, "manual", "scene");

        assertNotNull(result.batchId());
        assertNotNull(result.executionId());
        verify(executionRecordMapper, org.mockito.Mockito.times(2)).insert(any(ApiExecutionRecord.class));
        // 顺序硬约束（详设 4.2 第 2 步）：触发被调用时快照已全部落库
        assertEquals(List.of(2), sizeAtTrigger);
        assertEquals(1, seenBodies.size());
        // 触发报文只含批次票据，不含场景标识（详设 4.2 第 1 步）
        assertTrue(seenBodies.get(0).contains(result.batchId()));
        assertTrue(seenBodies.get(0).contains("ROBOTEST_BATCH_ID"));
        assertTrue(seenBodies.get(0).length() < 300);
        // 快照内容：sourceRef 冻结、commitSha 取探查缓存、状态 pending
        assertEquals(List.of("com.example.A#m1", "com.example.B#m2"),
                store.stream().map(ApiExecutionRecord::getSourceRef).toList());
        assertEquals("a1b2c3", store.get(0).getCommitSha());
        assertEquals("pending", store.get(0).getStatus());
        // 触发成功整批回写流水线编号并置 running
        ArgumentCaptor<String> batchCaptor = ArgumentCaptor.forClass(String.class);
        verify(executionRecordMapper).markBatchRunning(batchCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(88L), anyString());
        assertEquals(result.batchId(), batchCaptor.getValue());
    }

    @Test
    void triggerFailureMarksWholeBatchErrorAndRethrows() {
        GitLabApiClient rejecting = new GitLabApiClient(new GitLabApiClient.HttpOpener() {
            @Override
            public HttpResponse<String> get(String url, String token) {
                return mockResponse(200, "{\"id\":12345}");
            }

            @Override
            public HttpResponse<String> post(String url, String token, String body) {
                return mockResponse(400, "{\"message\":\"ref is invalid\"}");
            }
        });
        ApiGitBatchServiceImpl svc = serviceWith(rejecting);

        ServiceException ex = assertThrows(ServiceException.class, () -> svc.trigger(repo,
                List.of(scene("com.example.A#m1")), null, "manual", "scene"));

        assertEquals(1000017704, ex.getCode());
        // 冻结已落库（留痕前提），触发失败后整批置 error
        assertEquals(1, store.size());
        verify(executionRecordMapper).markBatchError(anyString(), anyString());
        org.mockito.Mockito.verify(executionRecordMapper, org.mockito.Mockito.never())
                .markBatchRunning(anyString(), any(), any());
    }

    @Test
    void unreachableRepoMapsToSceneNotExecutable() {
        GitLabApiClient broken = new GitLabApiClient(new GitLabApiClient.HttpOpener() {
            @Override
            public HttpResponse<String> get(String url, String token) {
                return mockResponse(200, "{\"id\":12345}");
            }

            @Override
            public HttpResponse<String> post(String url, String token, String body) throws Exception {
                throw new java.io.IOException("connection refused");
            }
        });
        ApiGitBatchServiceImpl svc = serviceWith(broken);

        ServiceException ex = assertThrows(ServiceException.class, () -> svc.trigger(repo,
                List.of(scene("com.example.A#m1")), null, "manual", "scene"));

        // BR-1.26 口径：执行阶段仓库不可达提示场景不可执行
        assertEquals(1000017707, ex.getCode());
        verify(executionRecordMapper).markBatchError(anyString(), anyString());
    }

    @Test
    void manifestIsIdempotentSnapshotWithDerivedCaseCount() {
        List<String> seenBodies = new ArrayList<>();
        ApiGitBatchServiceImpl svc = serviceWith(acceptingClient(seenBodies, null));
        ApiGitBatchService.Result result = svc.trigger(repo,
                List.of(scene("com.example.A#m1"), scene("com.example.B#m2")), null, "manual", "scene");

        // 幂等：同一 batchId 两次调用返回同一份快照
        ApiGitBatchManifestRespDTO first = svc.getManifest(result.batchId());
        ApiGitBatchManifestRespDTO second = svc.getManifest(result.batchId());

        assertEquals(result.batchId(), first.getBatchId());
        assertEquals("master", first.getRef());
        assertEquals(2, first.getExpectedCases());
        assertEquals(2, first.getScenes().size());
        // 期望用例数派生：gitlab_ci 场景与方法一一对应恒为 1（BR-1.43）
        assertEquals(1, first.getScenes().get(0).getExpectedCaseCount());
        assertEquals("com.example.A#m1", first.getScenes().get(0).getSourceRef());
        assertEquals(first.getScenes().size(), second.getScenes().size());
        assertEquals(first.getScenes().get(0).getSceneId(), second.getScenes().get(0).getSceneId());
    }

    @Test
    void manifestUnknownBatchReturnsBatchNotFound() {
        ApiGitBatchServiceImpl svc = serviceWith(acceptingClient(new ArrayList<>(), null));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> svc.getManifest(UUID.randomUUID().toString()));

        assertEquals(1000017713, ex.getCode());
    }
}
