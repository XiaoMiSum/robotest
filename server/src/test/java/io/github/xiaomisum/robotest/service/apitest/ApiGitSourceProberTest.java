package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 源探查服务测试（详设 3.7 / BR-1.45）：只读回写、失败留痕且保留上次提交号、
 * 单仓库失败不中断整轮、令牌解密失败按不可达降级。
 */
@ExtendWith(MockitoExtension.class)
class ApiGitSourceProberTest {

    private static final byte[] SECRET_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String TOKEN = "glpat-xxxx";
    private static final String HEAD = "abc123def456";

    @Mock
    private ApiGitRepoMapper gitRepoMapper;
    @Mock
    private GitLabApiClient gitLabApiClient;

    private ApiGitSourceProber prober;

    @BeforeEach
    void setUp() {
        prober = new ApiGitSourceProber(SECRET_KEY);
        ReflectionTestUtils.setField(prober, "gitRepoMapper", gitRepoMapper);
        ReflectionTestUtils.setField(prober, "gitLabApiClient", gitLabApiClient);
    }

    private static ApiGitRepo repo(String tokenCipher) {
        return repo("qa/order-api-tests", tokenCipher);
    }

    private static ApiGitRepo repo(String repoPath, String tokenCipher) {
        ApiGitRepo row = new ApiGitRepo();
        row.setId(java.util.UUID.randomUUID());
        row.setProjectId(java.util.UUID.randomUUID());
        row.setGitlabBaseUrl("https://gitlab.example.com");
        row.setRepoPath(repoPath);
        row.setDefaultBranch("master");
        row.setTokenCipher(tokenCipher);
        row.setEnabled(true);
        return row;
    }

    private static String cipher() {
        return SecretCryptoUtil.encrypt(SECRET_KEY, TOKEN);
    }

    @Test
    void probeAllWritesOkOnSuccess() {
        ApiGitRepo row = repo(cipher());
        when(gitRepoMapper.selectEnabledRepos()).thenReturn(List.of(row));
        when(gitLabApiClient.branchHead(anyString(), anyString(), anyString(), anyString())).thenReturn(HEAD);

        prober.probeAll();

        ArgumentCaptor<LocalDateTime> checkedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(gitRepoMapper).updateProbeResult(eq(row.getId()), eq(HEAD), checkedAt.capture(), eq("ok"));
        assertNotNull(checkedAt.getValue());
        // 只读约束：探查不触发导入、不写场景
        verify(gitRepoMapper, never()).insert(any(ApiGitRepo.class));
    }

    @Test
    void probeAllWritesUnreachableAndKeepsHeadCommitOnFailure() {
        ApiGitRepo row = repo(cipher());
        when(gitRepoMapper.selectEnabledRepos()).thenReturn(List.of(row));
        when(gitLabApiClient.branchHead(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(ServiceExceptionUtil.get(1000017702, "实例不可达"));

        prober.probeAll();

        // headCommit 传 null：mapper 不 set head_commit 列，保留上次探查值（详设 3.7 第 3 步）
        verify(gitRepoMapper).updateProbeResult(eq(row.getId()), isNull(), any(LocalDateTime.class),
                eq("unreachable"));
        verify(gitRepoMapper, never()).updateProbeResult(eq(row.getId()), eq(HEAD), any(), anyString());
    }

    @Test
    void probeAllDegradesToUnreachableWhenTokenUndecryptable() {
        ApiGitRepo row = repo("cipher-with-rotated-key");
        when(gitRepoMapper.selectEnabledRepos()).thenReturn(List.of(row));

        prober.probeAll();

        verify(gitLabApiClient, never()).branchHead(anyString(), anyString(), anyString(), anyString());
        verify(gitRepoMapper).updateProbeResult(eq(row.getId()), isNull(), any(LocalDateTime.class),
                eq("unreachable"));
    }

    @Test
    void singleRepoFailureDoesNotBreakRemainingRepos() {
        ApiGitRepo bad = repo("qa/broken-repo", cipher());
        ApiGitRepo good = repo("qa/healthy-repo", cipher());
        when(gitRepoMapper.selectEnabledRepos()).thenReturn(List.of(bad, good));
        when(gitLabApiClient.branchHead(anyString(), anyString(), eq(bad.getRepoPath()), anyString()))
                .thenThrow(ServiceExceptionUtil.get(1000017702, "分支不存在"));
        when(gitLabApiClient.branchHead(anyString(), anyString(), eq(good.getRepoPath()), anyString()))
                .thenReturn(HEAD);

        prober.probeAll();

        verify(gitRepoMapper).updateProbeResult(eq(bad.getId()), isNull(), any(LocalDateTime.class),
                eq("unreachable"));
        verify(gitRepoMapper).updateProbeResult(eq(good.getId()), eq(HEAD), any(LocalDateTime.class),
                eq("ok"));
    }

    @Test
    void emptyRepoListIsNoOp() {
        when(gitRepoMapper.selectEnabledRepos()).thenReturn(List.of());

        prober.probeAll();

        verify(gitRepoMapper, never()).updateProbeResult(any(), any(), any(), anyString());
        verify(gitLabApiClient, never()).branchHead(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void writeFailureDoesNotBreakRemainingRepos() {
        ApiGitRepo first = repo("qa/first-repo", cipher());
        ApiGitRepo second = repo("qa/second-repo", cipher());
        when(gitRepoMapper.selectEnabledRepos()).thenReturn(List.of(first, second));
        when(gitLabApiClient.branchHead(anyString(), anyString(), anyString(), anyString())).thenReturn(HEAD);
        when(gitRepoMapper.updateProbeResult(eq(first.getId()), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("db transient"));

        prober.probeAll();

        verify(gitRepoMapper).updateProbeResult(eq(second.getId()), eq(HEAD), any(LocalDateTime.class),
                eq("ok"));
    }

    @Test
    void probeIsReadOnlyAgainstImportPaths() {
        ApiGitRepo row = repo(cipher());
        row.setLastImportCommit("prev-commit");
        when(gitRepoMapper.selectEnabledRepos()).thenReturn(List.of(row));
        when(gitLabApiClient.branchHead(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(HEAD);

        prober.probeAll();

        // 探查只写探查三列，不触碰导入基准 last_import_commit（BR-1.45 只读）
        verify(gitRepoMapper).updateProbeResult(eq(row.getId()), eq(HEAD), any(LocalDateTime.class),
                eq("ok"));
        assertEquals("prev-commit", row.getLastImportCommit());
        assertNull(row.getLastImportAt());
    }

    @Test
    void missingSecretKeyDegradesToUnreachable() {
        ApiGitRepo row = repo(cipher());
        when(gitRepoMapper.selectEnabledRepos()).thenReturn(List.of(row));

        ApiGitSourceProber noKey = new ApiGitSourceProber((byte[]) null);
        ReflectionTestUtils.setField(noKey, "gitRepoMapper", gitRepoMapper);
        ReflectionTestUtils.setField(noKey, "gitLabApiClient", gitLabApiClient);
        noKey.probeAll();

        verify(gitLabApiClient, never()).branchHead(anyString(), anyString(), anyString(), anyString());
        verify(gitRepoMapper).updateProbeResult(eq(row.getId()), isNull(), any(LocalDateTime.class),
                eq("unreachable"));
    }
}
