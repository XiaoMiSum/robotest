package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiScheduledTaskMapper extends BaseMapperX<ApiScheduledTask> {

    default List<ApiScheduledTask> selectEnabled() {
        return selectList(new LambdaQueryWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getEnabled, true));
    }

    default Long selectCountBound(String taskType, UUID boundObjectId) {
        return selectCount(new LambdaQueryWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getTaskType, taskType)
                .eq(ApiScheduledTask::getBoundObjectId, boundObjectId));
    }

    default Long selectCountEnvBound(UUID environmentId) {
        return selectCount(new LambdaQueryWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getTaskType, "scene_execute")
                .eq(ApiScheduledTask::getEnvironmentId, environmentId));
    }
}
