package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabRepoSaveReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.GitLabRepository;
import io.github.xiaomisum.robotest.repository.apitest.GitLabRepositoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitLabRepoServiceImplTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID REPO_ID = UUID.fromString("00000000-0000-0000-0000-00000000d001");

    private static final String SECRET_KEY_BASE64 = "4chJwgVabFLPyA0Mty7RDhu7lXR5Hik2QZ0FJjS3rtI=";

    @Mock
    private GitLabRepositoryMapper gitLabRepositoryMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private GitLabRepoServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "secretKeyBase64", SECRET_KEY_BASE64);
    }

    // ========== fetchPage ==========
    // selectPageByProject 为 Mapper default 方法，无法通过 Mockito 拦截；覆盖由集成测试承担。

    // ========== create ==========

    @Test
    void create_normal_encryptsTokenAndInserts() {
        when(gitLabRepositoryMapper.existsByProjectAndName(PROJECT_ID, "主仓库", null))
                .thenReturn(false);
        when(gitLabRepositoryMapper.insert(any(GitLabRepository.class))).thenReturn(1);

        GitLabRepoSaveReqDTO reqDTO = saveReqDTO();
        UUID id = service.create(PROJECT_ID, WORKSPACE_ID, USER_ID, reqDTO);

        ArgumentCaptor<GitLabRepository> captor = ArgumentCaptor.forClass(GitLabRepository.class);
        verify(gitLabRepositoryMapper).insert(captor.capture());
        GitLabRepository saved = captor.getValue();
        assertEquals(PROJECT_ID, saved.getProjectId());
        assertEquals("主仓库", saved.getName());
        assertEquals("https://gitlab.example.com/team/test.git", saved.getRepoUrl());
        assertEquals("main", saved.getBranch());
        // 令牌应被加密，不存明文
        assertNotEquals("glpat-xxxx", saved.getAccessTokenCipher());
        // tokenSuffix 应为末 4 位
        assertEquals("xxxx", saved.getTokenSuffix());
        // id 由 mapper 回填
        assertEquals(id, saved.getId());
    }

    @Test
    void create_duplicateName_throws() {
        when(gitLabRepositoryMapper.existsByProjectAndName(PROJECT_ID, "主仓库", null))
                .thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(PROJECT_ID, WORKSPACE_ID, USER_ID, saveReqDTO()));
        assertEquals(ErrorCodeConstants.API_GITLAB_REPO_NAME_EXISTS.code(), ex.getCode());
    }

    // ========== update ==========

    @Test
    void update_normal_updatesFields() {
        GitLabRepository existing = repoEntity();
        when(gitLabRepositoryMapper.selectByProjectAndId(PROJECT_ID, REPO_ID)).thenReturn(existing);
        when(gitLabRepositoryMapper.existsByProjectAndName(PROJECT_ID, "新名称", REPO_ID))
                .thenReturn(false);
        when(gitLabRepositoryMapper.updateById(any(GitLabRepository.class))).thenReturn(1);

        GitLabRepoSaveReqDTO reqDTO = saveReqDTO();
        reqDTO.setName("新名称");
        reqDTO.setAccessToken(""); // 留空不修改令牌

        service.update(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, reqDTO);

        ArgumentCaptor<GitLabRepository> captor = ArgumentCaptor.forClass(GitLabRepository.class);
        verify(gitLabRepositoryMapper).updateById(captor.capture());
        GitLabRepository updated = captor.getValue();
        assertEquals(REPO_ID, updated.getId());
        assertEquals("新名称", updated.getName());
        // accessToken 留空时不应设置加密字段
        assertNull(updated.getAccessTokenCipher());
        assertNull(updated.getTokenSuffix());
    }

    @Test
    void update_withNewToken_encryptsNewToken() {
        GitLabRepository existing = repoEntity();
        when(gitLabRepositoryMapper.selectByProjectAndId(PROJECT_ID, REPO_ID)).thenReturn(existing);
        when(gitLabRepositoryMapper.existsByProjectAndName(PROJECT_ID, "主仓库", REPO_ID))
                .thenReturn(false);
        when(gitLabRepositoryMapper.updateById(any(GitLabRepository.class))).thenReturn(1);

        GitLabRepoSaveReqDTO reqDTO = saveReqDTO();
        reqDTO.setAccessToken("glpat-new-token-1234");

        service.update(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, reqDTO);

        ArgumentCaptor<GitLabRepository> captor = ArgumentCaptor.forClass(GitLabRepository.class);
        verify(gitLabRepositoryMapper).updateById(captor.capture());
        GitLabRepository updated = captor.getValue();
        // 新令牌应被加密
        byte[] key = SecretCryptoUtil.parseKey(SECRET_KEY_BASE64);
        String decrypted = SecretCryptoUtil.decrypt(key, updated.getAccessTokenCipher());
        assertEquals("glpat-new-token-1234", decrypted);
        assertEquals("1234", updated.getTokenSuffix());
    }

    @Test
    void update_notFound_throws() {
        when(gitLabRepositoryMapper.selectByProjectAndId(PROJECT_ID, REPO_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.update(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, saveReqDTO()));
        assertEquals(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void update_duplicateName_throws() {
        GitLabRepository existing = repoEntity();
        when(gitLabRepositoryMapper.selectByProjectAndId(PROJECT_ID, REPO_ID)).thenReturn(existing);
        when(gitLabRepositoryMapper.existsByProjectAndName(PROJECT_ID, "主仓库", REPO_ID))
                .thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.update(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID, saveReqDTO()));
        assertEquals(ErrorCodeConstants.API_GITLAB_REPO_NAME_EXISTS.code(), ex.getCode());
    }

    // ========== delete ==========

    @Test
    void delete_existing_deletesById() {
        GitLabRepository existing = repoEntity();
        when(gitLabRepositoryMapper.selectByProjectAndId(PROJECT_ID, REPO_ID)).thenReturn(existing);
        when(gitLabRepositoryMapper.deleteById(REPO_ID)).thenReturn(1);

        service.delete(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID);

        verify(gitLabRepositoryMapper).deleteById(REPO_ID);
    }

    @Test
    void delete_notFound_throws() {
        when(gitLabRepositoryMapper.selectByProjectAndId(PROJECT_ID, REPO_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.delete(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID));
        assertEquals(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND.code(), ex.getCode());
        verify(gitLabRepositoryMapper, never()).deleteById(any());
    }

    // ========== testConnection ==========

    @Test
    void testConnection_notFound_throws() {
        when(gitLabRepositoryMapper.selectByProjectAndId(PROJECT_ID, REPO_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.testConnection(PROJECT_ID, WORKSPACE_ID, USER_ID, REPO_ID));
        assertEquals(ErrorCodeConstants.API_GITLAB_REPO_NOT_FOUND.code(), ex.getCode());
    }

    // ========== helpers ==========

    private static GitLabRepoSaveReqDTO saveReqDTO() {
        GitLabRepoSaveReqDTO dto = new GitLabRepoSaveReqDTO();
        dto.setName("主仓库");
        dto.setRepoUrl("https://gitlab.example.com/team/test.git");
        dto.setAccessToken("glpat-xxxx");
        dto.setBranch("main");
        dto.setTestSourcePath("src/test/java");
        return dto;
    }

    private static GitLabRepository repoEntity() {
        GitLabRepository entity = new GitLabRepository();
        entity.setId(REPO_ID);
        entity.setProjectId(PROJECT_ID);
        entity.setName("主仓库");
        entity.setRepoUrl("https://gitlab.example.com/team/test.git");
        entity.setBranch("main");
        entity.setAccessTokenCipher("encrypted-value");
        entity.setTokenSuffix("xxxx");
        entity.setTestSourcePath("src/test/java");
        return entity;
    }

    private static void assertNotEquals(String unexpected, String actual) {
        org.junit.jupiter.api.Assertions.assertNotEquals(unexpected, actual);
    }
}
