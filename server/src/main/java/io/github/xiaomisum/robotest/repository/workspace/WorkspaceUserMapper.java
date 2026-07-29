package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface WorkspaceUserMapper extends BaseMapperX<WorkspaceUser> {

    default long countByWorkspaceId(UUID workspaceId) {
        return selectCount(WorkspaceUser::getWorkspaceId, workspaceId);
    }

    default long countByWorkspaceIdAndRole(UUID workspaceId, UUID roleId) {
        return selectCount(new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                .eq(WorkspaceUser::getWorkspaceRole, roleId));
    }

    default long countByUserId(UUID userId) {
        return selectCount(new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getUserId, userId));
    }

    default boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId) {
        return selectCount(new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                .eq(WorkspaceUser::getUserId, userId)) > 0;
    }

    default WorkspaceUser findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId) {
        return selectOne(new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                .eq(WorkspaceUser::getUserId, userId));
    }

    default List<WorkspaceUser> listByUserId(UUID userId) {
        return selectList(new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getUserId, userId));
    }

    default List<WorkspaceUser> listByWorkspaceId(UUID workspaceId) {
        return selectList(new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, workspaceId));
    }

    default PageResult<WorkspaceUser> findPageByUserId(PageParam pageParam, UUID userId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getUserId, userId));
    }

    default PageResult<WorkspaceUser> findPageByWorkspaceId(PageParam pageParam, UUID workspaceId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, workspaceId));
    }

    default void deleteByWorkspaceId(UUID workspaceId) {
        delete(new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, workspaceId));
    }

    default void updateDefaultProjectId(UUID wuId, UUID projectId) {
        update(null, new LambdaUpdateWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getId, wuId)
                .set(WorkspaceUser::getDefaultProjectId, projectId));
    }

    default void clearDefaultProjectId(UUID workspaceId, UUID projectId) {
        update(null, new LambdaUpdateWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                .eq(WorkspaceUser::getDefaultProjectId, projectId)
                .set(WorkspaceUser::getDefaultProjectId, null));
    }

    default boolean existsByUserIdAndWorkspaceIdAndRole(UUID userId, UUID workspaceId, UUID roleId) {
        return selectCount(new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getUserId, userId)
                .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                .eq(WorkspaceUser::getWorkspaceRole, roleId)) > 0;
    }

    default int deleteByUserIdAndWorkspaceIdAndRole(UUID userId, UUID workspaceId, UUID roleId) {
        return delete(new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getUserId, userId)
                .eq(WorkspaceUser::getWorkspaceId, workspaceId)
                .eq(WorkspaceUser::getWorkspaceRole, roleId));
    }

    default PageResult<WorkspaceUser> findPageByWorkspaceIdAndUserIds(PageParam pageParam, UUID workspaceId,
                                                                       List<UUID> userIds) {
        LambdaQueryWrapperX<WorkspaceUser> wrapper = new LambdaQueryWrapperX<WorkspaceUser>()
                .eq(WorkspaceUser::getWorkspaceId, workspaceId);
        if (userIds != null && !userIds.isEmpty()) {
            wrapper.in(WorkspaceUser::getUserId, userIds);
        }
        return selectPage(pageParam, wrapper);
    }
}
