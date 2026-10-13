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

    /** 场景列表分页：模块/名称/发布状态/来源/关注过滤，更新时间倒序 */
    default PageResult<ApiScene> selectPage(UUID projectId, UUID moduleId, String search,
            List<UUID> followedOnlySceneIds, String status, String sourceType, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiScene>()
                .eq(ApiScene::getProjectId, projectId)
                .eq(moduleId != null, ApiScene::getModuleId, moduleId)
                .likeIfPresent(ApiScene::getName, search)
                .in(followedOnlySceneIds != null, ApiScene::getId, followedOnlySceneIds)
                .eq(StringUtils.hasText(status), ApiScene::getStatus, status)
                .eq(StringUtils.hasText(sourceType), ApiScene::getSourceType, sourceType)
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

    /** 仓库配置删除后，其下已导入场景保留但源状态置为 repo_removed（GitLab 集成详设 3.1） */
    default int markRepoScenesRepoRemoved(UUID repoId, String sourceStatus) {
        return update(null, new LambdaUpdateWrapperX<ApiScene>()
                .eq(ApiScene::getRepoId, repoId)
                .set(ApiScene::getSourceStatus, sourceStatus));
    }

    /** 仓库下全部在场场景：与本次扫描结果比对，识别源侧已不存在的场景（BR-1.42） */
    default List<ApiScene> listByRepo(UUID repoId) {
        return selectList(new LambdaQueryWrapperX<ApiScene>()
                .eq(ApiScene::getRepoId, repoId));
    }

    /** 按稳定键（repo_id + source_id，类全限定名#方法名）批量取既有场景，导入 upsert 的比对基准 */
    default List<ApiScene> listByRepoAndSourceIds(UUID repoId, Collection<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<ApiScene>()
                .eq(ApiScene::getRepoId, repoId)
                .in(ApiScene::getSourceId, sourceIds));
    }

    /** 源侧已不存在的场景仅标记 repo_removed：不删除、不计 summary（BR-1.42） */
    default int markScenesRepoRemoved(Collection<UUID> sceneIds, String sourceStatus) {
        if (sceneIds == null || sceneIds.isEmpty()) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapperX<ApiScene>()
                .in(ApiScene::getId, sceneIds)
                .set(ApiScene::getSourceStatus, sourceStatus));
    }

    /** 导入场景环境回填（BR-1.19）：仅 environment_id 为空的行回填，不覆盖用户已选择的环境 */
    default int backfillEnvironmentIdIfNull(UUID repoId, UUID environmentId) {
        return update(null, new LambdaUpdateWrapperX<ApiScene>()
                .eq(ApiScene::getRepoId, repoId)
                .isNull(ApiScene::getEnvironmentId)
                .set(ApiScene::getEnvironmentId, environmentId));
    }

}
