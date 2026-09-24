package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.entity.workspace.ProjectActivity;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ProjectActivityMapper extends BaseMapperX<ProjectActivity> {

    default List<ProjectActivity> findRecentByProjectId(UUID projectId, int limit) {
        return selectList(new LambdaQueryWrapperX<ProjectActivity>()
                .eq(ProjectActivity::getProjectId, projectId)
                .orderByDesc(ProjectActivity::getOccurredAt)
                .orderByDesc(ProjectActivity::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
    }
}
