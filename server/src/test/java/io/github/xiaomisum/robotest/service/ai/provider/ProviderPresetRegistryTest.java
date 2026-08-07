package io.github.xiaomisum.robotest.service.ai.provider;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiProviderPresetRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPresetRegistryTest {

    private ProviderPresetRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProviderPresetRegistry(new ObjectMapper());
    }

    @Test
    void loadsPresetsFromResource() {
        List<AiProviderPresetRespDTO> all = registry.getAll();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().anyMatch(p -> "openai".equals(p.getKey())));
        assertTrue(all.stream().anyMatch(p -> "custom".equals(p.getKey())));
    }

    @Test
    void supports_scopeMatching() {
        assertTrue(registry.supports("openai", ProviderPresetRegistry.SCOPE_CHAT));
        assertTrue(registry.supports("openai", ProviderPresetRegistry.SCOPE_EMBEDDING));
        // deepseek 仅提供对话组
        assertTrue(registry.supports("deepseek", ProviderPresetRegistry.SCOPE_CHAT));
        assertFalse(registry.supports("deepseek", ProviderPresetRegistry.SCOPE_EMBEDDING));
        assertFalse(registry.supports("unknown", ProviderPresetRegistry.SCOPE_CHAT));
    }

    @Test
    void validateAndExpand_dotPathExpandedToNestedObject() {
        // 智谱 thinking.type 点号路径应展开为 {"thinking":{"type":"disabled"}}
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("thinking.type", "disabled");
        Map<String, Object> result = registry.validateAndExpand("zhipu", ProviderPresetRegistry.SCOPE_CHAT, input);

        assertFalse(result.containsKey("thinking.type"));
        Object thinking = result.get("thinking");
        assertInstanceOf(Map.class, thinking);
        assertEquals("disabled", ((Map<?, ?>) thinking).get("type"));
    }

    @Test
    void validateAndExpand_enumViolationRejected() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("thinking.type", "invalid_value");
        assertThrows(ServiceException.class,
                () -> registry.validateAndExpand("zhipu", ProviderPresetRegistry.SCOPE_CHAT, input));
    }

    @Test
    void validateAndExpand_booleanTypeViolationRejected() {
        // qwen enable_thinking 声明为 boolean，传字符串应被拒绝
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("enable_thinking", "yes");
        assertThrows(ServiceException.class,
                () -> registry.validateAndExpand("qwen", ProviderPresetRegistry.SCOPE_CHAT, input));
    }

    @Test
    void validateAndExpand_whitelistKeyRejected() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("temperature", 0.9);
        // 标准参数白名单键不可通过 extraParams 覆盖
        assertThrows(ServiceException.class,
                () -> registry.validateAndExpand("custom", ProviderPresetRegistry.SCOPE_CHAT, input));
    }

    @Test
    void validateAndExpand_customProviderKeepsArbitraryKeys() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("custom_flag", true);
        Map<String, Object> result = registry.validateAndExpand("custom", ProviderPresetRegistry.SCOPE_CHAT, input);
        assertEquals(true, result.get("custom_flag"));
    }

    @Test
    void validateAndExpand_nonTemplateKeyPassThrough() {
        // 模板外自定义键保留透传能力
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("enable_thinking", false);
        input.put("extra_vendor_key", "value");
        Map<String, Object> result = registry.validateAndExpand("qwen", ProviderPresetRegistry.SCOPE_CHAT, input);
        assertEquals(false, result.get("enable_thinking"));
        assertEquals("value", result.get("extra_vendor_key"));
    }
}
