package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiScheduledTaskMapper extends BaseMapperX<ApiScheduledTask> {

    default List<ApiScheduledTask> selectEnabled() {
        return selectList(new LambdaQueryWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getEnabled, true));
    }

    default PageResult<ApiScheduledTask> selectPageByProject(PageParam pageParam, UUID projectId,
            String taskType) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getProjectId, projectId)
                .eqIfPresent(ApiScheduledTask::getTaskType, taskType)
                .orderByDesc(ApiScheduledTask::getCreatedAt));
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

    default int updateEditableFields(UUID id, String taskType, String name, String description,
            String executionScope, List<UUID> moduleIds, List<UUID> sceneIds, String openapiUrl,
            UUID environmentId, String cronExpression) {
        return update(null, new LambdaUpdateWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getId, id)
                .set(ApiScheduledTask::getTaskType, taskType)
                .set(ApiScheduledTask::getName, name)
                .set(ApiScheduledTask::getDescription, description)
                .set(ApiScheduledTask::getExecutionScope, executionScope)
                .set(ApiScheduledTask::getModuleIds, toJsonOrNull(moduleIds))
                .set(ApiScheduledTask::getSceneIds, toJsonOrNull(sceneIds))
                .set(ApiScheduledTask::getOpenapiUrl, openapiUrl)
                .set(ApiScheduledTask::getEnvironmentId, environmentId)
                .set(ApiScheduledTask::getCronExpression, cronExpression));
    }

    default int updateEnabled(UUID id, Boolean enabled) {
        return update(null, new LambdaUpdateWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getId, id)
                .set(ApiScheduledTask::getEnabled, enabled));
    }

    /**
     * JSON 列经 wrapper.set 写入时没有 typeHandler 参与，List 原样交给 pgjdbc 会抛
     * "Can't infer the SQL type"，须传序列化后的字符串；null 必须保持 null 以显式清空列，
     * 不能用 JsonUtils.toJsonString（其 null 入参返回 "{}"）。
     */
    private static String toJsonOrNull(List<UUID> ids) {
        return ids == null ? null : JsonUtils.toJsonString(ids);
    }
}
