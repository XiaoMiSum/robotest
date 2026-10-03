package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiPromptTemplate;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

public interface AiPromptTemplateMapper extends BaseMapperX<AiPromptTemplate> {

    /** 按场景取自定义行（uk_ai_prompt_template_scene）；无行时由调用方回落内置默认 */
    default AiPromptTemplate selectByScene(String scene) {
        return selectOne(new LambdaQueryWrapperX<AiPromptTemplate>()
                .eq(AiPromptTemplate::getScene, scene)
                .last("LIMIT 1"));
    }
}
