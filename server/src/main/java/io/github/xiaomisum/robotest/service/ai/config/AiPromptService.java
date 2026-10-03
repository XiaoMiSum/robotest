package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiPromptTemplate;
import io.github.xiaomisum.robotest.repository.ai.AiPromptTemplateMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.Map;

/**
 * 场景提示词解析（详设 3.5 / 4.1）：自定义行优先，无行回落处理器内置默认，两者皆无报 1000018108。
 * 仅影响后续任务，不改变已生成产物。
 */
@Component
public class AiPromptService {

    @Resource
    private AiPromptTemplateMapper promptTemplateMapper;

    /** 渲染 {{变量}} 占位（详设 3.5）：解析场景提示词后替换为本次任务变量 */
    public String render(String scene, String builtInDefault, Map<String, String> variables) {
        String template = resolve(scene, builtInDefault);
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            // 未提供的变量保留原占位，便于提示词自定义行排查缺变量（保存侧校验在配置接口做）
            String value = entry.getValue() == null ? "" : entry.getValue();
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
        }
        return rendered;
    }

    /** 场景提示词：自定义行优先，回落 handler 内置默认（详设 3.5） */
    public String resolve(String scene, String builtInDefault) {
        AiPromptTemplate row = promptTemplateMapper.selectByScene(scene);
        if (row != null && row.getContent() != null && !row.getContent().isBlank()) {
            return row.getContent();
        }
        if (builtInDefault != null && !builtInDefault.isBlank()) {
            return builtInDefault;
        }
        throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROMPT_SCENE_NOT_FOUND);
    }
}
