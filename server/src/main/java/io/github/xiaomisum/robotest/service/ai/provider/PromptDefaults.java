package io.github.xiaomisum.robotest.service.ai.provider;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置默认提示词模板（资源文件随代码维护，键与 AiFunctionType 枚举一致；与 v1.1.sql 种子数据同源，DB 无记录时兜底）
 */
@Component
public class PromptDefaults {

    private final Map<String, DefaultTemplate> defaults = new LinkedHashMap<>();

    public PromptDefaults(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("ai/prompt-defaults.json").getInputStream()) {
            Map<String, DefaultTemplate> loaded = objectMapper.readValue(in,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, DefaultTemplate.class));
            defaults.putAll(loaded);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ai/prompt-defaults.json", e);
        }
    }

    public DefaultTemplate get(String functionType) {
        return defaults.get(functionType);
    }

    public record DefaultTemplate(String roleInstruction, String formatConstraint) {
    }
}
