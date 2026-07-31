package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiPromptTemplate;
import xyz.migoo.framework.mybatis.core.BaseMapperX;

public interface AiPromptTemplateMapper extends BaseMapperX<AiPromptTemplate> {

    default AiPromptTemplate findByFunctionType(String functionType) {
        return selectOne(AiPromptTemplate::getFunctionType, functionType);
    }
}
