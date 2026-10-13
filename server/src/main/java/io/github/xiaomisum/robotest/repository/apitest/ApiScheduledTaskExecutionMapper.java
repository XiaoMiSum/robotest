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

    /**
     * 完成判据输入（定时任务详设 27 §2 步骤 8）：本次触发写入的平台组终态记录数。
     * 平台组产出（套件报告 / 终态任务执行记录）均发生在执行结束之后，平台组仍在执行时计数为 0。
     */
    default long countTerminalSince(UUID taskId, LocalDateTime since) {
        return selectCount(new LambdaQueryWrapperX<ApiScheduledTaskExecution>()
                .eq(ApiScheduledTaskExecution::getTaskId, taskId)
                .in(ApiScheduledTaskExecution::getStatus, "success", "failed")
                .ge(ApiScheduledTaskExecution::getCreatedAt, since));
    }

    /** 完成判据输入（定时任务详设 27 §2 步骤 8）：本次触发写入的失败任务执行记录数（平台组失败信号） */
    default long countFailedSince(UUID taskId, LocalDateTime since) {
        return selectCount(new LambdaQueryWrapperX<ApiScheduledTaskExecution>()
                .eq(ApiScheduledTaskExecution::getTaskId, taskId)
                .eq(ApiScheduledTaskExecution::getStatus, "failed")
                .ge(ApiScheduledTaskExecution::getCreatedAt, since));
    }

    /** 保留期清理：一步物理删除（定时任务详细设计 4.4），绕过逻辑删除 */
    @Delete("DELETE FROM api_scheduled_task_execution WHERE triggered_at < #{cutoff}")
    int deletePhysicallyOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
