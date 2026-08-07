package io.github.xiaomisum.robotest.service.ai.provider;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiProviderPresetRespDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 供应商预设注册表 —— 代码内置元数据，仅作为管理端配置引导与校验依据，
 * 运行期调用协议统一走 OpenAI 兼容，不因供应商而异。
 */
@Component
public class ProviderPresetRegistry {

    public static final String PROVIDER_CUSTOM = "custom";
    public static final String SCOPE_CHAT = "chat";
    public static final String SCOPE_EMBEDDING = "embedding";

    /** 请求参数白名单：extraParams 不得覆盖（保存时校验，装配时白名单在前） */
    public static final Set<String> CHAT_STANDARD_PARAMS = Set.of(
            "model", "messages", "stream", "temperature", "max_tokens", "tools", "tool_choice", "response_format");
    public static final Set<String> EMBEDDING_STANDARD_PARAMS = Set.of("model", "input", "dimensions");

    private final Map<String, AiProviderPresetRespDTO> presets = new LinkedHashMap<>();

    public ProviderPresetRegistry(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("ai/provider-presets.json").getInputStream()) {
            List<AiProviderPresetRespDTO> list = objectMapper.readValue(in,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AiProviderPresetRespDTO.class));
            list.forEach(preset -> presets.put(preset.getKey(), preset));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ai/provider-presets.json", e);
        }
    }

    public List<AiProviderPresetRespDTO> getAll() {
        return List.copyOf(presets.values());
    }

    public AiProviderPresetRespDTO get(String key) {
        return presets.get(key);
    }

    /**
     * 供应商键有效且声明了对应组
     */
    public boolean supports(String key, String scope) {
        AiProviderPresetRespDTO preset = presets.get(key);
        return preset != null && preset.getScopes() != null && preset.getScopes().contains(scope);
    }

    /**
     * 保存 AI 配置时处理 extraParams：
     * 白名单键冲突校验 → 供应商独有配置项模板校验（类型/枚举） → 点号路径展开为嵌套对象并入。
     * 模板外自定义键原样保留（透传能力），点号路径键与同名顶层对象合并时模板路径值优先。
     */
    public Map<String, Object> validateAndExpand(String providerKey, String scope, Map<String, Object> extraParams) {
        Map<String, Object> result = extraParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraParams);

        Set<String> whitelist = SCOPE_EMBEDDING.equals(scope) ? EMBEDDING_STANDARD_PARAMS : CHAT_STANDARD_PARAMS;
        for (String key : result.keySet()) {
            if (whitelist.contains(key)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
        }

        if (providerKey == null || PROVIDER_CUSTOM.equals(providerKey)) {
            return result;
        }
        AiProviderPresetRespDTO preset = presets.get(providerKey);
        if (preset == null || preset.getUniqueParams() == null) {
            return result;
        }
        List<AiProviderPresetRespDTO.UniqueParam> params = preset.getUniqueParams().get(scope);
        if (params == null) {
            return result;
        }
        for (AiProviderPresetRespDTO.UniqueParam param : params) {
            Object value = result.containsKey(param.getKey())
                    ? result.remove(param.getKey())
                    : readPath(result, param.getKey());
            if (value == null) {
                continue;
            }
            validateType(param, value);
            writePath(result, param.getKey(), value);
        }
        return result;
    }

    private void validateType(AiProviderPresetRespDTO.UniqueParam param, Object value) {
        boolean valid = switch (param.getType()) {
            case "boolean" -> value instanceof Boolean;
            case "number" -> value instanceof Number;
            case "string" -> value instanceof String;
            case "enum" -> value instanceof String str
                    && param.getOptions() != null && param.getOptions().contains(str);
            default -> false;
        };
        if (!valid) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private Object readPath(Map<String, Object> map, String dotPath) {
        String[] segments = dotPath.split("\\.");
        Object cursor = map;
        for (String segment : segments) {
            if (!(cursor instanceof Map<?, ?> m)) {
                return null;
            }
            cursor = ((Map<String, Object>) m).get(segment);
        }
        return cursor;
    }

    @SuppressWarnings("unchecked")
    private void writePath(Map<String, Object> map, String dotPath, Object value) {
        String[] segments = dotPath.split("\\.");
        Map<String, Object> cursor = map;
        for (int i = 0; i < segments.length - 1; i++) {
            Object next = cursor.get(segments[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                cursor.put(segments[i], next);
            }
            cursor = (Map<String, Object>) next;
        }
        cursor.put(segments[segments.length - 1], value);
    }
}
