package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.CommonComponent;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

public interface CommonComponentMapper extends BaseMapperX<CommonComponent> {

    /** 分页查询可见范围内的组件 */
    default PageResult<CommonComponent> selectPageVisible(UUID projectId, UUID workspaceId, String type,
                                                         Boolean enabled, String scope, String keyword,
                                                         PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<CommonComponent>()
                .and(w -> w.eq(CommonComponent::getProjectId, projectId)
                        .or().eq(CommonComponent::getWorkspaceId, workspaceId)
                        .or().eq(CommonComponent::getScope, "global"))
                .eqIfPresent(CommonComponent::getType, type)
                .eqIfPresent(CommonComponent::getEnabled, enabled)
                .eqIfPresent(CommonComponent::getScope, scope)
                .likeIfPresent(CommonComponent::getName, keyword)
                .orderByDesc(CommonComponent::getUpdatedAt));
    }

    default CommonComponent findVisibleById(UUID projectId, UUID workspaceId, UUID id) {
        return selectOne(new LambdaQueryWrapperX<CommonComponent>()
                .eq(CommonComponent::getId, id)
                .and(w -> w.eq(CommonComponent::getProjectId, projectId)
                        .or().eq(CommonComponent::getWorkspaceId, workspaceId)
                        .or().eq(CommonComponent::getScope, "global")));
    }

    default boolean existsByScopeAndTypeAndName(String scope, UUID workspaceId, UUID projectId,
                                                String type, String name, UUID excludeId) {
        return selectCount(new LambdaQueryWrapperX<CommonComponent>()
                .eq(CommonComponent::getScope, scope)
                .eq("workspace".equals(scope), CommonComponent::getWorkspaceId, workspaceId)
                .eq("project".equals(scope), CommonComponent::getProjectId, projectId)
                .eq(CommonComponent::getType, type)
                .eq(CommonComponent::getName, name)
                .ne(excludeId != null, CommonComponent::getId, excludeId)) > 0;
    }
}
