package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiFunction;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface ApiFunctionMapper extends BaseMapperX<ApiFunction> {

    /** 项目上下文可见范围内的函数：本项目 + 所属空间 + 全局 */
    default List<ApiFunction> listVisible(UUID projectId, UUID workspaceId, Boolean enabled,
                                          String type, String scope, String keyword) {
        return selectList(new LambdaQueryWrapperX<ApiFunction>()
                .and(w -> w.eq(ApiFunction::getProjectId, projectId)
                        .or().eq(ApiFunction::getWorkspaceId, workspaceId)
                        .or().eq(ApiFunction::getScope, "global"))
                .eqIfPresent(ApiFunction::getEnabled, enabled)
                .eqIfPresent(ApiFunction::getType, type)
                .eqIfPresent(ApiFunction::getScope, scope)
                .likeIfPresent(ApiFunction::getName, keyword)
                .orderByDesc(ApiFunction::getUpdatedAt));
    }

    default ApiFunction findVisibleById(UUID projectId, UUID workspaceId, UUID id) {
        return selectOne(new LambdaQueryWrapperX<ApiFunction>()
                .eq(ApiFunction::getId, id)
                .and(w -> w.eq(ApiFunction::getProjectId, projectId)
                        .or().eq(ApiFunction::getWorkspaceId, workspaceId)
                        .or().eq(ApiFunction::getScope, "global")));
    }

    default boolean existsByScopeAndName(String scope, UUID workspaceId, UUID projectId,
                                         String name, UUID excludeId) {
        return selectCount(new LambdaQueryWrapperX<ApiFunction>()
                .eq(ApiFunction::getScope, scope)
                .eq("workspace".equals(scope), ApiFunction::getWorkspaceId, workspaceId)
                .eq("project".equals(scope), ApiFunction::getProjectId, projectId)
                .eq(ApiFunction::getName, name)
                .ne(excludeId != null, ApiFunction::getId, excludeId)) > 0;
    }
}
