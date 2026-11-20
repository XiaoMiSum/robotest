package io.github.xiaomisum.robotest.service.ai.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.github.xiaomisum.robotest.framework.audit.AuditLogWriter;
import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiEmbeddingSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiEmbeddingTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiEmbeddingRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiEmbeddingTestRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.repository.ai.AiEmbeddingConfigMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiEmbeddingAdminServiceTest {

    private static final byte[] SECRET_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Mock
    private AiEmbeddingConfigMapper embeddingMapper;
    @Mock
    private AiEmbeddingClient embeddingClient;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private HttpServletRequest request;

    private AiEmbeddingAdminService service;
    private LoginUser loginUser;

    @BeforeAll
    static void initTableInfo() {
        AiConfigTableInfo.init();
    }

    @BeforeEach
    void setUp() {
        service = new AiEmbeddingAdminService(SECRET_KEY);
        ReflectionTestUtils.setField(service, "embeddingMapper", embeddingMapper);
        ReflectionTestUtils.setField(service, "embeddingClient", embeddingClient);
        ReflectionTestUtils.setField(service, "auditLogWriter", auditLogWriter);
        ReflectionTestUtils.setField(service, "clientIpResolver", clientIpResolver);
        lenient().when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");

        loginUser = new LoginUser();
        loginUser.setId(UUID.randomUUID());
        loginUser.setUsername("admin");
    }

    private static AiEmbeddingConfig configuredRow(int dimensions, String operator) {
        AiEmbeddingConfig row = new AiEmbeddingConfig();
        row.setId(UUID.randomUUID());
        row.setProvider("openai");
        row.setBaseUrl("https://api.example.com/v1");
        row.setApiKeyEncrypted("cipher");
        row.setEmbeddingModel("text-embedding-x");
        row.setDimensions(dimensions);
        row.setOperator(operator);
        row.setIndexType("hnsw");
        row.setEnabled(true);
        row.setVersions(new ArrayList<>());
        return row;
    }

    private static AiEmbeddingSaveReqDTO saveReq(Integer dimensions, String operator) {
        AiEmbeddingSaveReqDTO req = new AiEmbeddingSaveReqDTO();
        req.setDimensions(dimensions);
        req.setOperator(operator);
        req.setEnabled(true);
        return req;
    }

    @Test
    void get_notConfigured_returnsUnconfiguredDefaults() {
        when(embeddingMapper.selectSingleton()).thenReturn(null);

        AiEmbeddingRespDTO dto = service.get();

        assertFalse(dto.getEnabled());
        assertFalse(dto.getRequiresReindex());
        assertTrue(dto.getVersions().isEmpty());
        assertFalse(dto.getKeyConfigured());
        assertNull(dto.getDimensions());
    }

    @Test
    void get_withRetiredVersions_marksRequiresReindex() {
        AiEmbeddingConfig row = configuredRow(768, "cosine");
        List<Map<String, Object>> versions = new ArrayList<>();
        Map<String, Object> retired = new LinkedHashMap<>();
        retired.put("version", 1);
        retired.put("dimensions", 1536);
        versions.add(retired);
        row.setVersions(versions);
        when(embeddingMapper.selectSingleton()).thenReturn(row);

        AiEmbeddingRespDTO dto = service.get();

        assertTrue(dto.getRequiresReindex());
        assertEquals(768, dto.getDimensions());
        assertTrue(dto.getKeyConfigured());
    }

    @Test
    void save_firstSaveMissingFields_throwsEmbeddingInvalid() {
        when(embeddingMapper.selectSingleton()).thenReturn(null);

        AiEmbeddingSaveReqDTO req = new AiEmbeddingSaveReqDTO();
        req.setDimensions(1536);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.save(req, loginUser, request));

        assertEquals(ErrorCodeConstants.AI_EMBEDDING_INVALID.code(), ex.getCode());
    }

    @Test
    void save_dimensionChange_retiresVersionAndRequiresReindex() {
        AiEmbeddingConfig before = configuredRow(1536, "cosine");
        AiEmbeddingConfig after = configuredRow(768, "cosine");
        Map<String, Object> retired = new LinkedHashMap<>();
        retired.put("version", 1);
        retired.put("dimensions", 1536);
        after.setVersions(new ArrayList<>(List.of(retired)));
        // 首次读到变更前配置，保存后回读 GET 拿到更新后的行
        when(embeddingMapper.selectSingleton()).thenReturn(before, after);

        AiEmbeddingRespDTO dto = service.save(saveReq(768, "cosine"), loginUser, request);

        assertTrue(dto.getRequiresReindex());
        assertEquals(768, dto.getDimensions());
        assertEquals(1, dto.getVersions().size());
        assertEquals(1536, dto.getVersions().get(0).get("dimensions"));
        ArgumentCaptor<Wrapper<AiEmbeddingConfig>> captor = wrapperCaptor();
        verify(embeddingMapper, org.mockito.Mockito.times(2)).update(any(), captor.capture());
        Collection<Object> values = ((LambdaUpdateWrapperX<?>) captor.getAllValues().get(0))
                .getParamNameValuePairs().values();
        assertTrue(values.stream().anyMatch(v -> v instanceof String json && json.contains("1536")),
                "versions 须以 JSON 字符串绑定");
        assertTrue(values.stream().noneMatch(v -> v instanceof List), "JSON 列禁止原始 List 直绑 pgjdbc");
    }

    @Test
    void save_invalidBaseUrl_throwsEmbeddingInvalid() {
        AiEmbeddingSaveReqDTO req = saveReq(1536, "cosine");
        req.setBaseUrl("ftp://bad");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.save(req, loginUser, request));

        assertEquals(ErrorCodeConstants.AI_EMBEDDING_INVALID.code(), ex.getCode());
    }

    @Test
    void test_notConfigured_throwsTestFailed() {
        when(embeddingMapper.selectSingleton()).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.test(loginUser, request));

        assertEquals(ErrorCodeConstants.AI_EMBEDDING_TEST_FAILED.code(), ex.getCode());
    }

    @Test
    void test_success_persistsAndReturnsDimensions() {
        AiEmbeddingConfig row = configuredRow(1536, "cosine");
        when(embeddingMapper.selectSingleton()).thenReturn(row);
        when(embeddingClient.embed(any(), anyList(), any(), isNull(), isNull()))
                .thenReturn(new AiEmbeddingClient.EmbeddingReply(List.of(0.1, 0.2, 0.3), 3, 5));

        AiEmbeddingTestRespDTO resp = service.test(loginUser, request);

        assertTrue(resp.getSuccess());
        assertEquals(3, resp.getDimensions());
        ArgumentCaptor<Wrapper<AiEmbeddingConfig>> captor = wrapperCaptor();
        verify(embeddingMapper).update(any(), captor.capture());
        Map<String, Object> saved = JsonUtils.parseObject(jsonParam(captor.getValue(), "latencyMs"), Map.class);
        assertEquals(Boolean.TRUE, saved.get("success"));
        assertEquals(3, saved.get("dimensions"));
    }

    @Test
    void test_failure_persistsAndThrowsTestFailed() {
        AiEmbeddingConfig row = configuredRow(1536, "cosine");
        when(embeddingMapper.selectSingleton()).thenReturn(row);
        when(embeddingClient.embed(any(), anyList(), any(), isNull(), isNull()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), "timeout"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.test(loginUser, request));

        assertEquals(ErrorCodeConstants.AI_EMBEDDING_TEST_FAILED.code(), ex.getCode());
        assertTrue(ex.getMessage().contains("timeout"));
        ArgumentCaptor<Wrapper<AiEmbeddingConfig>> captor = wrapperCaptor();
        verify(embeddingMapper).update(any(), captor.capture());
        Map<String, Object> saved = JsonUtils.parseObject(jsonParam(captor.getValue(), "msg"), Map.class);
        assertEquals(Boolean.FALSE, saved.get("success"));
        assertEquals("timeout", saved.get("msg"));
    }

    private static AiEmbeddingTestReqDTO fullTestReq() {
        AiEmbeddingTestReqDTO req = new AiEmbeddingTestReqDTO();
        req.setBaseUrl("https://draft.example.com/v1");
        req.setEmbeddingModel("text-embedding-draft");
        req.setDimensions(1024);
        req.setOperator("l2");
        return req;
    }

    @Test
    void test_withFormPayload_usesDraftValuesWithoutWritingConfig() {
        AiEmbeddingConfig row = configuredRow(1536, "cosine");
        when(embeddingMapper.selectSingleton()).thenReturn(row);
        when(embeddingClient.embed(any(), anyList(), any(), isNull(), isNull()))
                .thenReturn(new AiEmbeddingClient.EmbeddingReply(List.of(0.1), 1, 5));

        AiEmbeddingTestRespDTO resp = service.test(fullTestReq(), loginUser, request);

        assertTrue(resp.getSuccess());
        ArgumentCaptor<AiEmbeddingConfig> captor = ArgumentCaptor.forClass(AiEmbeddingConfig.class);
        verify(embeddingClient).embed(captor.capture(), anyList(), any(), isNull(), isNull());
        assertEquals("https://draft.example.com/v1", captor.getValue().getBaseUrl());
        assertEquals("text-embedding-draft", captor.getValue().getEmbeddingModel());
        assertEquals(1024, captor.getValue().getDimensions());
        assertEquals("l2", captor.getValue().getOperator());
        assertEquals("https://api.example.com/v1", row.getBaseUrl());
        assertEquals(1536, row.getDimensions());
    }

    @Test
    void test_withFormPayload_blankKeyReusesSavedCipher() {
        AiEmbeddingConfig row = configuredRow(1536, "cosine");
        when(embeddingMapper.selectSingleton()).thenReturn(row);
        when(embeddingClient.embed(any(), anyList(), any(), isNull(), isNull()))
                .thenReturn(new AiEmbeddingClient.EmbeddingReply(List.of(0.1), 1, 5));
        AiEmbeddingTestReqDTO req = fullTestReq();
        req.setApiKey("   ");

        service.test(req, loginUser, request);

        ArgumentCaptor<AiEmbeddingConfig> captor = ArgumentCaptor.forClass(AiEmbeddingConfig.class);
        verify(embeddingClient).embed(captor.capture(), anyList(), any(), isNull(), isNull());
        assertEquals("cipher", captor.getValue().getApiKeyEncrypted());
    }

    @Test
    void test_withFormPayload_newKeyEncryptedForThisCallOnly() {
        AiEmbeddingConfig row = configuredRow(1536, "cosine");
        when(embeddingMapper.selectSingleton()).thenReturn(row);
        when(embeddingClient.embed(any(), anyList(), any(), isNull(), isNull()))
                .thenReturn(new AiEmbeddingClient.EmbeddingReply(List.of(0.1), 1, 5));
        AiEmbeddingTestReqDTO req = fullTestReq();
        req.setApiKey("sk-draft");

        service.test(req, loginUser, request);

        ArgumentCaptor<AiEmbeddingConfig> configCaptor = ArgumentCaptor.forClass(AiEmbeddingConfig.class);
        verify(embeddingClient).embed(configCaptor.capture(), anyList(), any(), isNull(), isNull());
        assertNotEquals("sk-draft", configCaptor.getValue().getApiKeyEncrypted());
        assertNotEquals("cipher", configCaptor.getValue().getApiKeyEncrypted());
        ArgumentCaptor<Wrapper<AiEmbeddingConfig>> updateCaptor = wrapperCaptor();
        verify(embeddingMapper).update(any(), updateCaptor.capture());
        Collection<Object> values = ((LambdaUpdateWrapperX<?>) updateCaptor.getValue())
                .getParamNameValuePairs().values();
        assertTrue(values.stream().noneMatch(v -> v instanceof String s
                && (s.contains("sk-draft") || s.contains("draft.example.com"))));
    }

    @Test
    void test_withFormPayload_missingRequiredFields_throwsTestFailed() {
        when(embeddingMapper.selectSingleton()).thenReturn(null);
        AiEmbeddingTestReqDTO req = new AiEmbeddingTestReqDTO();
        req.setDimensions(1024);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.test(req, loginUser, request));

        assertEquals(ErrorCodeConstants.AI_EMBEDDING_TEST_FAILED.code(), ex.getCode());
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void test_withFullFormPayload_noSavedRow_testsWithoutPersisting() {
        when(embeddingMapper.selectSingleton()).thenReturn(null);
        when(embeddingClient.embed(any(), anyList(), any(), isNull(), isNull()))
                .thenReturn(new AiEmbeddingClient.EmbeddingReply(List.of(0.1), 1, 5));
        AiEmbeddingTestReqDTO req = fullTestReq();
        req.setApiKey("sk-draft");

        AiEmbeddingTestRespDTO resp = service.test(req, loginUser, request);

        assertTrue(resp.getSuccess());
        verify(embeddingMapper, never()).update(any(), any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Wrapper<AiEmbeddingConfig>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private static String jsonParam(Object wrapper, String marker) {
        return ((LambdaUpdateWrapperX<?>) wrapper).getParamNameValuePairs().values().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(json -> json.contains(marker))
                .findFirst()
                .orElseThrow();
    }
}
