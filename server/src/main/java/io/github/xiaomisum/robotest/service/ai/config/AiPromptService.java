package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiPromptTemplate;
import io.github.xiaomisum.robotest.repository.ai.AiPromptTemplateMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

/**
 * 场景提示词解析（详设 3.5 / 4.1）：自定义行优先，无行回落处理器内置默认，两者皆无报 1000018108。
 * 仅影响后续任务，不改变已生成产物。
 */
@Component
public class AiPromptService {

    @Resource
    private AiPromptTemplateMapper promptTemplateMapper;

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
