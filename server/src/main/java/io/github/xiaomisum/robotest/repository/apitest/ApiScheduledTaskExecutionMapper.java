package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

@Mapper
public interface ApiScheduledTaskExecutionMapper extends BaseMapperX<ApiScheduledTaskExecution> {

    default PageResult<ApiScheduledTaskExecution> selectPageByTask(UUID taskId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiScheduledTaskExecution>()
                .eq(ApiScheduledTaskExecution::getTaskId, taskId)
                .orderByDesc(ApiScheduledTaskExecution::getTriggeredAt));
    }

    default Long selectCountByReport(Collection<UUID> reportIds) {
        return selectCount(new LambdaQueryWrapperX<ApiScheduledTaskExecution>()
                .in(ApiScheduledTaskExecution::getReportId, reportIds));
    }

    /** 保留期清理：一步物理删除（定时任务详细设计 4.4），绕过逻辑删除 */
    @Delete("DELETE FROM api_scheduled_task_execution WHERE triggered_at < #{cutoff}")
    int deletePhysicallyOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
