package io.github.xiaomisum.robotest.service.ai.provider;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置默认提示词模板（资源文件随代码维护，键与 AiFunctionType 枚举一致；与 v1.1.sql 种子数据同源，
 * 仅作为「恢复默认」的数据源，运行时提示词一律从数据库读取，不参与任何组装路径）
 */
@Component
public class PromptDefaults {

    private final Map<String, DefaultTemplate> defaults = new LinkedHashMap<>();

    public PromptDefaults() {
        try (InputStream in = new ClassPathResource("ai/prompt-defaults.json").getInputStream()) {
            Map<String, DefaultTemplate> loaded = JsonUtils.parseObject(in,
                    new tools.jackson.core.type.TypeReference<Map<String, DefaultTemplate>>() {});
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
