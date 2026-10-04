package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.audit.AuditLogWriter;
import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiModelCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiModelUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiModelListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiModelRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiModelTestRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiModelConfigMapper;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelAdminServiceTest {

    private static final UUID MODEL_ID = UUID.randomUUID();
    private static final byte[] SECRET_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Mock
    private AiModelConfigMapper modelMapper;
    @Mock
    private AiConfigMapper configMapper;
    @Mock
    private AiModelClient modelClient;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private HttpServletRequest request;

    private AiModelAdminService service;
    private LoginUser loginUser;

    @BeforeAll
    static void initTableInfo() {
        AiConfigTableInfo.init();
    }

    @BeforeEach
    void setUp() {
        service = new AiModelAdminService(SECRET_KEY);
        ReflectionTestUtils.setField(service, "modelMapper", modelMapper);
        ReflectionTestUtils.setField(service, "configMapper", configMapper);
        ReflectionTestUtils.setField(service, "modelClient", modelClient);
        ReflectionTestUtils.setField(service, "auditLogWriter", auditLogWriter);
        ReflectionTestUtils.setField(service, "clientIpResolver", clientIpResolver);
        lenient().when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");

        loginUser = new LoginUser();
        loginUser.setId(UUID.randomUUID());
        loginUser.setUsername("admin");
    }

    private static AiModelConfig storedModel(UUID id) {
        AiModelConfig row = new AiModelConfig();
        row.setId(id);
        row.setName("gpt-x");
        row.setProvider("openai");
        row.setBaseUrl("https://api.example.com/v1");
        row.setApiKeyEncrypted("cipher");
        row.setModelName("gpt-x-mini");
        row.setCapabilities(List.of("chat"));
        row.setPriority(100);
        row.setEnabled(true);
        return row;
    }

    private static AiModelCreateReqDTO createReq(String name, String baseUrl, List<String> capabilities) {
        AiModelCreateReqDTO req = new AiModelCreateReqDTO();
        req.setName(name);
        req.setProvider("openai");
        req.setBaseUrl(baseUrl);
        req.setApiKey("sk-plain");
        req.setModelName("gpt-x-mini");
        req.setCapabilities(capabilities);
        return req;
    }

    @Test
    void list_keyNeverEchoed_onlyKeyConfiguredFlag() {
        when(modelMapper.selectList(any())).thenReturn(List.of(storedModel(MODEL_ID)));

        AiModelListRespDTO dto = service.list();

        assertEquals(1, dto.getTotal());
        assertTrue(dto.getList().get(0).getKeyConfigured());
        assertNull(dto.getList().get(0).getLastTest());
    }

    @Test
    void create_duplicateName_throwsModelInvalid() {
        when(modelMapper.selectOne(any())).thenReturn(storedModel(UUID.randomUUID()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(createReq("gpt-x", "https://api.example.com/v1", List.of("chat")),
                        loginUser, request));

        assertEquals(ErrorCodeConstants.AI_MODEL_INVALID.code(), ex.getCode());
    }

    @Test
    void create_invalidBaseUrl_throwsModelInvalid() {
        when(modelMapper.selectOne(any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(createReq("gpt-x", "ftp://bad", List.of("chat")), loginUser, request));

        assertEquals(ErrorCodeConstants.AI_MODEL_INVALID.code(), ex.getCode());
    }

    @Test
    void create_unsupportedCapability_throwsModelInvalid() {
        when(modelMapper.selectOne(any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(createReq("gpt-x", "https://api.example.com/v1", List.of("audio")),
                        loginUser, request));

        assertEquals(ErrorCodeConstants.AI_MODEL_INVALID.code(), ex.getCode());
    }

    @Test
    void create_success_encryptsApiKey() {
        when(modelMapper.selectOne(any())).thenReturn(null);

        service.create(createReq("gpt-x", "https://api.example.com/v1", List.of("chat")), loginUser, request);

        ArgumentCaptor<AiModelConfig> captor = ArgumentCaptor.forClass(AiModelConfig.class);
        verify(modelMapper).insert(captor.capture());
        assertNotEquals("sk-plain", captor.getValue().getApiKeyEncrypted());
        String plain = SecretCryptoUtil.decrypt(SECRET_KEY, captor.getValue().getApiKeyEncrypted());
        assertEquals("sk-plain", plain);
    }

    @Test
    void update_notFound_throwsModelNotFound() {
        when(modelMapper.selectById(MODEL_ID)).thenReturn(null);

        AiModelUpdateReqDTO req = new AiModelUpdateReqDTO();
        req.setEnabled(true);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.update(MODEL_ID, req, loginUser, request));

        assertEquals(ErrorCodeConstants.AI_MODEL_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void update_disableDefaultModel_throwsDefaultModelConflict() {
        AiModelConfig existing = storedModel(MODEL_ID);
        when(modelMapper.selectById(MODEL_ID)).thenReturn(existing);
        AiConfig config = new AiConfig();
        config.setDefaultModelId(MODEL_ID);
        when(configMapper.selectSingleton()).thenReturn(config);

        AiModelUpdateReqDTO req = new AiModelUpdateReqDTO();
        req.setEnabled(false);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.update(MODEL_ID, req, loginUser, request));

        assertEquals(ErrorCodeConstants.AI_DEFAULT_MODEL_CONFLICT.code(), ex.getCode());
    }

    @Test
    void delete_defaultModel_throwsDefaultModelConflict() {
        when(modelMapper.selectById(MODEL_ID)).thenReturn(storedModel(MODEL_ID));
        AiConfig config = new AiConfig();
        config.setDefaultModelId(MODEL_ID);
        when(configMapper.selectSingleton()).thenReturn(config);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.delete(MODEL_ID, loginUser, request));

        assertEquals(ErrorCodeConstants.AI_DEFAULT_MODEL_CONFLICT.code(), ex.getCode());
    }

    @Test
    void delete_success_logsAudit() {
        AiModelConfig existing = storedModel(MODEL_ID);
        when(modelMapper.selectById(MODEL_ID)).thenReturn(existing);
        AiConfig config = new AiConfig();
        config.setDefaultModelId(UUID.randomUUID());
        when(configMapper.selectSingleton()).thenReturn(config);

        service.delete(MODEL_ID, loginUser, request);

        verify(modelMapper).deleteById(MODEL_ID);
        verify(auditLogWriter).write(any());
    }

    @Test
    void test_success_persistsLastTest() {
        when(modelMapper.selectById(MODEL_ID)).thenReturn(storedModel(MODEL_ID));
        when(modelClient.chat(any())).thenReturn(new AiChatReply("ok", 10, 2));

        AiModelTestRespDTO resp = service.test(MODEL_ID, loginUser, request);

        assertTrue(resp.getSuccess());
        verify(modelMapper).update(isNull(), any());
    }

    @Test
    void test_failure_persistsFailureAndThrowsTestFailed() {
        when(modelMapper.selectById(MODEL_ID)).thenReturn(storedModel(MODEL_ID));
        when(modelClient.chat(any())).thenThrow(
                ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), "401 unauthorized"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.test(MODEL_ID, loginUser, request));

        assertEquals(ErrorCodeConstants.AI_MODEL_TEST_FAILED.code(), ex.getCode());
        assertTrue(ex.getMessage().contains("401 unauthorized"));
        verify(modelMapper).update(isNull(), any());
    }

    @Test
    void test_notFound_throwsModelNotFound() {
        when(modelMapper.selectById(MODEL_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.test(MODEL_ID, loginUser, request));

        assertEquals(ErrorCodeConstants.AI_MODEL_NOT_FOUND.code(), ex.getCode());
    }
}
