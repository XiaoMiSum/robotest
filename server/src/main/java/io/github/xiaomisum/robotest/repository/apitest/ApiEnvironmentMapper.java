package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.List;
import java.util.UUID;

public interface ApiEnvironmentMapper extends BaseMapperX<ApiEnvironment> {

    /** 列表排序规则：默认环境置顶，其余按 sort_order 升序 */
    default List<ApiEnvironment> listByProject(UUID projectId, String keyword) {
        return selectList(new LambdaQueryWrapperX<ApiEnvironment>()
                .eq(ApiEnvironment::getProjectId, projectId)
                .likeIfPresent(ApiEnvironment::getName, keyword)
                .orderByDesc(ApiEnvironment::getIsDefault)
                .orderByAsc(ApiEnvironment::getSortOrder));
    }

    default ApiEnvironment findByProjectIdAndName(UUID projectId, String name) {
        return selectOne(new LambdaQueryWrapperX<ApiEnvironment>()
                .eq(ApiEnvironment::getProjectId, projectId)
                .eq(ApiEnvironment::getName, name));
    }

    /** 项目默认环境：按环境排序取首条，兼容历史数据中短暂存在多条默认标记的情况。 */
    default ApiEnvironment findDefaultByProjectId(UUID projectId) {
        return selectOne(new LambdaQueryWrapperX<ApiEnvironment>()
                .eq(ApiEnvironment::getProjectId, projectId)
                .eq(ApiEnvironment::getIsDefault, true)
                .orderByAsc(ApiEnvironment::getSortOrder)
                .orderByAsc(ApiEnvironment::getCreatedAt)
                .last("LIMIT 1"));
    }

    default boolean existsByProjectIdAndName(UUID projectId, String name, UUID excludeId) {
        return selectCount(new LambdaQueryWrapperX<ApiEnvironment>()
                .eq(ApiEnvironment::getProjectId, projectId)
                .eq(ApiEnvironment::getName, name)
                .ne(excludeId != null, ApiEnvironment::getId, excludeId)) > 0;
    }

    default void clearDefaultByProjectId(UUID projectId) {
        update(null, new LambdaUpdateWrapperX<ApiEnvironment>()
                .eq(ApiEnvironment::getProjectId, projectId)
                .eq(ApiEnvironment::getIsDefault, true)
                .set(ApiEnvironment::getIsDefault, false));
    }
}
