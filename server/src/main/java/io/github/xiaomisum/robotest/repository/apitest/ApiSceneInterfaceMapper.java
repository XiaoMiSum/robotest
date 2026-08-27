package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneInterface;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiSceneInterfaceMapper extends BaseMapperX<ApiSceneInterface> {

    default List<ApiSceneInterface> listBySceneId(UUID sceneId) {
        return selectList(new LambdaQueryWrapperX<ApiSceneInterface>()
                .eq(ApiSceneInterface::getSceneId, sceneId)
                .orderByAsc(ApiSceneInterface::getCreatedAt));
    }

    default ApiSceneInterface selectBySceneAndInterface(UUID sceneId, UUID interfaceId) {
        return selectOne(new LambdaQueryWrapperX<ApiSceneInterface>()
                .eq(ApiSceneInterface::getSceneId, sceneId)
                .eq(ApiSceneInterface::getInterfaceId, interfaceId));
    }

    default void deleteBySceneId(UUID sceneId) {
        delete(new LambdaQueryWrapperX<ApiSceneInterface>()
                .eq(ApiSceneInterface::getSceneId, sceneId));
    }

}
