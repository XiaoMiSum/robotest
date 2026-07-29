package io.github.xiaomisum.robotest.repository.bug;

import io.github.xiaomisum.robotest.model.entity.Bug;
import xyz.migoo.framework.mybatis.core.BaseMapperX;

import java.util.UUID;

public interface BugMapper extends BaseMapperX<Bug> {

    default long countByProjectId(UUID projectId) {
        return selectCount(Bug::getProjectId, projectId);
    }
}
