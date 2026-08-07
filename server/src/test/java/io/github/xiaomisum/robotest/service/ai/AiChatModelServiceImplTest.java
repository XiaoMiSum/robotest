package io.github.xiaomisum.robotest.service.ai;


import io.github.xiaomisum.robotest.model.dto.request.ai.AiChatModelSaveReqDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiChatModel;
import io.github.xiaomisum.robotest.repository.ai.AiChatModelMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiChatModelServiceImpl;
import io.github.xiaomisum.robotest.service.ai.provider.ProviderPresetRegistry;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;
import io.github.xiaomisum.robotest.service.ai.support.AiCryptoUtil;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatModelServiceImplTest {

    private static final String KEY_BASE64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final UUID OPERATOR = UUID.randomUUID();

    @Mock
    private AiChatModelMapper aiChatModelMapper;
    @Mock
    private ProviderPresetRegistry presetRegistry;

    @InjectMocks
    private AiChatModelServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "secretKeyBase64", KEY_BASE64);
        lenient().when(presetRegistry.supports(anyString(), eq(ProviderPresetRegistry.SCOPE_CHAT))).thenReturn(true);
        lenient().when(presetRegistry.validateAndExpand(any(), any(), any())).thenReturn(Map.of());
    }

    private AiChatModelSaveReqDTO req(String name, String apiKey) {
        AiChatModelSaveReqDTO dto = new AiChatModelSaveReqDTO();
        dto.setName(name);
        dto.setProvider("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setModel("gpt-4o");
        dto.setApiKey(apiKey);
        return dto;
    }

    private AiChatModel model(UUID id, boolean enabled, boolean isDefault) {
        AiChatModel m = new AiChatModel();
        m.setId(id);
        m.setName("M-" + id);
        m.setProvider("openai");
        m.setBaseUrl("https://api.openai.com/v1");
        m.setModel("gpt-4o");
        m.setApiKeyCipher(AiCryptoUtil.encrypt(AiCryptoUtil.parseKey(KEY_BASE64), "sk-x"));
        m.setEnabled(enabled);
        m.setIsDefault(isDefault);
        return m;
    }

    @Test
    void create_firstModelBecomesDefault() {
        when(aiChatModelMapper.findByNameActive("GPT-4o")).thenReturn(null);
        when(aiChatModelMapper.findAllActive()).thenReturn(List.of());
        when(aiChatModelMapper.findByIdActive(any())).thenReturn(model(UUID.randomUUID(), true, true));

        service.create(req("GPT-4o", "sk-x"), OPERATOR);

        ArgumentCaptor<AiChatModel> captor = ArgumentCaptor.forClass(AiChatModel.class);
        verify(aiChatModelMapper).insert(captor.capture());
        assertTrue(captor.getValue().getIsDefault());
        assertTrue(captor.getValue().getEnabled());
    }

    @Test
    void create_secondModelNotDefault() {
        when(aiChatModelMapper.findByNameActive("GPT-4o")).thenReturn(null);
        when(aiChatModelMapper.findAllActive()).thenReturn(List.of(model(UUID.randomUUID(), true, true)));
        when(aiChatModelMapper.findByIdActive(any())).thenReturn(model(UUID.randomUUID(), true, false));

        service.create(req("GPT-4o", "sk-x"), OPERATOR);

        ArgumentCaptor<AiChatModel> captor = ArgumentCaptor.forClass(AiChatModel.class);
        verify(aiChatModelMapper).insert(captor.capture());
        assertFalse(captor.getValue().getIsDefault());
    }

    @Test
    void create_blankApiKeyRejected() {
        when(aiChatModelMapper.findByNameActive("GPT-4o")).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.create(req("GPT-4o", ""), OPERATOR));
    }

    @Test
    void create_duplicateNameRejected() {
        when(aiChatModelMapper.findByNameActive("GPT-4o")).thenReturn(model(UUID.randomUUID(), true, false));
        assertThrows(ServiceException.class, () -> service.create(req("GPT-4o", "sk-x"), OPERATOR));
    }

    @Test
    void delete_defaultRejected() {
        UUID id = UUID.randomUUID();
        when(aiChatModelMapper.findByIdActive(id)).thenReturn(model(id, true, true));
        assertThrows(ServiceException.class, () -> service.delete(id, OPERATOR));
    }

    @Test
    void setEnabled_disableDefaultRejected() {
        UUID id = UUID.randomUUID();
        when(aiChatModelMapper.findByIdActive(id)).thenReturn(model(id, true, true));
        assertThrows(ServiceException.class, () -> service.setEnabled(id, false, OPERATOR));
    }

    @Test
    void setDefault_onDisabledRejected() {
        UUID id = UUID.randomUUID();
        when(aiChatModelMapper.findByIdActive(id)).thenReturn(model(id, false, false));
        assertThrows(ServiceException.class, () -> service.setDefault(id, OPERATOR));
    }

    @Test
    void setDefault_transfersDefault() {
        UUID id = UUID.randomUUID();
        when(aiChatModelMapper.findByIdActive(id)).thenReturn(model(id, true, false));
        when(aiChatModelMapper.setDefaultById(id, OPERATOR)).thenReturn(1);

        service.setDefault(id, OPERATOR);

        // 两步：清除原默认 + 置新默认
        verify(aiChatModelMapper).clearDefault();
        verify(aiChatModelMapper).setDefaultById(id, OPERATOR);
    }

    @Test
    void resolve_byModelIdHit() {
        UUID target = UUID.randomUUID();
        when(aiChatModelMapper.findEnabledActive())
                .thenReturn(List.of(model(target, true, false), model(UUID.randomUUID(), true, true)));

        ResolvedChatModel resolved = service.resolve(target);
        assertNotNull(resolved);
        assertEquals(target, resolved.id());
        assertEquals("sk-x", resolved.apiKey());
    }

    @Test
    void resolve_fallbackToDefaultWhenModelMissing() {
        UUID defaultId = UUID.randomUUID();
        when(aiChatModelMapper.findEnabledActive())
                .thenReturn(List.of(model(UUID.randomUUID(), true, false), model(defaultId, true, true)));

        // 传入不存在的 modelId → 静默回退默认
        ResolvedChatModel resolved = service.resolve(UUID.randomUUID());
        assertNotNull(resolved);
        assertEquals(defaultId, resolved.id());
    }

    @Test
    void resolve_nullWhenNoDefault() {
        when(aiChatModelMapper.findEnabledActive()).thenReturn(List.of(model(UUID.randomUUID(), true, false)));
        assertNull(service.resolve(null));
    }

    @Test
    void resolve_nullWhenSecretMissing() {
        ReflectionTestUtils.setField(service, "secretKeyBase64", "");
        assertNull(service.resolve(UUID.randomUUID()));
    }

    @Test
    void hasEnabledModel_reflectsEnabledList() {
        when(aiChatModelMapper.findEnabledActive()).thenReturn(List.of(model(UUID.randomUUID(), true, true)));
        assertTrue(service.hasEnabledModel());
    }
}
