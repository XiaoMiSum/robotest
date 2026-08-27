package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneStepVariable;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiSceneStepVariableMapper extends BaseMapperX<ApiSceneStepVariable> {

    default List<ApiSceneStepVariable> listByStepId(UUID stepId) {
        return selectList(new LambdaQueryWrapperX<ApiSceneStepVariable>()
                .eq(ApiSceneStepVariable::getStepId, stepId)
                .orderByAsc(ApiSceneStepVariable::getSortOrder));
    }

    default List<ApiSceneStepVariable> listByStepIds(List<UUID> stepIds) {
        return selectList(new LambdaQueryWrapperX<ApiSceneStepVariable>()
                .in(ApiSceneStepVariable::getStepId, stepIds)
                .orderByAsc(ApiSceneStepVariable::getSortOrder));
    }

    default void deleteByStepId(UUID stepId) {
        delete(new LambdaQueryWrapperX<ApiSceneStepVariable>()
                .eq(ApiSceneStepVariable::getStepId, stepId));
    }

}
