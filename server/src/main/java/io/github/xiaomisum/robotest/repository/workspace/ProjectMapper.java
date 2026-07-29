package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

public interface ProjectMapper extends BaseMapperX<Project> {

    default long countByWorkspaceId(UUID workspaceId) {
        return selectCount(Project::getWorkspaceId, workspaceId);
    }

    default List<Project> listByWorkspaceId(UUID workspaceId) {
        return selectList(new LambdaQueryWrapperX<Project>().eq(Project::getWorkspaceId, workspaceId));
    }

    default PageResult<Project> findPage(PageParam pageParam, UUID workspaceId,
                                          String keyword, String status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<Project>()
                .eq(Project::getWorkspaceId, workspaceId)
                .likeIfPresent(Project::getName, keyword)
                .eqIfPresent(Project::getStatus, status)
                .orderByDesc(Project::getCreatedAt));
    }

    default Project findByName(UUID workspaceId, String name) {
        return selectOne(new LambdaQueryWrapperX<Project>()
                .eq(Project::getWorkspaceId, workspaceId)
                .eq(Project::getName, name));
    }

    default Project findByNameExcludingId(UUID workspaceId, String name, UUID excludeId) {
        return selectOne(new LambdaQueryWrapperX<Project>()
                .eq(Project::getWorkspaceId, workspaceId)
                .eq(Project::getName, name)
                .ne(Project::getId, excludeId));
    }
}
