package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.audit.AuditLogWriter;
import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiSettingsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiSettingsRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiEmbeddingConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiModelConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSettingsAdminServiceTest {

    private static final UUID MODEL_ID = UUID.randomUUID();

    @Mock
    private AiConfigMapper configMapper;
    @Mock
    private AiModelConfigMapper modelMapper;
    @Mock
    private AiEmbeddingConfigMapper embeddingMapper;
    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private AiSettingsReader settingsReader;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AiSettingsAdminService service;

    private LoginUser loginUser;

    @BeforeAll
    static void initTableInfo() {
        AiConfigTableInfo.init();
    }

    @BeforeEach
    void setUp() {
        loginUser = new LoginUser();
        loginUser.setId(UUID.randomUUID());
        loginUser.setUsername("admin");
        // update() 末尾回读 GET，先给默认桩（用例内可覆盖）
        lenient().when(settingsReader.settings())
                .thenReturn(new AiSettingsReader.AiSettings(true, null, 600, 2));
        lenient().when(settingsReader.usableModel()).thenReturn(null);
        lenient().when(embeddingMapper.selectSingleton()).thenReturn(null);
    }

    private static AiModelConfig model(String name, boolean enabled) {
        AiModelConfig model = new AiModelConfig();
        model.setId(MODEL_ID);
        model.setName(name);
        model.setEnabled(enabled);
        return model;
    }

    @Test
    void get_computesReadinessBooleans() {
        when(settingsReader.settings())
                .thenReturn(new AiSettingsReader.AiSettings(true, MODEL_ID, 600, 2));
        when(settingsReader.usableModel()).thenReturn(model("gpt-x", true));
        when(modelMapper.selectById(MODEL_ID)).thenReturn(model("gpt-x", true));
        AiEmbeddingConfig embedding = new AiEmbeddingConfig();
        embedding.setEnabled(true);
        when(embeddingMapper.selectSingleton()).thenReturn(embedding);

        AiSettingsRespDTO dto = service.get();

        assertTrue(dto.getEnabled());
        assertTrue(dto.getModelReady());
        assertTrue(dto.getEmbeddingReady());
        assertTrue(dto.getAvailable());
        assertEquals("gpt-x", dto.getDefaultModelName());
        assertEquals(600, dto.getTaskTimeoutSeconds());
    }

    @Test
    void get_disabledSwitch_marksUnavailable() {
        when(settingsReader.settings())
                .thenReturn(new AiSettingsReader.AiSettings(false, MODEL_ID, 600, 2));
        when(settingsReader.usableModel()).thenReturn(model("gpt-x", true));
        when(modelMapper.selectById(MODEL_ID)).thenReturn(model("gpt-x", true));
        when(embeddingMapper.selectSingleton()).thenReturn(null);

        AiSettingsRespDTO dto = service.get();

        assertTrue(dto.getModelReady());
        assertFalse(dto.getEmbeddingReady());
        assertFalse(dto.getAvailable());
    }

    @Test
    void update_defaultModelMissing_throwsDefaultModelConflict() {
        when(modelMapper.selectById(MODEL_ID)).thenReturn(null);
        AiSettingsUpdateReqDTO req = new AiSettingsUpdateReqDTO();
        req.setDefaultModelId(MODEL_ID);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.update(req, loginUser, request));

        assertEquals(ErrorCodeConstants.AI_DEFAULT_MODEL_CONFLICT.code(), ex.getCode());
    }

    @Test
    void update_disableSwitch_failsActiveTasksAndAudits() {
        AiConfig row = new AiConfig();
        row.setEnabled(true);
        when(configMapper.selectSingleton()).thenReturn(row);
        when(taskMapper.failActive(anyString(), eq(ErrorCodeConstants.AI_DISABLED.code()))).thenReturn(3);
        when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");

        AiSettingsUpdateReqDTO req = new AiSettingsUpdateReqDTO();
        req.setEnabled(false);
        service.update(req, loginUser, request);

        verify(taskMapper).failActive(eq("AI 总开关关闭"), eq(ErrorCodeConstants.AI_DISABLED.code()));
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).write(captor.capture());
        assertEquals(3, captor.getValue().getChanges().get("failedTasks"));
    }

    @Test
    void update_singleRowMissing_insertsWithDefaults() {
        when(configMapper.selectSingleton()).thenReturn(null);
        when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");

        AiSettingsUpdateReqDTO req = new AiSettingsUpdateReqDTO();
        req.setEnabled(true);
        req.setTaskTimeoutSeconds(900);
        service.update(req, loginUser, request);

        ArgumentCaptor<AiConfig> captor = ArgumentCaptor.forClass(AiConfig.class);
        verify(configMapper).insert(captor.capture());
        assertEquals(900, captor.getValue().getTaskTimeoutSeconds());
        assertEquals(2, captor.getValue().getTaskMaxRetries());
        verify(taskMapper, never()).failActive(anyString(), any(Integer.class));
    }

    @Test
    void update_partialUpdate_onlySetsProvidedFields() {
        when(configMapper.selectSingleton()).thenReturn(new AiConfig());
        when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");

        AiSettingsUpdateReqDTO req = new AiSettingsUpdateReqDTO();
        req.setTaskTimeoutSeconds(30);
        service.update(req, loginUser, request);

        verify(configMapper).update(eq(null), any());
        verify(taskMapper, never()).failActive(anyString(), any(Integer.class));
    }
}
