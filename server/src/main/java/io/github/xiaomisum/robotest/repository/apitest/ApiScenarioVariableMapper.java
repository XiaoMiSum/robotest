package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScenarioVariable;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiScenarioVariableMapper extends BaseMapperX<ApiScenarioVariable> {

    default List<ApiScenarioVariable> listBySceneId(UUID sceneId) {
        return selectList(new LambdaQueryWrapperX<ApiScenarioVariable>()
                .eq(ApiScenarioVariable::getSceneId, sceneId)
                .orderByAsc(ApiScenarioVariable::getSortOrder));
    }

    default void deleteBySceneId(UUID sceneId) {
        delete(new LambdaQueryWrapperX<ApiScenarioVariable>()
                .eq(ApiScenarioVariable::getSceneId, sceneId));
    }

}
