package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiSceneMapper extends BaseMapperX<ApiScene> {

    /** 场景列表分页：模块/名称/发布状态/关注过滤，更新时间倒序 */
    default PageResult<ApiScene> selectPage(UUID projectId, UUID moduleId, String search,
            List<UUID> followedOnlySceneIds, String status, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiScene>()
                .eq(ApiScene::getProjectId, projectId)
                .eq(moduleId != null, ApiScene::getModuleId, moduleId)
                .likeIfPresent(ApiScene::getName, search)
                .in(followedOnlySceneIds != null, ApiScene::getId, followedOnlySceneIds)
                .eq(StringUtils.hasText(status), ApiScene::getStatus, status)
                .orderByDesc(ApiScene::getUpdatedAt));
    }

    default List<ApiScene> listByProject(UUID projectId) {
        return selectList(new LambdaQueryWrapperX<ApiScene>()
                .eq(ApiScene::getProjectId, projectId)
                .orderByAsc(ApiScene::getCreatedAt));
    }

    default List<ApiScene> listByModuleIds(UUID projectId, Collection<UUID> moduleIds) {
        return selectList(new LambdaQueryWrapperX<ApiScene>()
                .eq(ApiScene::getProjectId, projectId)
                .in(ApiScene::getModuleId, moduleIds)
                .orderByAsc(ApiScene::getCreatedAt));
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

    /** 场景设置保存：按当前版本条件更新，影响行数为 0 表示版本冲突。 */
    default int updateByIdAndChangeVersion(UUID id, Integer currentVersion, ApiScene carrier) {
        return update(carrier, new LambdaUpdateWrapperX<ApiScene>()
                .eq(ApiScene::getId, id)
                .eq(ApiScene::getChangeVersion, currentVersion));
    }

}
