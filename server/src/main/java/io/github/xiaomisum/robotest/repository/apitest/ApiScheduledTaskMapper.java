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

    /** 项目内测试计划类任务；删除保护（设计 4.2）按实时任务配置判定场景/模块引用 */
    default List<ApiScheduledTask> listTestPlanByProject(UUID projectId) {
        return selectList(new LambdaQueryWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getProjectId, projectId)
                .eq(ApiScheduledTask::getTaskType, "scene_execute"));
    }

    /** 环境被测试计划任务绑定（目标环境）时禁止删除 */
    default Long selectCountEnvBound(UUID environmentId) {
        return selectCount(new LambdaQueryWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getTaskType, "scene_execute")
                .eq(ApiScheduledTask::getEnvironmentId, environmentId));
    }
}
