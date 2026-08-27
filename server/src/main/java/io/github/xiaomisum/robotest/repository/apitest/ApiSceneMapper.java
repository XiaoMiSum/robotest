package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

@Mapper
public interface ApiSceneMapper extends BaseMapperX<ApiScene> {

    /** 场景列表分页：模块/名称过滤，更新时间倒序 */
    default PageResult<ApiScene> selectPage(UUID projectId, UUID moduleId, String search, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiScene>()
                .eq(ApiScene::getProjectId, projectId)
                .eq(moduleId != null, ApiScene::getModuleId, moduleId)
                .likeIfPresent(ApiScene::getName, search)
                .orderByDesc(ApiScene::getUpdatedAt));
    }

    default ApiScene selectByName(UUID projectId, String name) {
        return selectOne(new LambdaQueryWrapperX<ApiScene>()
                .eq(ApiScene::getProjectId, projectId)
                .eq(ApiScene::getName, name));
    }

    default void deleteByProjectAndName(UUID projectId, String name) {
        delete(new LambdaQueryWrapperX<ApiScene>()
                .eq(ApiScene::getProjectId, projectId)
                .eq(ApiScene::getName, name));
    }

}
