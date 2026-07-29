package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.entity.Project;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface ProjectMapper extends BaseMapperX<Project> {

    default long countByWorkspaceId(UUID workspaceId) {
        return selectCount(Project::getWorkspaceId, workspaceId);
    }

    default List<Project> listByWorkspaceId(UUID workspaceId) {
        return selectList(new LambdaQueryWrapperX<Project>().eq(Project::getWorkspaceId, workspaceId));
    }
}
