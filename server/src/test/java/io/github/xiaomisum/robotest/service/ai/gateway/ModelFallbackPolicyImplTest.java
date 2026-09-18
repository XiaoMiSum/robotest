package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.model.entity.ai.AiChatModel;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;
import io.github.xiaomisum.robotest.service.ai.support.AiCryptoUtil;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelFallbackPolicyImplTest {

    private final ModelFallbackPolicy policy = new ModelFallbackPolicyImpl();

    private static final byte[] SECRET_KEY =
            AiCryptoUtil.parseKey(Base64.getEncoder().encodeToString(new byte[32]));
    private static final String PLAIN_KEY = "sk-test-1234";

    private final UUID defaultId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID altId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private AiChatModel model(UUID id, boolean isDefault, Map<String, Object> extraParams) {
        AiChatModel m = new AiChatModel();
        m.setId(id);
        m.setName("M-" + id);
        m.setProvider("openai-compat");
        m.setBaseUrl("https://api.example.com");
        m.setModel("gpt-4o-mini");
        m.setIsDefault(isDefault);
        m.setEnabled(true);
        m.setApiKeyCipher(AiCryptoUtil.encrypt(SECRET_KEY, PLAIN_KEY));
        m.setExtraParams(extraParams);
        return m;
    }

    private List<AiChatModel> activeModels() {
        return List.of(model(altId, false, null), model(defaultId, true, null));
    }

    @Test
    void resolve_specifiedModelId_wins() {
        ResolvedChatModel resolved = policy.resolve(altId, activeModels(), SECRET_KEY);

        assertEquals(altId, resolved.id());
        assertEquals("M-" + altId, resolved.name());
        assertEquals(PLAIN_KEY, resolved.apiKey());
    }

    @Test
    void resolve_nullModelId_picksDefault() {
        ResolvedChatModel resolved = policy.resolve(null, activeModels(), SECRET_KEY);

        assertEquals(defaultId, resolved.id());
        assertEquals("gpt-4o-mini", resolved.model());
        assertEquals("https://api.example.com", resolved.baseUrl());
    }

    @Test
    void resolve_invalidModelId_fallsBackToDefault() {
        // 已删除/不存在：不在启用快照中，静默回退默认（4.11）
        UUID missing = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

        ResolvedChatModel resolved = policy.resolve(missing, activeModels(), SECRET_KEY);

        assertEquals(defaultId, resolved.id());
    }

    @Test
    void resolve_noDefaultInEnabled_returnsNull() {
        List<AiChatModel> enabled = List.of(model(altId, false, null));

        // 命中启用不受默认缺失影响（用途直接返回）；仅缺省/失效才需要默认（4.11）
        assertNull(policy.resolve(null, enabled, SECRET_KEY));
        assertEquals(altId, policy.resolve(altId, enabled, SECRET_KEY).id());
    }

    @Test
    void resolve_emptyEnabled_returnsNull() {
        assertNull(policy.resolve(null, List.of(), SECRET_KEY));
        assertNull(policy.resolve(defaultId, List.of(), SECRET_KEY));
    }

    @Test
    void resolve_decryptFailure_returnsNull() {
        AiChatModel corrupt = model(defaultId, true, null);
        corrupt.setApiKeyCipher("not-a-valid-cipher");

        assertNull(policy.resolve(defaultId, List.of(corrupt), SECRET_KEY));
    }

    @Test
    void resolve_missingSecretKey_returnsNull() {
        assertNull(policy.resolve(defaultId, List.of(model(defaultId, true, null)), null));
    }

    @Test
    void resolve_extraParamsNull_becomesEmptyMap() {
        ResolvedChatModel resolved = policy.resolve(null, activeModels(), SECRET_KEY);

        assertNotNull(resolved.extraParams());
        assertTrue(resolved.extraParams().isEmpty());
    }

    @Test
    void resolve_preservesExtraParams() {
        Map<String, Object> extra = Map.of("temperature", "0.7");
        List<AiChatModel> enabled = List.of(model(defaultId, true, extra));

        ResolvedChatModel resolved = policy.resolve(null, enabled, SECRET_KEY);

        assertEquals(extra, resolved.extraParams());
    }
}